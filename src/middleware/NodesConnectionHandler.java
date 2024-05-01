package middleware;

import org.json.simple.JSONObject;

import javax.net.ssl.SSLSocket;
import java.io.*;
import java.util.Arrays;
import java.util.List;

import static crypto.FileProcessor.MAX_CHUNK_SIZE;

/**
 * Thread that handle a single connection with a client
 */
public class NodesConnectionHandler extends Thread {

    private final SSLSocket clientSocket;
    private OutputStream out;
    private InputStream in;

    private final MessageHandler messageHandler;
    private final String rootFolder;
    private final String id;


    public NodesConnectionHandler(SSLSocket socket, String rootFolder, String id) {
        this.clientSocket = socket;
        this.messageHandler = new MessageHandler();
        this.rootFolder = rootFolder;
        this.id = id;
    }

    /**
     * Read the packages from socket and execute the correct method based on the message type
     */
    public void requestHandler() {
        try {
            int len, totalLen = 0;
            byte[] message = new byte[(int) (MAX_CHUNK_SIZE * 1.5)];
            byte[] receivedMessage = new byte[(int) (MAX_CHUNK_SIZE * 1.5)];
            while (true) {
                len = in.read(message);
                if (len<= 0 || new String(message, 0, len).equals("EOF")) {
                    break;
                }
                System.arraycopy(message, 0, receivedMessage, totalLen, len);
                totalLen += len;
            }
            receivedMessage = Arrays.copyOfRange(receivedMessage, 0, totalLen);

            JSONObject data = messageHandler.getDataMessage(receivedMessage);
            String messageId = messageHandler.getIdMessage(receivedMessage);
            MessageHandler.MsgType msgType = messageHandler.getTypeMessage(receivedMessage);

            if (msgType == null || messageId == null) {
                sendError("Server received null");
                return;
            }
            switch (msgType){
                case GET -> messageGET(messageId);
                case DEL -> messageDEL(messageId);
                case PUT -> messagePUT(messageId,data);
                case REP -> messageREP(messageId,data);
                case DELREP -> messageDELREP(messageId);
                default -> sendError("Bad request");
            }
        } catch (IOException e) {
            System.out.println("Error on request:" + e);
        }

    }

    /**
     * Get message handler, answer to the client with the content of the request file if it exists
     * @param messageID Digest of the file
     */
    private void messageGET(String messageID) throws IOException {
        JSONObject data = FilesManager.getFile(rootFolder, messageID);
        if (data == null) {
            sendError("Server file does not exist");
            return;
        }
        byte[] response = messageHandler.buildMessage(MessageHandler.MsgType.OK, messageID, data);
        out.write(response, 0, response.length);
        out.flush();
    }

    /**
     * Not implemented in client
     */
    private void messageDELREP(String messageID) throws IOException {
        JSONObject data = FilesManager.deleteFile(rootFolder, messageID);
        if (data == null) {
            sendError("Server can't delete file");
            return;
        }
        byte[] response = messageHandler.buildMessage(MessageHandler.MsgType.OK, messageID, data);
        out.write(response, 0, response.length);
        out.flush();
    }

    /**
     * Not implemented in client
     */
    private void messageDEL(String messageID) throws IOException {
        messageDELREP(messageID);
        int redundancy = NodesListHandler.getRedundancy();
        List<String> nodes = NodesListHandler.getNearestNodeById(id, redundancy);
        for (String s : nodes) {
            if (!s.equals(this.id)) {
                System.out.println("send delete message to " + NodesListHandler.getPortById(s));
                if (!messageHandler.sendClientReplicaDelMessage(s,messageID)) {
                    System.out.println("Error on sending delete message to " + NodesListHandler.getPortById(s));
                }
            }
        }
    }

    /**
     * REP message handler, send the packages to the nearest server to maintain the replicas
     * @param messageID Digest of the file
     * @param data Payload
     */
    private void messageREP(String messageID,JSONObject data) throws IOException {
        if (data == null) {
            sendError("Server received null data");
            return;
        }
        if (FilesManager.saveFile(rootFolder, messageID, data.toJSONString())) {
            byte[] response = messageHandler.buildMessage(MessageHandler.MsgType.OK);
            out.write(response, 0, response.length);
            out.flush();
        } else {
            sendError("Unable to save file");
        }
    }

    /**
     * PUT message handler, save the file locally and send replica request
     * @param messageId Digest of the file
     * @param data Payload
     */
    private void messagePUT(String messageId,JSONObject data) throws IOException {
        messageREP(messageId, data);
        int redundancy = NodesListHandler.getRedundancy();
        List<String> nodes = NodesListHandler.getNearestNodeById(messageId, redundancy);
        for (String s : nodes) {
            if (!s.equals(this.id)) {
                System.out.println("send replica to " + NodesListHandler.getPortById(s));
                if (!messageHandler.sendClientReplicaMessage(s, messageId, data)) {
                    System.out.println("Error on sending replicas to " + NodesListHandler.getPortById(s)
                    );
                }
            }
        }
    }

    /**
     * Send to client an error message type
     * @param err Log to print in the console
     */
    private void sendError(String err) throws IOException {
        byte[] response = messageHandler.buildMessage(MessageHandler.MsgType.ERROR);
        out.write(response, 0, response.length);
        out.flush();
        System.out.println(err);
    }


    /**
     * Entry point of the thread
     */
    public void run() {
        try {
            out = new BufferedOutputStream(clientSocket.getOutputStream());
            in = new BufferedInputStream(clientSocket.getInputStream());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.requestHandler();
        this.close();
    }

    public void close() {
        try {
            in.close();
            out.close();
            clientSocket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}