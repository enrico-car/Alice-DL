package middleware;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * [FilesManager] is a static class used by the server socket to manage the files.<br>
 * All method are synchronized to avoid concurrency between thread
 */
class FilesManager {

    /**
     * Thi method save the [data] in a file with the [id] name
     * @param rootFolder Folder of the server socket
     * @param id         DHT id of the file
     * @param data       data to store
     * @return [true] if the method successfully save the file, [false] otherwise
     */
    protected static synchronized boolean saveFile(String rootFolder, String id, String data) {
        Path filePath = Path.of(rootFolder + "/" + id);
        try {
            if (!Files.exists(filePath)) {
                Files.createFile(filePath);
            }
            Files.writeString(filePath, data);
        } catch (IOException e) {
            System.out.println("Unable to create file: " + e);
            return false;
        }
        return true;
    }

    /**
     * Thi method return the content of the file with the [id] name
     *
     * @param rootFolder Folder of the server socket
     * @param id         DHT id of the file
     * @return [JSONObject] of the read data if the file exist, [null] otherwise
     */
    protected static synchronized JSONObject getFile(String rootFolder, String id) {
        Path filePath = Path.of(rootFolder + "/" + id);
        try {
            String stringModel = Files.readString(filePath);
            return (JSONObject) new JSONParser().parse(stringModel);
        } catch (IOException | ParseException e) {
            System.out.println("Unable to read file: " + e);
            return null;
        }
    }

    /**
     * Thi method delete the file with the [id] name
     *
     * @param rootFolder Folder of the server socket
     * @param id         DHT id of the file
     * @return [JSONObject] of the read data if the file is deleted correctly, [null] otherwise
     */
    protected static synchronized JSONObject deleteFile(String rootFolder, String id) {
        Path filePath = Path.of(rootFolder + "/" + id);
        try {
            String stringModel = Files.readString(filePath);
            Files.delete(filePath);
            return (JSONObject) new JSONParser().parse(stringModel);
        } catch (IOException | ParseException e) {
            System.out.println("Unable to delete file: " + e);
            return null;
        }
    }
}
