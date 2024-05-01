package crypto;

import com.goterl.lazysodium.LazySodiumJava;
import com.goterl.lazysodium.SodiumJava;


public class Sodium {
    public static LazySodiumJava lazySodium = null;

    /**
     * Get the LazySodium Object
     */
    public static LazySodiumJava get() {
        if (lazySodium == null) {
            SodiumJava sodium = new SodiumJava();
            lazySodium = new LazySodiumJava(sodium);
        }
        return  lazySodium;
    }
}
