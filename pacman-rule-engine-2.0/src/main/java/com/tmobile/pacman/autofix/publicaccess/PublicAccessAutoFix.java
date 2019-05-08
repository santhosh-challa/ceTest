package com.tmobile.pacman.autofix.publicaccess;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest;
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest;
import com.amazonaws.services.ec2.model.CreateSecurityGroupResult;
import com.amazonaws.services.ec2.model.DeleteSecurityGroupRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DescribeRouteTablesRequest;
import com.amazonaws.services.ec2.model.DescribeRouteTablesResult;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.IpRange;
import com.amazonaws.services.ec2.model.Ipv6Range;
import com.amazonaws.services.ec2.model.ModifyInstanceAttributeRequest;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.Route;
import com.amazonaws.services.ec2.model.RouteTable;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.ec2.model.UserIdGroupPair;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeLoadBalancersRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeLoadBalancersResult;
import com.amazonaws.services.elasticloadbalancingv2.model.LoadBalancer;
import com.amazonaws.services.rds.AmazonRDS;
import com.amazonaws.services.rds.model.DBInstance;
import com.amazonaws.services.rds.model.DescribeDBInstancesRequest;
import com.amazonaws.services.rds.model.DescribeDBInstancesResult;
import com.amazonaws.services.rds.model.ModifyDBInstanceRequest;
import com.amazonaws.services.redshift.AmazonRedshift;
import com.amazonaws.services.redshift.model.Cluster;
import com.amazonaws.services.redshift.model.DescribeClustersRequest;
import com.amazonaws.services.redshift.model.DescribeClustersResult;
import com.amazonaws.services.redshift.model.ModifyClusterRequest;
import com.amazonaws.util.StringUtils;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.tmobile.pacman.common.PacmanSdkConstants;
import com.tmobile.pacman.commons.AWSService;
import com.tmobile.pacman.commons.aws.clients.AWSClientManager;
import com.tmobile.pacman.commons.aws.clients.impl.AWSClientManagerImpl;
import com.tmobile.pacman.commons.exception.RuleExecutionFailedExeption;
import com.tmobile.pacman.commons.exception.UnableToCreateClientException;

public class PublicAccessAutoFix {

	/** The Constant logger. */

	private static final Logger logger = LoggerFactory.getLogger(PublicAccessAutoFix.class);
	
	/** The Constant WAIT_INTERVAL. */
	final static Long WAIT_INTERVAL= 50L;
	
	/** The Constant MAX_ATTEMPTS. */
	final static int MAX_ATTEMPTS= 5;
	
	/** The clinet map. */
	Map<String, Object> clinetMap = null;
	
	/** The pac tag. */
	static String pacTag = "PacBot created SG During Autofix ";
	
	/** The cidr ip. */
	static String cidrIp = "0.0.0.0/0";
	
	/** The cidr ipv 6. */
	static String cidrIpv6 = "::/0";

	/**
	 * Creates the security group description.
	 *
	 * @param securityGroupId the security group id
	 * @return the string
	 */
	private static String createSecurityGroupDescription(String securityGroupId) {
		Date todayDate = new Date();
		return "PacBot copied this SG from " + securityGroupId+ " and removed its inbound rule: 0.0.0.0/0 on " + todayDate;
	}

	/**
	 * Creates the security group name.
	 *
	 * @param pacTag the pac tag
	 * @param reourceId the reource id
	 * @return securitygroupName
	 */
	private static String createSecurityGroupName(String pacTag, String reourceId) {
		long millis = System.currentTimeMillis();
		return pacTag + reourceId + Long.toString(millis);
	}

