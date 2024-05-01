package crypto;


import java.io.*;
import java.util.*;

import com.goterl.lazysodium.interfaces.GenericHash;
import com.goterl.lazysodium.interfaces.MessageEncoder;
import com.goterl.lazysodium.interfaces.SecretBox;
import com.goterl.lazysodium.utils.Base64MessageEncoder;
import com.goterl.lazysodium.utils.HexMessageEncoder;
import middleware.MessageHandler;
import middleware.NodesListHandler;
import org.json.simple.JSONObject;

public class FileProcessor {
    public static final int MAX_CHUNK_SIZE = 1024 * 1024 * 10;
    private static final int MIN_CHUNK_COUNT = 3;
    private static MessageEncoder hexEnc = new HexMessageEncoder();
    private static MessageEncoder base64Enc = new Base64MessageEncoder();
    /**
     * The method upload a given file to the distributed system. It divides the file in chunks and encrypt them
     *
     * @param path Path to the file
     * @param pass Passphrase for deriving masterKey
     * @return [True] if the process has been completed successfully. [False] otherwise
     */
    public static boolean uploadFile(String path, String pass) {

        NodesListHandler.initHandler();
        MessageHandler messageHandler = new MessageHandler();

        File file = new File(path);

        long fileSize = file.length();

        /*
        A file is split into at least MIN_CHUNK_COUNT chunks
        until chunk size reaches DEFAULT_CHUNK_SIZE, then
        count is increased. If MAX_CHUNK_COUNT is reached,
        chunk size is increased.
         */
        int chunkCount = MIN_CHUNK_COUNT;
        int chunkSize = (int) Math.ceil(fileSize / (double) chunkCount);
        if (chunkSize > MAX_CHUNK_SIZE) {
            chunkCount = (int) Math.ceil(fileSize / (double) MAX_CHUNK_SIZE);
            chunkSize = (int) Math.ceil(fileSize / (double) chunkCount);
        }
        System.out.println("FileSize: " + fileSize);
        System.out.println("ChunkSize: " + chunkSize);
        System.out.println("ChunkCount: " + chunkCount);

        // Use the KD constructor that randomly generates seed and context
        KeyDerivation kd = new KeyDerivation(pass);
        kd.deriveNSubKeys(chunkCount);
        List<byte[]> subKeys = kd.getSubKeys();
        List<byte[]> subHashKeys = kd.getSubHashKeys();
        System.out.println(subKeys.size());
        byte[] context = kd.getContext();
        byte[] salt = kd.getSalt();
        byte[] nonce;
        byte[] lastChunkHash = new byte[GenericHash.BYTES];
        Arrays.fill(lastChunkHash, (byte) 9);


        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            int bytesRead;
            byte[] buffer = new byte[chunkSize];
            int i = chunkCount - 1;
            long currentPosition = file.length() - chunkSize; // read from the bottom
            long remainingBytes = file.length();

            while (i >= 0) {
                System.out.println("Processing chunk " + i);
                raf.seek(currentPosition);

                bytesRead = raf.read(buffer, 0, (int) Math.min(remainingBytes, chunkSize));
                if (bytesRead == -1) {
                    break;
                }

                // 1. Split the file into chunks
                byte[] chunk = new byte[bytesRead];
                System.arraycopy(buffer, 0, chunk, 0, bytesRead);


                // 2. Hash the chunk
                byte[] hash = new byte[GenericHash.BYTES];
                byte[] hashKey = subHashKeys.get(i); // get key in reverse order
                Sodium.get().cryptoGenericHash(hash, GenericHash.BYTES, chunk, chunk.length, hashKey, GenericHash.KEYBYTES);

                // 3. Encrypt the chunk
                byte[] key = subKeys.get(i); // get key in reverse order

                nonce = Sodium.get().nonce(SecretBox.NONCEBYTES);

                // The "full chunk" is composed of the actual chunk + the hash of the previous chunk for DHT
                byte[] fullChunk = new byte[chunk.length + lastChunkHash.length];
                System.arraycopy(chunk, 0, fullChunk, 0, chunk.length);
                System.arraycopy(lastChunkHash, 0, fullChunk, chunk.length, lastChunkHash.length);
                byte[] ciphertext = new byte[fullChunk.length + SecretBox.MACBYTES];
                Sodium.get().cryptoSecretBoxEasy(ciphertext, fullChunk, fullChunk.length, nonce, key);
                // 4. Encode data
                JSONObject json = getJSON(ciphertext, context, salt, nonce, chunkCount);
                String hashString = hexEnc.encode(hash);

                // 4. Send the chunk
                boolean res = messageHandler.sendClientPutMessage(hashString, json);
                if (res) {
                    System.out.println("message sent");
                } else {
                    System.out.println("error on sending chunk " + i);
                    return false;
                }

                // 5. Save current hash as the last one for the next chunk
                lastChunkHash = hash;

                // Update the remaining bytes to read
                remainingBytes -= bytesRead;
                // Move the position backward by the amount of bytes
                currentPosition = Math.max(currentPosition - bytesRead, 0);
                System.out.println("----------------------------------------------------------");

                i--;
            }
        } catch (IOException e) {
            System.out.println("Unable to read file: " + e);
            return false;
        }
        System.out.println("The following token have to be saved in order to download the file. If the token is lost, also the file is, so it is important to save it properly\nTOKEN: " + hexEnc.encode(lastChunkHash));
        return true;
    }


    /**
     * The method to download a file from the distributed system. It decrypts and recomposes the chunks
     *
     * @param token Hash of the first block
     * @param pass  Passphrase for deriving masterKey
     * @return [True] if the process has been completed successfully. [False] otherwise
     */
    public static boolean downloadFile(String token, String pass, String path) {
        MessageHandler messageHandler = new MessageHandler();
        final byte[] EOF = new byte[GenericHash.BYTES];
        Arrays.fill(EOF,(byte)9);

        byte[] prev_hash = hexEnc.decode(token);
        if(prev_hash.length!=GenericHash.BYTES){
            return false;
        }

        JSONObject json;
        byte[] ciphertext;
        byte[] context;
        byte[] salt;
        byte[] nonce;

        // create file to save the file
        RandomAccessFile complete_file = null;
        try {
            complete_file = new RandomAccessFile(new File(path), "rw");
            complete_file.seek(0);
        } catch (IOException e) {
            System.out.println("Unable to create file: " + e);
            return false;
        }

        byte[] decryptedData;
        byte[] chunk;
        byte[] nextHash;

        for (int i = 0; ; i++) {
            if (hexEnc.encode(prev_hash).equals(hexEnc.encode(EOF))) {
                System.out.println("End of file reached");
                break;
            }
            System.out.println("Iteration " + i);
            System.out.println("accessing file: " + hexEnc.encode(prev_hash));
            json = messageHandler.sendClientGetMessage(hexEnc.encode(prev_hash));
            if (json == null){
                System.out.println("Unable to retrieve chunk from system");
                return false;
            }
            ciphertext = base64Enc.decode(String.valueOf(json.get("ciphertext")));
            context = hexEnc.decode(String.valueOf(json.get("context")));
            salt = hexEnc.decode(String.valueOf(json.get("salt")));
            nonce = hexEnc.decode(String.valueOf(json.get("nonce")));

            decryptedData = new byte[ciphertext.length - SecretBox.MACBYTES];
            chunk = new byte[decryptedData.length - GenericHash.BYTES];
            nextHash = new byte[GenericHash.BYTES];

            KeyDerivation kd = new KeyDerivation(pass, context, salt);
            byte[][] keyPair = kd.deriveKeyNumber(i);
            byte[] key = keyPair[0];

            boolean res = Sodium.get().cryptoSecretBoxOpenEasy(decryptedData, ciphertext, ciphertext.length, nonce, key);
            if (!res) {
                System.out.println("ERROR IN DECRYPTION " + i);
                return false;
            }

            System.arraycopy(decryptedData, 0, chunk, 0, chunk.length);
            System.arraycopy(decryptedData, chunk.length, nextHash, 0, GenericHash.BYTES);
            System.out.println("extracted next hash:" + hexEnc.encode(nextHash));
            try {
                complete_file.write(chunk);
            } catch (IOException e) {
                System.out.println("Unable to write chunk to file: " + e);
            }

            prev_hash = nextHash;
            System.out.println("----------------------------------------------------------");
        }
        try {
            complete_file.close();
        } catch (IOException e) {
            System.out.println("Unable to save file: " + e);
            return false;
        }

        System.out.println("File " + path + " successfully reconstructed");
        return true;
    }


    /**
     * Generate the JSONObject for the socket communication
     *
     * @param encryptedChunk
     * @param context
     * @param salt
     * @param nonce
     * @param fileSize
     * @return A JSONObject
     */
    private static JSONObject getJSON(byte[] encryptedChunk, byte[] context, byte[] salt, byte[] nonce, long fileSize) {
        JSONObject message = new JSONObject();
        message.put("ciphertext", base64Enc.encode(encryptedChunk));
        message.put("context", hexEnc.encode(context));
        message.put("salt", hexEnc.encode(salt));
        message.put("nonce", hexEnc.encode(nonce));
        message.put("fileSize", fileSize);
        return message;
    }
}

