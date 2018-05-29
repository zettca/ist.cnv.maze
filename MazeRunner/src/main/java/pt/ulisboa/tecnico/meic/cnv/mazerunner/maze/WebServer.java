package pt.ulisboa.tecnico.meic.cnv.mazerunner.maze;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class WebServer {

    private static final String FILENAME = "instrumentation_data.txt";
    private static final String DYNAMO_TABLE_NAME = "cnv-metrics";

    private static final short PORT = 8000;
    private static final String CP = "MazeRunner/src/main/java/";
    private static final String FOLDER = "pt/ulisboa/tecnico/meic/cnv/mazerunner/maze/";

    private static DynamoDB dynamoDB;

    public static void main(String[] args) throws Exception {
        dynamoDB = new DynamoDB(new AmazonDynamoDBClient(new ProfileCredentialsProvider()));

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/test", new MyHandler());
        server.createContext("/mzrun.html", new MazeHandler());
        server.setExecutor(Executors.newCachedThreadPool()); // creates a default executor
        server.start();
    }

    static class MyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String response = "This was the query:" + t.getRequestURI().getQuery();
            t.sendResponseHeaders(200, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    static class MazeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String query = t.getRequestURI().getQuery();
            System.out.println(query);
            Map<String, String> args = new HashMap<>();

            for (String arg : query.split("&")) {
                String[] str = arg.split("=");
                args.put(str[0], str[1]);
            }

            String id = String.format("%s_%s_%s_%s_%s_%s_%s",
                    args.get("x0"), args.get("y0"), args.get("x1"), args.get("y1"), args.get("v"), args.get("s"),
                    args.get("m"));

            String outFile = String.format("%soutputs/%s.html", FOLDER, id);
            //TODO verify keys
            args.put("m", FOLDER + "mazes/" + args.get("m"));

            String values = String.format(
                    "java pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.Main %s %s %s %s %s %s %s %s",
                    args.get("x0"), args.get("y0"), args.get("x1"), args.get("y1"), args.get("v"), args.get("s"), args.get("m"), outFile);

            System.out.println("exec.dir = " + System.getProperty("user.dir"));

            System.out.println(values);
            try {
                Process pr = Runtime.getRuntime().exec(values);
                int exitCode = pr.waitFor();
                if (exitCode < 0) {
                    System.out.println("Command exited with " + exitCode);
                }
            } catch (Exception e) {
                System.out.println(e.getMessage());
                e.printStackTrace();
            }

            System.out.println("Output: " + outFile);
            Path path = Paths.get(outFile);
            byte[] data = Files.readAllBytes(path);

            System.out.println("Sending Solution...");
            t.sendResponseHeaders(200, data.length);
            OutputStream os = t.getResponseBody();
            os.write(data);
            os.close();

            System.out.println("Loading metrics...");
            int value = -1;
            File file = new File(FILENAME);
            if (file.exists()) {
                List<String> lines = Files.readAllLines(Paths.get(FILENAME), Charset.defaultCharset());
                String line0 = lines.get(0);
                String valString = line0.split(": ")[1];
                value = Integer.parseInt(valString);
            }

            System.out.println("Writing metrics to Dynamo...");
            Table table = dynamoDB.getTable(DYNAMO_TABLE_NAME);
            table.putItem(new Item().withPrimaryKey("id", id).withInt("value", value));
        }
    }
}