	/**
	 * Gets the AWS client.
	 *
	 * @param targetType the target type
	 * @param annotation the annotation
	 * @param ruleIdentifyingString the rule identifying string
	 * @return the AWS client
	 * @throws Exception the exception
	 */
	public static Map<String, Object> getAWSClient(String targetType, Map<String, String> annotation, String ruleIdentifyingString) throws Exception {

		StringBuilder roleArn = new StringBuilder();
		Map<String, Object> clientMap = null;
		roleArn.append(PacmanSdkConstants.ROLE_ARN_PREFIX).append(annotation.get(PacmanSdkConstants.ACCOUNT_ID)).append(":").append(ruleIdentifyingString);

		AWSClientManager awsClientManager = new AWSClientManagerImpl();
		try {
			clientMap = awsClientManager.getClient(annotation.get(PacmanSdkConstants.ACCOUNT_ID),roleArn.toString(), AWSService.valueOf(targetType.toUpperCase()),Regions.fromName(annotation.get(PacmanSdkConstants.REGION) == null ? Regions.DEFAULT_REGION
									.getName() : annotation
									.get(PacmanSdkConstants.REGION)), ruleIdentifyingString);
		} catch (UnableToCreateClientException e1) {
			String msg = String.format("unable to create client for account %s  and region %s",annotation.get(PacmanSdkConstants.ACCOUNT_ID), annotation.get(PacmanSdkConstants.REGION));
			logger.error(msg);
			throw new Exception(msg);
		}
		return clientMap;
	}

	/**
	 * Nested security group details.
	 *
	 * @param groupId the group id
	 * @param ipPermissionstobeAdded the ip permissionstobe added
	 * @param ec2Client the ec 2 client
	 * @param publiclyAccessible the publicly accessible
	 * @param alreadyCheckedSgSet the already checked sg set
	 * @param portToCheck the port to check
	 * @return the sets the
	 */
	public static Set<String> nestedSecurityGroupDetails(String groupId, Collection<IpPermission> ipPermissionstobeAdded, AmazonEC2 ec2Client, Set<String> publiclyAccessible,Set<String> alreadyCheckedSgSet,Integer portToCheck) {
		Set<String> sgSet = new HashSet<>();
		sgSet.add(groupId);
		List<SecurityGroup> securityGroups = getExistingSecurityGroupDetails(sgSet, ec2Client);
		
		List<IpRange> updatedIpranges;
		List<Ipv6Range> updatedIp6ranges;
		for (SecurityGroup securityGroup : securityGroups) {
			for (IpPermission ipPermission : securityGroup.getIpPermissions()) {

				updatedIpranges = new ArrayList<>();
				updatedIp6ranges = new ArrayList<>();

				for (IpRange ipRangeValue : ipPermission.getIpv4Ranges()) {
					if (ipRangeValue.getCidrIp().equals(cidrIp)) {
						if ((portToCheck > 0 && ipPermission.getFromPort().equals(portToCheck)) || portToCheck.equals(0)) {
							publiclyAccessible.add("Yes");
							for (UserIdGroupPair usergroupPair : ipPermission.getUserIdGroupPairs()) {
								IpRange ipv4Ranges = new IpRange();
								ipPermission.setIpv4Ranges(Arrays.asList(ipv4Ranges));
								ipPermission.setUserIdGroupPairs(Arrays.asList(usergroupPair));
								ipPermissionstobeAdded.add(ipPermission);
							}
						}else {
							updatedIpranges.add(ipRangeValue);
						}

					} else {
						
						updatedIpranges.add(ipRangeValue);
					}

				}
				
				if(ipPermission.getIpv4Ranges().isEmpty() && ipPermission.getIpv6Ranges().isEmpty() ){
					for (UserIdGroupPair usergroupPair : ipPermission.getUserIdGroupPairs()) {
						ipPermission.setUserIdGroupPairs(Arrays.asList(usergroupPair));
						ipPermissionstobeAdded.add(ipPermission);
					}
				}

				for (Ipv6Range ip6RangeValue : ipPermission.getIpv6Ranges()) {

					if (ip6RangeValue.getCidrIpv6().equals(cidrIpv6)) {
						if((portToCheck > 0 && ipPermission.getFromPort().equals(portToCheck)) || portToCheck.equals(0)){
							publiclyAccessible.add("Yes");
							for (UserIdGroupPair usergroupPair : ipPermission.getUserIdGroupPairs()) {
								Ipv6Range ipv6Ranges = new Ipv6Range();
								ipPermission.setIpv6Ranges(Arrays.asList(ipv6Ranges));
								ipPermission.setUserIdGroupPairs(Arrays.asList(usergroupPair));
								ipPermissionstobeAdded.add(ipPermission);
							}
						}else{
							updatedIp6ranges.add(ip6RangeValue);	
						}
					} else {
						updatedIp6ranges.add(ip6RangeValue);
					}

				}

				if (!updatedIpranges.isEmpty()) {
					ipPermission.setIpv4Ranges(updatedIpranges);
					for (Ipv6Range ip6RangeValue : ipPermission.getIpv6Ranges()) {

						if (ip6RangeValue.getCidrIpv6().equals(cidrIpv6)) {
							List<Ipv6Range> clearIpv6ranges = new ArrayList<>();
							ipPermission.setIpv6Ranges(clearIpv6ranges);
						}
					}
				}

				if (!updatedIp6ranges.isEmpty()) {
					ipPermission.setIpv6Ranges(updatedIp6ranges);
				}

				if ((!updatedIpranges.isEmpty() || !updatedIp6ranges.isEmpty()) && (!ipPermissionstobeAdded.contains(ipPermission))) {
					ipPermissionstobeAdded.add(ipPermission);
				}
			}
		}
		
		return publiclyAccessible;
	}

