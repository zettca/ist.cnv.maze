package ist.cnv.maze.loadbalancer;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;

import java.util.ArrayList;
import java.util.List;

public class AutoBalancer {

    private static final String LBNAME = "CNVLB";
    private static final String TGNAME = "CNVTG";
    private static final String SECGROUP = "cnv-ssh+http";
    private static final String AMI = "ami-a53bb7da";
    private static final String KEY = "CNV-proj";
    private static final String PROTOCOL = "HTTP";
    private static final String REGION = "us-east-1";
    private static final String ITYPE = "t2.micro";
    private static final String VPCID = "vpc-928105e9";
    private static final String[] SUBNETS = {"subnet-6b8fa536", "subnet-482cd202"};
    private static final int PORT = 8000;
    private static List<Instance> instances;



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



    public static void launchInstances(AmazonEC2 ec2, int min, int max){
        RunInstancesRequest request = new RunInstancesRequest();


        request.withImageId(AMI)
                .withInstanceType(ITYPE)
                .withMinCount(min)
                .withMaxCount(max)
                .withKeyName(KEY)
                .withSecurityGroups(SECGROUP);

        RunInstancesResult result =	ec2.runInstances(request);
        instances.addAll(result.getReservation().getInstances());
    }

    public static void terminateInstances(AmazonEC2 ec2, List<String> ids){
        TerminateInstancesRequest request = new TerminateInstancesRequest(ids);
        ec2.terminateInstances(request);
    }


    public static void runAutoScaler(AmazonEC2 ec2) {
        class AutoScaler implements Runnable {
            AmazonEC2 ec2;

            public AutoScaler(AmazonEC2 ec2) {
                this.ec2 = ec2;
            }

            @Override
            public void run() {
                try {
                    while(true) {
                        if (instances.size() == 0) {
                            launchInstances(ec2, 1, 1);
                        }
                        else if (instances.size() > 1){
                            List<String> terminators = new ArrayList<>();
                            for (int i = instances.size(); i > 1; i--){
                                Instance t = instances.get(0);
                                instances.remove(t);
                                terminators.add(t.getInstanceId());
                            }
                            terminateInstances(ec2, terminators);
                        }

                        Thread.sleep(6000);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        Runnable autoScaler = new AutoScaler(ec2);
        Thread as = new Thread(autoScaler);
        as.start();
    }

    public static void main(String[] args){
        try {
            //Initializes instances list and ec2 client
            instances = new ArrayList<>();
            AmazonEC2 ec2 = AmazonEC2ClientBuilder.standard().withRegion(REGION).withCredentials(new AWSStaticCredentialsProvider(getCredentials())).build();

            //Starts AutoScaler
            runAutoScaler(ec2);

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
