package ist.cnv.maze.loadbalancer;

import com.amazonaws.services.elasticloadbalancingv2.model.CreateLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.CreateLoadBalancerResult;
import com.amazonaws.services.elasticloadbalancingv2.model.IpAddressType;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancingClientBuilder;
import com.amazonaws.services.elasticloadbalancingv2.model.Listener;
import com.amazonaws.services.elasticloadbalancingv2.model.CreateTargetGroupRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.CreateTargetGroupResult;
import com.amazonaws.services.elasticloadbalancingv2.model.RegisterTargetsRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.RegisterTargetsResult;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetGroup;
import com.amazonaws.services.elasticloadbalancingv2.model.LoadBalancer;


public class BalancerLauncher{

	private static final String LBNAME = "CNVLB";
	private static final String TGNAME = "CNVTG";
	private static final String PROTOCOL = "HTTP";
	private static final String REGION = "us-east-1";
	private static final String VPCID = "vpc-928105e9";
	private static final String[] SUBNETS = {"subnet-6b8fa536", "subnet-482cd202"};
	private static final int PORT = 8000;
	


	public static TargetGroup buildTargetGroup(AmazonElasticLoadBalancing client){
		CreateTargetGroupRequest request = new CreateTargetGroupRequest().withName(TGNAME).withPort(PORT).withProtocol(PROTOCOL).withVpcId(VPCID);
		CreateTargetGroupResult result = client.createTargetGroup(request);
		return result.getTargetGroups().get(0);
	}

	public static LoadBalancer buildLoadBalancer(AmazonElasticLoadBalancing client){
		CreateLoadBalancerRequest request = new CreateLoadBalancerRequest().withName(LBNAME).withSubnets(SUBNETS[0], SUBNETS[1]);
	    CreateLoadBalancerResult result = client.createLoadBalancer(request);
		return result.getLoadBalancers().get(0);
	}

	public static RegisterTargetsResult register(AmazonElasticLoadBalancing client, TargetGroup tg){
		RegisterTargetsRequest request = new RegisterTargetsRequest().withTargetGroupArn(tg.getTargetGroupArn());
		return client.registerTargets(request);
	}

	
	public static void main(String[] args){
		AmazonElasticLoadBalancing client = AmazonElasticLoadBalancingClientBuilder.standard().withRegion(REGION).build();

		try{
			LoadBalancer lb = buildLoadBalancer(client);
			TargetGroup tg = buildTargetGroup(client);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	
}
