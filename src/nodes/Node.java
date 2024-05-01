package nodes;

import com.goterl.lazysodium.LazySodiumJava;
import com.goterl.lazysodium.SodiumJava;
import com.goterl.lazysodium.exceptions.SodiumException;
import middleware.NodesConnectionHandler;
import middleware.NodesListHandler;
import org.apache.commons.codec.binary.Hex;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Node extends Thread {
    private static final String[] enabledProtocols = new String[]{"TLSv1.3"};
    private static final String[] enabledCipherSuites = new String[]{"TLS_AES_256_GCM_SHA384"};

    private final String rootFolder;

    private SSLServerSocket serverSocket;
    private final String host;
    private final int port;
    private final String id;

    public Node(String host, int port) {
        NodesListHandler.initHandler();
        SodiumJava sodium = new SodiumJava();
        LazySodiumJava lazySodium = new LazySodiumJava(sodium);

        this.host = host;
        this.port = port;
        rootFolder = Paths.get("data/DB") + "/" + host + "_" + port;
        String id = host + port;
        try {
            this.id = lazySodium.cryptoGenericHash(id);
        } catch (SodiumException e) {
            System.err.println("Error on generating hash: " + e);
            throw new RuntimeException(e);
        }
        try {
            Files.createDirectories(Paths.get(rootFolder));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * When received a socket request, start a [NodesConnectionHandler] thread to handle the connection and wait for other connection
     */
    public void run() {
        try {
            SSLServerSocketFactory sslServerSocketFactory = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
            serverSocket = (SSLServerSocket) sslServerSocketFactory.createServerSocket(port);
            serverSocket.setEnabledProtocols(enabledProtocols);
            serverSocket.setEnabledCipherSuites(enabledCipherSuites);
            NodesListHandler.addNode(id, host, port);

            System.out.println("Server " + port + ": waiting");
            //noinspection InfiniteLoopStatement
            while (true) {
                new NodesConnectionHandler((SSLSocket) serverSocket.accept(), rootFolder,id).start();
                System.out.println("Server "+port+" accept");
            }
        } catch (Exception e) {
            close();
            throw new RuntimeException(e);
        }
    }

    public void close() {
        try {
            NodesListHandler.removeNode(id);
            serverSocket.close();
            NodesListHandler.removeNode(id);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


}
