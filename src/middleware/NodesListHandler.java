package middleware;


import org.apache.commons.codec.binary.Hex;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Handle the map between hash digest and host
 */
public class NodesListHandler {
    private static final String jsonFile = "data/nodesList.json";
    public static final Path pathFile = Path.of(jsonFile);

    private static JSONObject model;

    /**
     * Create or load the file and convert it to a JSONObject
     */
    public static void initHandler() {
        try {
            if(!Files.exists(pathFile))
            {
                Files.createFile(pathFile);
                model = new JSONObject();
                model.put("nodes",new JSONObject());
                model.put("redundancy",0);
                saveFile();
            }
            else {
                model = (JSONObject) new JSONParser().parse(new FileReader(jsonFile));
            }
        } catch (ParseException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Add a node to the map
     * @param id Hash digest
     * @param host Hostname
     * @param port Port number
     */
    public static synchronized void addNode(String id, String host, int port) {
        JSONObject nodes = (JSONObject) model.get("nodes");
        JSONObject node = new JSONObject();
        if (nodes.get(id) != null) {
            node = (JSONObject) nodes.get(id);
            node.replace("host", host);
            node.replace("port", port);
        } else {
            node.put("host", host);
            node.put("port", port);
        }
        nodes.put(id, node);
        model.put("nodes", nodes);
        saveFile();
        setRedundancy();
    }

    /**
     * Remove node on the map
     * @param id Hash digest
     */
    public static synchronized void removeNode(String id)
    {
        JSONObject nodes = (JSONObject) model.get("nodes");
        nodes.remove(id);
        saveFile();
        setRedundancy();
    }

    /**
     * Based on the number of nodes set the redundancy level [%]
     */
    public static synchronized void setRedundancy()
    {
        JSONObject nodes = (JSONObject) model.get("nodes");
        int redundancy = (int) Math.ceil(nodes.size() * 0.15);
        if (redundancy < 2){
            redundancy = 2;
        }
        model.put("redundancy",redundancy);
        saveFile();
    }

    public static synchronized Integer getPortById(String id) {
        JSONObject nodes = (JSONObject) model.get("nodes");
        JSONObject node = (JSONObject) nodes.get(id);
        if (node == null) {
            return null;
        }
        return Integer.parseInt("" + node.get("port"));
    }

    public static synchronized String getHostById(String id) {
        JSONObject nodes = (JSONObject) model.get("nodes");
        JSONObject node = (JSONObject) nodes.get(id);
        if (node == null) {
            return null;
        }
        return "" + node.get("host");
    }

    /**
     * Based on the hash digest return a list of the nearest nodes
     * @param id Hash digest
     * @param n Number of nodes to return
     * @return
     */
    public static synchronized List<String> getNearestNodeById(String id, int n) {
        JSONObject nodes = (JSONObject) model.get("nodes");
        List<String> l = new ArrayList<String>(nodes.keySet());
        BigInteger idInt = new BigInteger(id,16);
        // Sort the list based on the differences of the hash digests
        l.sort((s, t) -> {
            BigInteger s1 = new BigInteger(s,16).subtract(idInt).abs();
            BigInteger t1 = new BigInteger(t,16).subtract(idInt).abs();
            return s1.compareTo(t1);
        });
        if(n>l.size()) {
            n=l.size();
        }
        return l.subList(0,n);

    }

    public static synchronized int getRedundancy(){
        return Integer.parseInt("" + model.get("redundancy"));
    }

    private static void saveFile() {
        try (FileWriter file = new FileWriter(jsonFile)) {
            file.write(model.toJSONString());
            file.flush();
        } catch (IOException e) {
            System.err.println("Unable to save file:" + e);
        }
    }
}