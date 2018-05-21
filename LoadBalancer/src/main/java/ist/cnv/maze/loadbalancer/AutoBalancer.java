package ist.cnv.maze.loadbalancer;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.*;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import javax.management.AttributeList;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AutoBalancer {

    private static final int MIN = 1;
    private static final int MAX = 4;
    private static final String SECGROUP = "cnv-ssh+http";
    private static final String AMI = "ami-a53bb7da";
    private static final String KEY = "CNV-proj";
    private static final String REGION = "us-east-1";
    private static final String ITYPE = "t2.micro";
    private static final int PORT = 8000;
    private static int robinInstance;
    private static AmazonEC2 ec2;
    private static List<Instance> instances = new ArrayList<>();
    private static AmazonDynamoDB dynamoDB;
    private static final String TABLENAME= "cnv-metrics";


    public static AWSCredentials getCredentials() {
        AWSCredentials credentials;
        try{
            credentials = new ProfileCredentialsProvider().getCredentials();
        } catch( Exception e) {
            throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file. " +
                            "Please make sure that your credentials file is at the correct " +
                            "location (~/.aws/credentials), and is in valid format.",
                    e);
        }
        return credentials;
    }



    public static void launchInstances(int min, int max){
        RunInstancesRequest request = new RunInstancesRequest();


        request.withImageId(AMI)
                .withInstanceType(ITYPE)
                .withMinCount(min)
                .withMaxCount(max)
                .withKeyName(KEY)
                .withSecurityGroups(SECGROUP);

        RunInstancesResult result =	ec2.runInstances(request);

        List<Instance> newInstances = result.getReservation().getInstances();
        List<String> newInstancesIds = new ArrayList<>();

        for (Instance i : newInstances){
            newInstancesIds.add(i.getInstanceId());
        }

        while(newInstancesIds.size() > 0) {
            DescribeInstancesResult describeInstancesRequest = ec2.describeInstances();
            List<Reservation> reservations = describeInstancesRequest.getReservations();
            List<Instance> instanceList = new ArrayList<>();
            for (Reservation reservation : reservations) {
                instanceList.addAll(reservation.getInstances());
            }

            for (Instance i : instanceList) {
                if (newInstancesIds.contains(i.getInstanceId())) {
                    if (i.getState().getCode() == 16){
                        instances.add(i);
                        newInstancesIds.remove(i.getInstanceId());
                    }
                }
            }
        }
        System.out.println("Started requested Instances");
    }

    public static void terminateInstances(List<String> ids){
        TerminateInstancesRequest request = new TerminateInstancesRequest(ids);
        ec2.terminateInstances(request);
    }

    public static Instance getNextRobin() {
        Instance i;
        i = instances.get(robinInstance);
        robinInstance = (robinInstance + 1) % instances.size();
        return i;
    }

    public static Boolean ping(URL url)  {
        HttpURLConnection connection = null;
        try {
            String urlParameters = "test";
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Content-Length", Integer.toString(urlParameters.getBytes().length));
            connection.setDoOutput(true);


            DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
            wr.writeBytes(urlParameters);
            wr.close();

            InputStream is = connection.getInputStream();
            BufferedReader rd = new BufferedReader(new InputStreamReader(is));

            StringBuilder response = new StringBuilder(); // or StringBuffer if Java version 5+
            String line;

            while ((line = rd.readLine()) != null) {
                response.append(line);
                response.append('\r');
            }

            rd.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public static void runAutoScaler() {
        class AutoScaler implements Runnable {

            @Override
            public void run() {
                try {
                    while(true) {

                        /*
                        List<String> malfunctions = new ArrayList<>();
                        for (Instance x : instances) {
                            Boolean p = ping(new URL(x.getPublicDnsName()));
                            if (!p) {
                                malfunctions.add(x.getInstanceId());
                                instances.remove(x);
                            }
                        }
                        if (malfunctions.size() > 0){
                            terminateInstances(malfunctions);
                            launchInstances(malfunctions.size(), malfunctions.size());
                        }
                        */

                        if (instances.size() == 0) {
                            launchInstances(1, 4);
                        }
                        else if (instances.size() > 4){
                            List<String> terminators = new ArrayList<>();
                            for (int i = instances.size(); i > 1; i--){
                                Instance instance = instances.get(0);
                                instances.remove(instance);
                                terminators.add(instance.getInstanceId());
                            }
                            terminateInstances(terminators);
                        }

                        Thread.sleep(600);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();

                }
            }
        }
        Runnable autoScaler = new AutoScaler();
        Thread as = new Thread(autoScaler);
        as.start();
    }
    
    public static void buildLoadBalancer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/mzrun", new lbHandler());
        server.setExecutor(null);
        server.start();
    }

    private static class lbHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String response;

            if (instances.size() > 0) {
                String ip = getNextRobin().getPublicIpAddress();
                System.out.println("Redirecting Query " + t.getRequestURI().getQuery() + " to ip: " + ip);
                response = t.getRequestURI().getQuery();
                Headers rHeaders = t.getResponseHeaders();
                rHeaders.set("Location", "http://" + ip + ":" + PORT + "/mzrun.html?" + t.getRequestURI().getQuery() );
                t.sendResponseHeaders(302, 0);
            }
            else {
                System.out.println("Received Query " + t.getRequestURI().getQuery());
                response = "Error code 503: No Available Instance";
                t.sendResponseHeaders(503, response.length());
            }
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();


            //TODO Send to WS with ip of client
        }
    }

    public static TableDescription getTableDescription(){
        DescribeTableRequest request = new DescribeTableRequest().withTableName(TABLENAME);
        return dynamoDB.describeTable(request).getTable();
    }

    public static ScanResult scanTable(String tableName, String query){
            Map<String,Condition> scanFilter = new HashMap<>();
            Condition c = new Condition().withComparisonOperator(ComparisonOperator.EQ.toString()).withAttributeValueList(new AttributeValue().withS(query));
            scanFilter.put("id", c);
            ScanRequest request = new ScanRequest(tableName).withScanFilter(scanFilter);
            return dynamoDB.scan(request);
    }

    public static void main(String[] args){
        try {
            //Initializes instances list and ec2 client and dynamoDB
            ec2 = AmazonEC2ClientBuilder.standard().withRegion(REGION).withCredentials(new AWSStaticCredentialsProvider(getCredentials())).build();
            dynamoDB = AmazonDynamoDBClientBuilder.standard().withCredentials(new AWSStaticCredentialsProvider(getCredentials())).build();
            robinInstance = 0;




            //Starts Auto Scaler
            runAutoScaler();

            //Starts Load Balancer
            buildLoadBalancer();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