	/**
	 * Creates the security group.
	 *
	 * @param sourceSecurityGroupId the source security group id
	 * @param vpcId the vpc id
	 * @param ec2Client the ec 2 client
	 * @param ipPermissionsToBeAdded the ip permissions to be added
	 * @param resourceId the resource id
	 * @param defaultCidrIp the default cidr ip
	 * @param existingIpPermissions the existing ip permissions
	 * @return the string
	 * @throws Exception the exception
	 */
	public static String createSecurityGroup(String sourceSecurityGroupId, String vpcId, AmazonEC2 ec2Client, Collection<IpPermission> ipPermissionsToBeAdded, String resourceId,String defaultCidrIp,List<IpPermission> existingIpPermissions) throws Exception {
		String createdSecurityGroupId = null;
		try {
			CreateSecurityGroupRequest createsgRequest = new CreateSecurityGroupRequest();
			createsgRequest.setGroupName(createSecurityGroupName(pacTag,resourceId));
			createsgRequest.setVpcId(vpcId);
			createsgRequest.setDescription(createSecurityGroupDescription(sourceSecurityGroupId));
			CreateSecurityGroupResult createResult = ec2Client.createSecurityGroup(createsgRequest);
			createdSecurityGroupId = createResult.getGroupId();

			if (!createdSecurityGroupId.isEmpty()) {
				logger.info("Security Group {} created successfully" ,createdSecurityGroupId);
				// Authorize newly created securityGroup with Inbound Rules
				AuthorizeSecurityGroupIngressRequest authRequest = new AuthorizeSecurityGroupIngressRequest();
				authRequest.setGroupId(createdSecurityGroupId);
				if(ipPermissionsToBeAdded.isEmpty()){
                    IpRange ipv4Ranges = new IpRange();
                    ipv4Ranges.setCidrIp(defaultCidrIp);
					for (IpPermission ipPermission : existingIpPermissions) {

						if (!ipPermission.getIpv4Ranges().isEmpty()) {
							ipPermission.setIpv4Ranges(Arrays.asList(ipv4Ranges));
						}

						if (!ipPermission.getIpv6Ranges().isEmpty()) {
							Ipv6Range ipv6Range = new Ipv6Range();
							ipPermission.setIpv6Ranges(Arrays.asList(ipv6Range));
						}
						if (!ipPermission.getIpv4Ranges().isEmpty() || !ipPermission.getIpv6Ranges().isEmpty()) {
							ipPermissionsToBeAdded.add(ipPermission);
						}
					}
                }
				authRequest.setIpPermissions(ipPermissionsToBeAdded);
				ec2Client.authorizeSecurityGroupIngress(authRequest);

			}

		} catch (Exception e) {
			logger.error(e.getMessage());
			logger.debug(e.getMessage());
			throw new RuntimeException(sourceSecurityGroupId+ " SG copy failed");
		}
		return createdSecurityGroupId;
	}

	

	

	

