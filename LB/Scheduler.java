
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;

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
    private static final double NEW_INSTANCE_THRESHOLD = 2; // FIXME

    
    
    private static final double ESTIMATED_COST_THRESHOLD = 30000; // FIXME
    private static final int INSTANCES_IP_CHECK_INTERVAL = 30; //In Seconds
    private static final ScheduledExecutorService _scheduledExecutor = Executors.newScheduledThreadPool(1);

    private ConcurrentHashMap<String, ConcurrentHashMap<String, Job>> _instanceJobMap = new ConcurrentHashMap<String, ConcurrentHashMap<String, Job>>();
    private MetricsManager _metricsManager;
    private final List<String> _pendingBoot = new LinkedList<>();
    private ConcurrentHashMap<String, Long> _latestJobForInstance;
    private AmazonAutoScaling _amazonAutoScalingClient;
    private AmazonEC2 _ec2Client;


    private HashMap<String, String> getGroupInstances(){
        HashMap<String, String> instancesMap = new HashMap<>();
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
        
        // FIXME when load balancer is an AWS image use internal IPs
        // get the instances public IPs

        /*Now get their public IPs*/
        DescribeInstancesRequest describeRequest = new DescribeInstancesRequest();
        describeRequest.setInstanceIds(groupInstances);
        DescribeInstancesResult instances = _ec2Client.describeInstances(describeRequest);
        List<Reservation> reservations = instances.getReservations();
        for(Reservation r : reservations){
            for(Instance i : r.getInstances()){
                instancesMap.put(i.getPublicIpAddress(), i.getInstanceId());
            }
        }
        return instancesMap;
    }

    private List<String> getGroupIPs() {
        Set<String> keyset = getGroupInstances().keySet();
        return new LinkedList<>(keyset);
    }
    

    public void init() {
        _amazonAutoScalingClient = AmazonAutoScalingClientBuilder
                .standard()
                .withRegion(REGION)
                .build();
                
        _ec2Client = AmazonEC2ClientBuilder
                .standard()
                .withRegion(REGION)
                .build();
                
        // FIXME populate our instance map with the ips to the instances in our auto-scaling group.
        
        _metricsManager = new MetricsManager();
        _metricsManager.init();
    
    
    
        _scheduledExecutor.scheduleAtFixedRate(new GetGroupIps(), 0, INSTANCES_IP_CHECK_INTERVAL, TimeUnit.SECONDS);


// 		setDesiredCapacity(1);
    }

    private class GetGroupIps implements Runnable{
        private int TIME_TO_BOOT = 60000;//1min
        public void run(){
            final List<String> ipsInAGS = getGroupIPs();

            // Check if some of the IPs in the _instanceJobMap does not exist
            // in the ASG. (This is the case where we have removed an instance.
            for(String ip: _instanceJobMap.keySet()) {
                if (!ipsInAGS.contains(ip)) {
                    removeInstance(ip);
                }
            }

            // Add ip's that are not in the group.
            // This is the case where we have added a new instance.
            for(String ipInAgs : ipsInAGS) {
                final String ip = ipInAgs;//Hack
                if (ip != null && !_instanceJobMap.containsKey(ip)) { //If instance is not in our map

                    //Then check if there is a thread already looking into it
                    boolean alreadyWaiting = false;
                    synchronized (_pendingBoot) {
                        for (String pendingIp : _pendingBoot)
                            if (pendingIp.equals(ip))
                                alreadyWaiting = true; //There is a thread already looking into it
                        _pendingBoot.add(ip);
                    }
                    if(alreadyWaiting)
                        continue;

                    //If not then its our responsibility
                    Executor ex = Executors.newSingleThreadExecutor();
                    ex.execute(new Runnable() { //Create a new thread to add the instance once it responds to http
                        @Override
                        public void run(){
                            for(int retries=3; retries>0; retries--) {
                                try {
                                    HttpURLConnection connection = (HttpURLConnection) new URL("http", ip,
                                            LoadBalancer.WS_PORT, LoadBalancer.TEST_RESOURCE).openConnection();
                                    connection.setConnectTimeout(TIME_TO_BOOT);
                                    if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                                        connection.disconnect();
                                        addInstance(ip);
                                        synchronized (_pendingBoot){
                                            _pendingBoot.remove(ip);
                                        }
                                        return;
                                    }
                                    throw new IOException("Bad http response");
                                } catch (IOException e) {
                                    try {
                                        Thread.sleep(TIME_TO_BOOT);
                                        //If the connection fails try sleeping for TIME_TO_BOOT and then try again
                                    }catch (InterruptedException ex){
                                        //empty
                                    }
                                }
                            }
                            synchronized (_pendingBoot){
                                _pendingBoot.remove(ip);
                            }
                            reboot(ip); //If the machine does not respond for retries retries reboot it
                        }
                    });
                }
            }

        }
    }

    private synchronized void addInstance(String ip){
        if(!_instanceJobMap.containsKey(ip)) {
            _instanceJobMap.put(ip, new ConcurrentHashMap<String, Job>());
            System.out.println(cyan("\nAdded instance: ") + ip);
        }
    }

    private synchronized ConcurrentHashMap<String, Job> removeInstance(String ip){
        ConcurrentHashMap<String, Job> removedInstance = null;
        if(_instanceJobMap.containsKey(ip)) {
            removedInstance = _instanceJobMap.get(ip);
            _instanceJobMap.remove(ip);
        }
        System.out.println(cyan("\nRemoved instance: ") + ip);
        return removedInstance;
    }

    public void instanceFailure(String ip){
        //Remove the instance from our map
        removeInstance(ip);

        //reboot it if it is still running
        List<String> ipsInAGS = getGroupIPs();
        for(String AGSip : ipsInAGS){
            if(AGSip == null)
                continue;
            if(AGSip.equals(ip)){
                reboot(ip);
            }
        }

    }

    private void reboot(String ip){
        TerminateInstanceInAutoScalingGroupRequest terminateRequest = new TerminateInstanceInAutoScalingGroupRequest().withShouldDecrementDesiredCapacity(false);
        HashMap<String, String> instances = getGroupInstances();
        if(instances.containsKey(ip)) {
            System.out.println(red("Rebooting: ") + ip);
            terminateRequest.setInstanceId(instances.get(ip));
            _amazonAutoScalingClient.terminateInstanceInAutoScalingGroup(terminateRequest);
        }
    }

    public String getIpForJob(Job newJob) {
        //Get the estimated cost for running this job
        double cost = newJob.estimateCost(_metricsManager);

		String bestIP = "";
		int jobCountOnBestIP = 999999;
		double costOnInstance = 0;
		double costOnBestInstance = 9999999;
		int totalRunningJobs = 0;
		System.out.println();
		for(Map.Entry<String, ConcurrentHashMap<String, Job>> ipJobsKeyPair : _instanceJobMap.entrySet()) {
			String ip = ipJobsKeyPair.getKey();
			ConcurrentHashMap<String, Job> jobsForInstance = ipJobsKeyPair.getValue();
			
			// return first instance found with free threads
			if (jobsForInstance.size() < THREAD_COUNT_ON_INSTANCES) {
				return ip;
				
			} else {
				costOnInstance = 0;
				totalRunningJobs += jobsForInstance.size();
				for(Job job : jobsForInstance.values()) {
					costOnInstance += job.getEstimatedCost();
				}
				System.out.println(red("At capacity: ") + italic(ip));
				
				
				if ((costOnInstance * jobsForInstance.size() + cost) < jobCountOnBestIP) {
					bestIP = ip;
					jobCountOnBestIP = jobsForInstance.size();
					costOnBestInstance = costOnInstance;
				}
				
// 				// FIXME calculate best possible based on some formula
// 				if (jobsForInstance.size() < jobCountOnBestIP) {
// 					bestIP = ip;
// 					jobCountOnBestIP = jobsForInstance.size();
// 				}
			}
		}
		
		
		scaleUp(totalRunningJobs, _instanceJobMap.size());
		
        return bestIP;
    }
    
    
    public void scaleUp(final int totalRunningJobs, final int totalInstances) {
		
		Executors.newSingleThreadExecutor().execute(new Runnable() {
				@Override
				public void run() {
					// start a new instance based on a ration between the exceeding and a threshold
					System.out.println(yellow("All instances full, total jobs running: ") + totalRunningJobs);
					if(totalRunningJobs > totalInstances * NEW_INSTANCE_THRESHOLD) {
						int incrementBy = (int) (totalRunningJobs / THREAD_COUNT_ON_INSTANCES) - 1;
						if(incrementBy > 0) {
							System.out.println(italic(cyan("Starting ") + incrementBy + cyan(" new instances...")) + "\tDesired total: " + (totalInstances + incrementBy));
							setDesiredCapacity(totalInstances + incrementBy);
						}
					}
				}
			});

    }

    public synchronized String scheduleJob(Job newJob) {
		
		String ip = getIpForJob(newJob);
		
		_instanceJobMap.get(ip).put(newJob.getJobId(), newJob);
		
		newJob.start();
		
        return ip;
    }
    
    
    public  void finishJob(Job job, String instanceIp) {
        job.stop();
		
		ConcurrentHashMap<String, Job> jobsForInstance = _instanceJobMap.get(instanceIp);
		synchronized(jobsForInstance) {
			jobsForInstance.remove(job.getJobId());
        }
    }
    

    private void setDesiredCapacity(int capacity) {
        SetDesiredCapacityRequest desiredCapacity = new SetDesiredCapacityRequest()
                    .withAutoScalingGroupName(AUTOSCALING_GROUP_NAME)
                    .withDesiredCapacity(capacity);
        _amazonAutoScalingClient.setDesiredCapacity(desiredCapacity);
    }
    

    public boolean allInstancesAreFull() {
        if (_instanceJobMap.size() == 0)
            return true;
            
        for(Map.Entry<String, ConcurrentHashMap<String, Job>> entry : _instanceJobMap.entrySet()) {
            ConcurrentHashMap<String, Job> jobsForInstance = entry.getValue();
            if(jobsForInstance.size() < THREAD_COUNT_ON_INSTANCES)
                return false;
        }
        return true;

    }

    public boolean shouldScaleDown() {
        int emptyInstances = 0;

        for(Map.Entry<String, ConcurrentHashMap<String, Job>> entry: _instanceJobMap.entrySet()) {
            int jobsRunning = entry.getValue().size();
            if(jobsRunning == 0)
                emptyInstances++;
        }

        return emptyInstances > 1;

    }

    public boolean shouldScaleUp() {
        int availableThreads = 0;
        for(Map.Entry<String, ConcurrentHashMap<String, Job>> entry: _instanceJobMap.entrySet()) {
            int jobsRunning = entry.getValue().size();
            availableThreads += THREAD_COUNT_ON_INSTANCES - jobsRunning;
        }

        return availableThreads == 0;

    }
    

    

    public String red(String text) { return "\u001B[31m" + text + "\u001B[0m"; }
    public String green(String text) { return "\u001B[32m" + text + "\u001B[0m"; }
	public String yellow(String text) { return "\u001B[33m" + text + "\u001B[0m"; }
	public String cyan(String text) { return "\u001B[36m" + text + "\u001B[0m"; }
	public String italic(String text) { return "\u001B[03m" + text + "\u001B[0m"; }

    
}

