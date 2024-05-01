import middleware.MessageHandler;
import middleware.NodesListHandler;
import nodes.Node;
import org.apache.commons.cli.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static crypto.FileProcessor.downloadFile;
import static crypto.FileProcessor.uploadFile;

public class AliceDL {
    public static void main(String[] args) {
        System.setProperty("javax.net.ssl.keyStore", "data/keys/keystore");
        System.setProperty("javax.net.ssl.keyStorePassword", "passphrase");
        System.setProperty("javax.net.ssl.trustStore", "data/keys/truststore");
        System.setProperty("javax.net.ssl.trustStorePassword", "passphrase");

        Option get = Option.builder("get")
                .argName("hashDigest")
                .hasArg()
                .desc("Retrieve the file in the network based on the first hash")
                .build();

        Option put = Option.builder("put")
                .argName("filePath")
                .hasArg()
                .desc("Save the given file to the network")
                .build();

        Option server = Option.builder("server")
                .argName("number")
                .hasArg()
                .desc("Start the given number of server")
                .build();

        Option passPhrase = Option.builder("pass")
                .argName("passPhrase")
                .hasArg()
                .desc("[Required] Password for the cryptography")
                .build();

        Option destination = Option.builder("d")
                .argName("filePath")
                .hasArg()
                .desc("File path destination of the file")
                .build();

        Option port = Option.builder("p")
                .argName("number")
                .hasArg()
                .desc("Starting port number")
                .build();

        Options options = new Options();
        options.addOption(get);
        options.addOption(put);
        options.addOption(server);
        options.addOption(passPhrase);
        options.addOption(destination);
        options.addOption(port);
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();

        try {
            CommandLine cmd = parser.parse(options, args);

            if(cmd.hasOption("get") || cmd.hasOption("put")) {
                String pass = cmd.getOptionValue("pass");
                if (pass == null || pass.length() < 12 || pass.length() > 64) {
                    System.out.println("Passphrase wrongly specified - length should be between 12 and 64");
                    throw new ParseException("");
                }
            }

            if (cmd.hasOption("get") && cmd.hasOption("d")) {
                String pass = cmd.getOptionValue("pass");
                String hashDigest = cmd.getOptionValue("get");
                String dest = cmd.getOptionValue("d");
                boolean result=downloadFile(hashDigest, pass, dest);
                if(!result){
                    System.out.println("Error in the download of the file");
                }
            } else if (cmd.hasOption("put")) {
                String pass = cmd.getOptionValue("pass");
                if (pass == null) {
                    System.out.println("Passphrase not specified");
                    throw new ParseException("");
                }
                String pathFile = cmd.getOptionValue("put");

                if (!Files.exists(Path.of(pathFile))) {
                    System.out.println("Path file does not exist");
                    throw new ParseException("");
                }

                boolean result=uploadFile(pathFile, pass);
                if(!result){
                    System.out.println("Error in file uploading");
                }


            } else if (cmd.hasOption("server")) {
                Files.deleteIfExists(NodesListHandler.pathFile);
                int n = Integer.parseInt(cmd.getOptionValue("server"));
                int p = Integer.parseInt(cmd.getOptionValue("p"));
                if ((n <= 0) || (p <= 0)) {
                    System.out.println("Unable to run server");
                    throw new ParseException("");
                }
                for (int i = 0; i < n; i++) {
                    new Node("localhost", p++).start();
                }
            } else {
                throw new ParseException("");
            }
        } catch (ParseException | IOException e) {
            System.out.println("AliceDL - Wrong command format");
            formatter.printHelp("ALiceDL", options);
        }

    }
}