	/**
	 * Gets the existing security group details.
	 *
	 * @param securityGroupList the security group list
	 * @param ec2Client the ec 2 client
	 * @return the existing security group details
	 */
	public static List<SecurityGroup> getExistingSecurityGroupDetails(Set<String> securityGroupList, AmazonEC2 ec2Client) {
		RetryConfig config = RetryConfig.custom().maxAttempts(MAX_ATTEMPTS).waitDuration(Duration.ofSeconds(WAIT_INTERVAL)).build();
		RetryRegistry registry = RetryRegistry.of(config);
		DescribeSecurityGroupsRequest securityGroups = new DescribeSecurityGroupsRequest();
   		securityGroups.setGroupIds(securityGroupList);
		Retry retry = registry.retry(securityGroups.toString());
   		
		Function<Integer, List<SecurityGroup>> decorated
		  =  Retry.decorateFunction(retry, (Integer s) -> {
			  DescribeSecurityGroupsResult  groupsResult =  ec2Client.describeSecurityGroups(securityGroups);
			  return groupsResult.getSecurityGroups();
		    });
		return decorated.apply(1);
	}

	
	
   
    
   
    /**
     * Apply security groups to ec 2.
     *
     * @param amazonEC2 the amazon EC 2
     * @param sgIdToBeAttached the sg id to be attached
     * @param resourceId the resource id
     * @return true, if successful
     * @throws Exception the exception
     */
    public static boolean applySecurityGroupsToEc2(AmazonEC2 amazonEC2, Set<String> sgIdToBeAttached, String resourceId) throws Exception {
        boolean applysgFlg = false;
        try {
        	ModifyInstanceAttributeRequest modifyInstanceAttributeRequest = new ModifyInstanceAttributeRequest();
        	modifyInstanceAttributeRequest.setInstanceId(resourceId);
        	modifyInstanceAttributeRequest.setGroups(sgIdToBeAttached);
        	amazonEC2.modifyInstanceAttribute(modifyInstanceAttributeRequest);
            applysgFlg = true;
        } catch (Exception e) {
            logger.error("Apply Security Group operation failed for ec2 {}" , resourceId );
         throw new Exception(e);
        }
        return applysgFlg;
    }
    
    
   
    
    /**
     * Gets the instance details for ec 2.
     *
     * @param clientMap the client map
     * @param resourceId the resource id
     * @return the instance details for ec 2
     * @throws Exception the exception
     */
    public static Instance getInstanceDetailsForEc2(Map<String,Object> clientMap,String resourceId) throws Exception {
    	AmazonEC2 ec2Client = (AmazonEC2) clientMap.get("client");
    	DescribeInstancesRequest describeInstancesRequest = new DescribeInstancesRequest();
		describeInstancesRequest.setInstanceIds(Arrays.asList(resourceId));
		
		
		RetryConfig config = RetryConfig.custom().maxAttempts(MAX_ATTEMPTS).waitDuration(Duration.ofSeconds(WAIT_INTERVAL)).build();
		RetryRegistry registry = RetryRegistry.of(config);
		
		Retry retry = registry.retry(describeInstancesRequest.toString());
   		
		Function<Integer, Instance> decorated
		  =  Retry.decorateFunction(retry, (Integer s) -> {
			  DescribeInstancesResult  describeInstancesResult =  ec2Client.describeInstances(describeInstancesRequest);
			  List<Reservation> reservations = describeInstancesResult.getReservations();
				Reservation reservation = reservations.get(0);
				List<Instance> instances = reservation.getInstances();
				return instances.get(0);
		    });
		return decorated.apply(1);
	
    }

}
