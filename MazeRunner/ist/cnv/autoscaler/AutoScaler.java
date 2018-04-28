package ist.cnv.autoscaler;

import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClientBuilder;
import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupRequest;
import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupResult;
import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.CreateLaunchConfigurationRequest;
import com.amazonaws.services.autoscaling.model.CreateLaunchConfigurationResult;
import com.amazonaws.services.autoscaling.model.LaunchConfiguration;
import com.amazonaws.services.autoscaling.model.AttachLoadBalancerTargetGroupsRequest;
import com.amazonaws.services.autoscaling.model.AttachLoadBalancerTargetGroupsResult;

import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancingClientBuilder;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetGroup;



public class AutoScaler{

	private static final String ASNAME = "CNVAS";
	private static final String LCNAME = "CNVCONFIG";
	private static final String ITYPE = "t2.micro";
	private static final String IMAGEID = "ami-78a91a07";
	private static final String SECGROUP = "cnv-ssh+http";
	private static final String SUBNET = "subnet-6b8fa536";
	private static final String REGION = "us-east-1";
	private static final int MIN = 1;
	private static final int MAX = 3;

	public static void buildLaunchConfiguration(AmazonAutoScaling client){
		CreateLaunchConfigurationRequest request = new CreateLaunchConfigurationRequest().withImageId(IMAGEID).
			withLaunchConfigurationName(LCNAME).withInstanceType(ITYPE);
		CreateLaunchConfigurationResult result = client.createLaunchConfiguration(request);
	}

		
	public static void buildAutoScaler(AmazonAutoScaling client){
		CreateAutoScalingGroupRequest request = new CreateAutoScalingGroupRequest().withAutoScalingGroupName(ASNAME).
			withLaunchConfigurationName(LCNAME).withMinSize(MIN).withMaxSize(MAX).withVPCZoneIdentifier(SUBNET);
		CreateAutoScalingGroupResult result = client.createAutoScalingGroup(request);
	}

	public static void attachAutoScaler(AmazonAutoScaling asclient){
		AmazonElasticLoadBalancing lbclient = AmazonElasticLoadBalancingClientBuilder.standard().withRegion(REGION).build();
		TargetGroup tg = lbclient.describeTargetGroups(new DescribeTargetGroupsRequest()).getTargetGroups().get(0);
		AttachLoadBalancerTargetGroupsRequest request = new AttachLoadBalancerTargetGroupsRequest()
			.withAutoScalingGroupName(ASNAME).withTargetGroupARNs(tg.getTargetGroupArn());
		asclient.attachLoadBalancerTargetGroups(request);
	}
	
	public static void main(String[] args){
		AmazonAutoScaling client = AmazonAutoScalingClientBuilder.standard().withRegion(REGION).build();
		buildLaunchConfiguration(client);
		buildAutoScaler(client);
		attachAutoScaler(client);
	}
	
}
