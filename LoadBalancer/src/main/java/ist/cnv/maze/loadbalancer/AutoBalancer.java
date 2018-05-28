package ist.cnv.maze.loadbalancer;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.*;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.*;

public class AutoBalancer {

    private static final int MIN_INS = 1;
    private static final int MAX_INS = 1;
    private static final String PINGPATH = "/test";
    private static final String MAZEPATH = "/mzrun.html";
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
    private static final int INITTIME = 300000;
    private static final int ASWAIT = 60000;


    private static AWSCredentials getCredentials() {
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



    private static void launchInstances(int min, int max){
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
        System.out.println("Started " + max + " requested Instances");
    }

    private static void terminateInstances(List<String> ids){
        TerminateInstancesRequest request = new TerminateInstancesRequest(ids);
        ec2.terminateInstances(request);
    }

    private static Instance getNextRobin() {
        Instance i;
        int x = 0;
        if (instances.size() == 0){
            System.out.println("No Available Instances");
            return null;
        }
        for (i = instances.get(robinInstance); !isOk(i); robinInstance = (robinInstance + 1) % instances.size()){
            if (x == instances.size()){
                System.out.println("No initialized instance");
                return null;
            }
            x++;
        }
        return i;
    }

    private static Boolean isOk(Instance i){
        return getInstanceStatus(i).getInstanceStatus().getStatus().equals("ok");
    }

    private static Boolean ping(URL url)  {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Content-Length", Integer.toString(0));
            connection.setDoOutput(true);


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

    private static void runAutoScaler() {
        class AutoScaler implements Runnable {

            @Override
            public void run() {
                try {
                    if (instances.size() == 0) {
                        launchInstances(MIN_INS, MAX_INS);
                    }
                    do {
                        List<String> malfunctions = new ArrayList<>();
                        if (instances.size() > 0) {
                            for (Instance x : instances) {
                                InstanceStatus status = getInstanceStatus(x);
                                if (status.getInstanceStatus().getStatus().equals("initializing")) {
                                    System.out.println("Instance still Initializing");
                                    long compareTime = x.getLaunchTime().getTime() + INITTIME;
                                    Date date = new Date();
                                    if (date.getTime() > compareTime) {
                                        System.out.println("Instance timed out");
                                        malfunctions.add(x.getInstanceId());
                                    }
                                } else {
                                    URL url = new URL("http://" + x.getPublicDnsName() + ":" + PORT + PINGPATH);
                                    System.out.println("http://" + x.getPublicDnsName() + ":" + PORT + PINGPATH);
                                    Boolean p = ping(url);
                                    if (!p) {
                                        System.out.println("Instance " + x.getInstanceId() + " is malfunctioning :(");
                                        malfunctions.add(x.getInstanceId());
                                    }
                                }
                            }
                            if (malfunctions.size() > 0) {
                                List<Instance> instanceList = new ArrayList<>();
                                for (Instance i : instances) {
                                    if (!malfunctions.contains(i.getInstanceId())) {
                                        instanceList.add(i);
                                    }
                                }
                                instances = instanceList;
                                terminateInstances(malfunctions);
                                launchInstances(malfunctions.size(), malfunctions.size());
                            }
                            Thread.sleep(ASWAIT);
                        }
                    } while (true);

                } catch (Exception e) {
                    e.printStackTrace();

                }
            }
        }
        Runnable autoScaler = new AutoScaler();
        Thread as = new Thread(autoScaler);
        as.start();
    }

    private static InstanceStatus getInstanceStatus(Instance i) {
        DescribeInstanceStatusRequest request = new DescribeInstanceStatusRequest().withInstanceIds(i.getInstanceId());
        DescribeInstanceStatusResult result = ec2.describeInstanceStatus(request);
        return result.getInstanceStatuses().get(0);
    }

    private static void buildLoadBalancer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext(MAZEPATH, new lbHandler());
        server.setExecutor(null);
        server.start();
    }

    private static class lbHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String response;
            Instance robin = getNextRobin();

            if (robin != null) {
                String ip = robin.getPublicIpAddress();
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
            dynamoDB = AmazonDynamoDBClientBuilder.standard().withCredentials(new AWSStaticCredentialsProvider(getCredentials())).withRegion(REGION).build();
            robinInstance = 0;




            //Starts Auto Scaler
            runAutoScaler();

            //Starts Load Balancer
            buildLoadBalancer();

            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    super.run();
                    if (instances.size() > 0) {
                        List<String> ids = new ArrayList<>();
                        for (Instance i : instances) {
                            ids.add(i.getInstanceId());
                        }
                        terminateInstances(ids);
                        System.out.println("Instances Terminated");
                    }
                }

            });

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
