
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;

import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClientBuilder;
import com.amazonaws.services.autoscaling.model.*;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.services.ec2.model.Instance;


public class Scheduler {

    private static final String REGION = "eu-west-1";
    private static final String AUTOSCALING_GROUP_NAME = "RENDERFARM_ASG";
    private static final int THREAD_COUNT_ON_INSTANCES = 2;
    private static final int AGS_POLL_RATE_SECONDS = 30;
    private static final double ESTIMATED_COST_THRESHOLD = 30000; // FIXME
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);


    private static ConcurrentHashMap<String, ConcurrentHashMap<String, Job>> _instanceJobMap = new ConcurrentHashMap<String, ConcurrentHashMap<String, Job>>();
    private static MetricsManager _metricsManager;
    private static ConcurrentHashMap<String, Long> _latestJobForInstance;
    private static AmazonAutoScaling _amazonAutoScalingClient;
    private static AmazonEC2 _ec2Client;
    
    
    private static List<String> getGroupIPs() {
        List<String> IPs = new LinkedList<>();
        List<AutoScalingInstanceDetails> details = _amazonAutoScalingClient
                .describeAutoScalingInstances()
                .getAutoScalingInstances();
		
        /*Get the instance IDs of the instances in our autoscaling group*/
        List<String> groupInstances = new LinkedList<>();
        for(AutoScalingInstanceDetails detail : details){
            if(detail.getAutoScalingGroupName().equals(AUTOSCALING_GROUP_NAME)) {
                groupInstances.add(detail.getInstanceId());
            }
        }
        
        /*Now get their public IPs*/
        DescribeInstancesRequest describeRequest = new DescribeInstancesRequest();
        describeRequest.setInstanceIds(groupInstances);
        DescribeInstancesResult instances = _ec2Client.describeInstances(describeRequest);
        List<Reservation> reservations = instances.getReservations();
        for(Reservation r : reservations){
            for(Instance i : r.getInstances()){
                IPs.add(i.getPublicIpAddress());
            }
        }
        
        return IPs;
    }
    
    
    public static void init() {
        _amazonAutoScalingClient = AmazonAutoScalingClientBuilder
                .standard()
                .withRegion(REGION)
                .build();
                
        _ec2Client = AmazonEC2ClientBuilder
                .standard()
                .withRegion(REGION)
                .build();
                
        // Populate our instance map with the ips to the instances in our auto-scaling group.
        
        _metricsManager = new MetricsManager();
        _metricsManager.init();
        
// 		setDesiredCapacity(1);
    }
    
    
    
    public static String getIpForJob(Job newJob) {
    
        //TODO this should be done periodically and not every time we receive a request
        // Refresh instances before we select server.
        Scheduler.pollIPsFromAGS();
        
        //Get the estimated cost for running this job
        double cost = newJob.estimateCost(_metricsManager);
        
		String bestIP = "";
		int jobCountOnBestIP = 9999;
		double totalEstimatedCost = 0;
		
		for(Map.Entry<String, ConcurrentHashMap<String, Job>> ipJobsKeyPair : _instanceJobMap.entrySet()) {
			
			String ip = ipJobsKeyPair.getKey();
			ConcurrentHashMap<String, Job> jobsForInstance = ipJobsKeyPair.getValue();
			
			// return first instance found with free threads
			if (jobsForInstance.size() < THREAD_COUNT_ON_INSTANCES) {
				return ip;
				
			// FIXME calculate best possible based on some formula
			} else {
				if (jobsForInstance.size() < jobCountOnBestIP) {
					bestIP = ip;
					jobCountOnBestIP = jobsForInstance.size();
					
					for(Job job : jobsForInstance.values()) {
						totalEstimatedCost += job.getEstimatedCost();
					}
					System.out.println(red("\nAt capacity: ") + ip);
// 					System.out.println(red("\nAt capacity: ") + ip + "\tcurrent cost " + totalEstimatedCost);
				}
			}
		}
		
		
		// FIXME 
		// If we come here it means that all the servers where fully loaded
		// Now we have to select by job size or according to time.
		
		// start a new instance based on a threshoold
		System.out.println(yellow("\nTotal Estimate: ") + totalEstimatedCost);
		if(totalEstimatedCost > ESTIMATED_COST_THRESHOLD) {
			System.out.println("Starting a new instance...");
			setDesiredCapacity(_instanceJobMap.size()+1);
		}
		
        return bestIP;
    }


    public static synchronized String scheduleJob(Job newJob) {
		
		String ip = getIpForJob(newJob);
		
		_instanceJobMap.get(ip).put(newJob.getJobId(), newJob);
		
		newJob.start();
		
        return ip;
    }
    
    
    public static  void finishJob(Job job, String instanceIp) {
        job.stop();
		
		ConcurrentHashMap<String, Job> jobsForInstance = _instanceJobMap.get(instanceIp);
		synchronized(jobsForInstance) {
			jobsForInstance.remove(job.getJobId());
        }
    }
    

    private static void pollIPsFromAGS() {
        List<String> ipsInAGS = Scheduler.getGroupIPs();
        
        // Add ip's that are not in the group.
        // This is the case where we have added a new instance.
        for(String ip : ipsInAGS) {
            if (ip != null && !_instanceJobMap.containsKey(ip)) {
                _instanceJobMap.put(ip, new ConcurrentHashMap<String, Job> ());
                System.out.println(cyan("\nAdded instance: ") + ip);
            }
        }
        
        // Check if some of the IPs in the _instanceJobMap does not exist
        // in the ASG. (This is the case where we have removed an instance.
        for(String ip: _instanceJobMap.keySet()) {
            if (!ipsInAGS.contains(ip)) {
                _instanceJobMap.remove(ip);
                System.out.println(cyan("\nRemoved instance: ") + ip);
            }
        }
        
    }
    
    
    private static void setDesiredCapacity(int capacity) {
        SetDesiredCapacityRequest desiredCapacity = new SetDesiredCapacityRequest()
                    .withAutoScalingGroupName(AUTOSCALING_GROUP_NAME)
                    .withDesiredCapacity(capacity);
        _amazonAutoScalingClient.setDesiredCapacity(desiredCapacity);
    }
    

    public static boolean allInstancesAreFull() {
        if (_instanceJobMap.size() == 0)
            return true;
            
        for(Map.Entry<String, ConcurrentHashMap<String, Job>> entry : _instanceJobMap.entrySet()) {
            ConcurrentHashMap<String, Job> jobsForInstance = entry.getValue();
            if(jobsForInstance.size() < THREAD_COUNT_ON_INSTANCES)
                return false;
        }
        
        return true;
    }
    

    

    public static void main(String[] args) {
        Scheduler.init();
    }
    
    public static String red(String text) { return "\u001B[31m" + text + "\u001B[0m"; }
    public static String green(String text) { return "\u001B[32m" + text + "\u001B[0m"; }
	public static String yellow(String text) { return "\u001B[33m" + text + "\u001B[0m"; }
	public static String cyan(String text) { return "\u001B[36m" + text + "\u001B[0m"; }
	public static String italic(String text) { return "\u001B[03m" + text + "\u001B[0m"; }

    
}

