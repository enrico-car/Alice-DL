package crypto;

import com.goterl.lazysodium.interfaces.GenericHash;
import com.goterl.lazysodium.interfaces.SecretBox;
import com.goterl.lazysodium.utils.HexMessageEncoder;

import java.util.ArrayList;
import java.util.List;

public class KeyDerivation {
    private List<byte[]> subKeys;
    private List<byte[]> subHashKeys;

    private byte[] salt;
    private byte[] context;
    private String passPhrase;
    HexMessageEncoder h=new HexMessageEncoder();

    /**
     * Constructor of the class. Context and salt are randomly generated
     * @param passPhrase
     */
    public KeyDerivation(String passPhrase){
        // generate master key
        subKeys = new ArrayList<>();
        subHashKeys = new ArrayList<>();
        context = new byte[8];
        salt = Sodium.get().randomBytesBuf(8);
        context = Sodium.get().randomBytesBuf(8);
        this.passPhrase = passPhrase;
    }

    /**
     * Constructor of the class. It allows to specify context and salt
     * @param passPhrase
     * @param context
     * @param salt
     */
    public KeyDerivation(String passPhrase, byte[] context, byte[] salt){
        // retrieve master key
        subKeys = new ArrayList<>();
        subHashKeys = new ArrayList<>();
        this.salt=salt;
        this.context = context;
        this.passPhrase = passPhrase;
    }

    /**
     * The function can be used to retrieve the n sub-keys from the master key. It saves the key in the field subKeys in the instance of the class.
     * @param n Number of subKeys to retrieve
     */
    public void deriveNSubKeys(int n){
        subKeys.clear();
        subHashKeys.clear();
        for (int i = 0; i < n; i++) {
            byte[][] keys = deriveKeyNumber(i);
            subKeys.add(keys[0]);
            subHashKeys.add(keys[1]);
        }
    }

    /**
     * The function can be used to retrieve the i-th sub-key from the master key
     * @param i Index of the key to derive
     * @return The i-th sub-key
     */
    public byte[][] deriveKeyNumber(int i){
        byte[] master_key = new byte[SecretBox.KEYBYTES];
        Sodium.get().cryptoGenericHash(master_key, SecretBox.KEYBYTES, passPhrase.getBytes(), passPhrase.length(), salt, salt.length);

        byte[] subKey= new byte[SecretBox.KEYBYTES];
        byte[] subHashKey= new byte[GenericHash.KEYBYTES];

        if(i<Math.pow(2,63)) { // 2^64 max amount, 2^63 because 2 keys each are generated
            Sodium.get().cryptoKdfDeriveFromKey(subKey, SecretBox.KEYBYTES, 2*i, context, master_key);
            Sodium.get().cryptoKdfDeriveFromKey(subHashKey, GenericHash.KEYBYTES, 2*i+1, context, master_key);
        }
        else throw new RuntimeException("Key Derivation limit exceeded");

        return new byte[][] {subKey, subHashKey};
    }



    public byte[] getSalt(){
        return salt;
    }

    public byte[] getContext(){
        return context;
    }

    public List<byte[]> getSubKeys() {
        return subKeys;
    }
    public List<byte[]> getSubHashKeys() {
        return subHashKeys;
    }
}
