package nodes;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.util.Arrays;

import static crypto.FileProcessor.MAX_CHUNK_SIZE;

public class Client {
    private static final String[] enabledProtocols = new String[]{"TLSv1.3"};
    private static final String[] enabledCipherSuites = new String[]{"TLS_AES_256_GCM_SHA384"};
    private static final int maxBufferSize = 2048 * 2048 * 10;
    SSLSocket clientSocket = null;
    OutputStream out = null;
    InputStream in = null;

    /**
     * Start a TLS1.3 socket with the specified host
     * @param hostName
     * @param portNumber
     */
    public boolean startConnection(String hostName, int portNumber) {
        SSLSocketFactory sslSocketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        try {
            clientSocket = (SSLSocket) sslSocketFactory.createSocket(hostName, portNumber);
            clientSocket.setEnabledProtocols(enabledProtocols);
            clientSocket.setEnabledCipherSuites(enabledCipherSuites);
            clientSocket.startHandshake();
            out = new BufferedOutputStream(clientSocket.getOutputStream());
            in = new BufferedInputStream(clientSocket.getInputStream());
        } catch (IOException e) {
            System.err.println("Client socket creation failed: " + e);
            return false;
        }
        return true;
    }

    /**
     * Send a message byte to the host
     * @param msg Payload
     * @return the [answer] of the host or [null]
     */
    public byte[] sendMessage(byte[] msg) {
        try {
            out.write(msg);
            out.flush();
            out.write("EOF".getBytes());
            out.flush();
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
            return Arrays.copyOfRange(receivedMessage, 0, totalLen);
        } catch (Exception e) {
            System.err.println("Fail to send message: " + e);
            this.closeConnection();
            return null;
        }
    }

    public boolean closeConnection() {
        try {
            in.close();
            out.close();
            clientSocket.close();
            return true;
        } catch (IOException e) {
            System.err.println("Close client socket failed: " + e);
            return false;
        }
    }
}
