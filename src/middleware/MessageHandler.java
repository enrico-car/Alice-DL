package middleware;


import nodes.Client;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.util.List;
import java.util.Objects;

/**
 * Handle socket packages communication
 */
public class MessageHandler {

    private final Client client;

    public MessageHandler() {
        NodesListHandler.initHandler();
        this.client = new Client();
    }

    /**
     * Supported type of message
     */
    public enum MsgType {
        GET,
        PUT,
        REP,
        DEL,
        DELREP,
        OK,
        ERROR
    }

    /**
     * Send to the specific server a PUT message with the given payload
     *
     * @param id   Digest of the file to retrieve the nearest server
     * @param data Payload to send
     * @return [True] if the process has been completed successfully. [False] otherwise
     */
    public boolean sendClientPutMessage(String id, JSONObject data) {
        int redundancy = NodesListHandler.getRedundancy();
        // Retrieve the nearest servers in order
        List<String> nodes = NodesListHandler.getNearestNodeById(id, redundancy);
        boolean flag = false;
        int index = -1;
        // Ask to max [Redundancy] server, then for sure the file is not in the system
        while (!flag) {
            if (++index >= redundancy) {
                return false;
            }
            String host = NodesListHandler.getHostById(nodes.get(index));
            Integer port = NodesListHandler.getPortById(nodes.get(index));
            if (host == null || port == null) {
                break;
            }
            // Connect and send message to the server
            if (!client.startConnection(host, port)) {
                break;
            }
            byte[] message = buildMessage(MsgType.PUT, id, data);
            byte[] res = client.sendMessage(message);
            if (!client.closeConnection()) {
                break;
            }
            flag = getTypeMessage(res) == MsgType.OK;
        }
        return flag;
    }

    /**
     * Send to the specific server a GET message to retrieve a specific file
     *
     * @param id Digest of the file to retrieve the nearest server
     * @return [JSONObject] if the process has been completed successfully. [null] otherwise
     */
    public JSONObject sendClientGetMessage(String id) {
        int redundancy = NodesListHandler.getRedundancy();
        List<String> nodes = NodesListHandler.getNearestNodeById(id, redundancy);
        boolean flag = false;
        int index = -1;
        byte[] res = null;
        while (!flag) {
            if (++index >= redundancy) {
                return null;
            }
            String host = NodesListHandler.getHostById(nodes.get(index));
            Integer port = NodesListHandler.getPortById(nodes.get(index));
            if (host == null || port == null) {
                break;
            }
            // Connect and send message to the server
            if (!client.startConnection(host, port)) {
                break;
            }
            byte[] message = buildMessage(MsgType.GET, id);
            res = client.sendMessage(message);
            if (!client.closeConnection()) {
                break;
            }
            flag = (getTypeMessage(res) == MsgType.OK) && (Objects.equals(getIdMessage(res), id));
        }
        return getDataMessage(res);
    }

    /**
     * Not current implemented in the client
     */
    public JSONObject sendClientDelMessage(String id) {
        int redundancy = NodesListHandler.getRedundancy();
        List<String> nodes = NodesListHandler.getNearestNodeById(id, redundancy);
        boolean flag = false;
        int index = -1;
        byte[] res = null;
        while (!flag) {
            if (++index >= redundancy) {
                return null;
            }
            String host = NodesListHandler.getHostById(nodes.get(index));
            Integer port = NodesListHandler.getPortById(nodes.get(index));
            if (host == null || port == null) {
                break;
            }
            // Connect and send message to the server
            if (!client.startConnection(host, port)) {
                break;
            }
            byte[] message = buildMessage(MsgType.DEL, id);
            res = client.sendMessage(message);
            if (!client.closeConnection()) {
                break;
            }
            flag = (getTypeMessage(res) == MsgType.OK) && (Objects.equals(getIdMessage(res), id));
        }
        return getDataMessage(res);
    }

    /**
     * Send to the specific server a REP message to replicate the file on the network
     *
     * @param NodeId Digest of the server
     * @param dataId Digest of the file
     * @param data   Payload to send
     * @return [True] if the process has been completed successfully. [False] otherwise
     */
    public boolean sendClientReplicaMessage(String NodeId, String dataId, JSONObject data) {
        String host = NodesListHandler.getHostById(NodeId);
        Integer port = NodesListHandler.getPortById(NodeId);
        if (host != null && port != null) {
            client.startConnection(host, port);
            byte[] message = buildMessage(MsgType.REP, dataId, data);
            byte[] res = client.sendMessage(message);
            return getTypeMessage(res) == MsgType.OK;
        }
        return false;
    }

    /**
     * Not current implemented in the client
     */
    public boolean sendClientReplicaDelMessage(String NodeId, String dataId) {
        String host = NodesListHandler.getHostById(NodeId);
        Integer port = NodesListHandler.getPortById(NodeId);
        if (host != null && port != null) {
            client.startConnection(host, port);
            byte[] message = buildMessage(MsgType.DELREP, dataId);
            byte[] res = client.sendMessage(message);
            return getTypeMessage(res) == MsgType.OK;
        }
        return false;
    }

    public JSONObject getDataMessage(byte[] message) {
        try {
            JSONObject model = (JSONObject) new JSONParser().parse(new String(message));
            return (JSONObject) model.get("data");
        } catch (ParseException e) {
            System.out.println("Unable to convert message data: " + e);
            return null;
        }
    }

    public String getIdMessage(byte[] message) {
        try {
            JSONObject model = (JSONObject) new JSONParser().parse(new String(message));
            return ((String) model.get("id"));
        } catch (ParseException e) {
            System.out.println("Unable to convert message id: " + e);
            return null;
        }
    }

    public MsgType getTypeMessage(byte[] message) {
        try {
            JSONObject model = (JSONObject) new JSONParser().parse(new String(message));
            return MsgType.valueOf((String) model.get("msgType"));
        } catch (ParseException e) {
            System.out.println("Unable to convert message type: " + e);
            return null;
        }
    }

    public byte[] buildMessage(MsgType msgType) {
        JSONObject message = new JSONObject();
        message.put("msgType", msgType.toString());
        return message.toString().getBytes();
    }

    public byte[] buildMessage(MsgType msgType, String id) {
        JSONObject message = new JSONObject();
        message.put("msgType", msgType.toString());
        message.put("id", id);
        return message.toString().getBytes();
    }

    public byte[] buildMessage(MsgType msgType, String id, JSONObject data) {
        JSONObject message = new JSONObject();
        message.put("msgType", msgType.toString());
        message.put("id", id);
        message.put("data", data);
        return message.toString().getBytes();
    }
}
