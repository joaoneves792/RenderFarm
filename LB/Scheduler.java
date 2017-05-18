
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

import java.util.concurrent.*;

import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClientBuilder;
import com.amazonaws.services.autoscaling.model.*;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.services.ec2.model.Instance;


public class Scheduler {

    public static final String QUEUED = "QUEUED";
	private static final double QUEUE_JOB_THRESHOLD = 100000; // FIXME

    private static final String REGION = "eu-west-1";
    private final String AUTOSCALING_GROUP_NAME = "RENDERFARM_ASG";
    private static final int THREAD_COUNT_ON_INSTANCES = 2;

    private static final int AUTOSCALING_GROUP_MIN_INSTANCES = 2;
    private static final double NEW_INSTANCE_THRESHOLD = 1.5; // FIXME
    
    private static final double ESTIMATED_COST_THRESHOLD = 30000; // FIXME
    private static final int INSTANCES_IP_CHECK_INTERVAL = 30; //In Seconds
    private static final int REMOVE_UNUSED_INSTANCES_INTERVAL = 120;//In Seconds
    private static final ScheduledExecutorService _scheduledExecutor = Executors.newScheduledThreadPool(2);

    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Job>> _instanceJobMap = new ConcurrentHashMap<String, ConcurrentHashMap<String, Job>>();
    private final LinkedBlockingQueue<String> _freshInstances = new LinkedBlockingQueue<String>();
    private final LinkedBlockingQueue<Job> _queuedJobs = new LinkedBlockingQueue<Job>();
    
    private MetricsManager _metricsManager;
    private final List<String> _pendingBoot = new LinkedList<>();
    private final List<String> _idleInstances = new LinkedList<>();
    private final ConcurrentLinkedQueue<String> _terminatingInstances = new ConcurrentLinkedQueue<>();
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
            if(detail.getAutoScalingGroupName().trim().equals(AUTOSCALING_GROUP_NAME)) {
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
        _scheduledExecutor.scheduleAtFixedRate(new RemoveUnusedInstances() ,REMOVE_UNUSED_INSTANCES_INTERVAL, REMOVE_UNUSED_INSTANCES_INTERVAL, TimeUnit.SECONDS);

		setDesiredCapacity(2);
    }

    private class GetGroupIps implements Runnable{
        private int RETRY_INTERVAL = 30000;//30sec
        public void run(){
            final List<String> ipsInAGS = getGroupIPs();

            //Remove terminating instances that are no longer on the ASG
            Iterator<String> iter = _terminatingInstances.iterator();
            while(iter.hasNext()){
                String currentIP = iter.next();
                if(!ipsInAGS.contains(currentIP)){
                    iter.remove();
                }
            }

            // Add ip's that are not in the map.
            // This is the case where we have added a new instance.
            for(String ipInAgs : ipsInAGS) {
                final String ip = ipInAgs;//Hack
                if (ip != null && !_instanceJobMap.containsKey(ip) &&
                        !_terminatingInstances.contains(ip)) { //If instance is not in our map and is not marked for termination

                    //Then check if there is a thread already looking into it
                    boolean alreadyWaiting = false;
                    synchronized (_pendingBoot) {
                        for (String pendingIp : _pendingBoot)
                            if (pendingIp.equals(ip))
                                alreadyWaiting = true; //There is a thread already looking into it
                        _pendingBoot.add(ip);
                    }
                    if(!alreadyWaiting) {
                        pollUntilAlive(ip);
                    }
                }
            }

        }

        private void pollUntilAlive(String ip) {
            Executor ex = Executors.newSingleThreadExecutor();
            ex.execute(new Runnable() { //Create a new thread to add the instance once it responds to http
                @Override
                public void run() {
                    for (int retries = 6; retries > 0; retries--) {
                        try {
                            HttpURLConnection connection = (HttpURLConnection) new URL("http", ip,
                                    LoadBalancer.WS_PORT, LoadBalancer.TEST_RESOURCE).openConnection();
                            connection.setConnectTimeout(RETRY_INTERVAL);
                            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                                connection.disconnect();
                                addInstance(ip);
                                synchronized (_pendingBoot) {
                                    _pendingBoot.remove(ip);
                                }
                                return;
                            }
                            throw new IOException("Bad http response");
                        } catch (IOException e) {
                            try {
                                Thread.sleep(RETRY_INTERVAL);
                                //If the connection fails try sleeping for RETRY_INTERVAL and then try again
                            } catch (InterruptedException ex) {
                                //empty
                            }
                        }
                    }
                    synchronized (_pendingBoot) {
                        _pendingBoot.remove(ip);
                    }
                    reboot(ip); //If the machine does not respond for retries retries reboot it
                }
            });
        }
    }

    private class RemoveUnusedInstances implements Runnable {
        @Override
        //This code runs periodically
        public void run(){
            //Synchronized on instanceJobMap so we dont run concurrently with the scheduling of requests
            //This is done to prevent jobs from being assigned to instances in the process of being terminated
            synchronized (_instanceJobMap) {

                //trim the list of instances to be terminated so we always have some available
                while(_instanceJobMap.size() - _idleInstances.size() < AUTOSCALING_GROUP_MIN_INSTANCES
                        && _idleInstances.size() > 0) {
                    _idleInstances.remove(0);
                }
                //Terminate idle instances that have been idle for the last period
                for (String ip : _idleInstances) {
                    _terminatingInstances.add(ip);
                    terminateInstance(ip);
                }

                _idleInstances.clear();//Start with an empty list of idle instances

                //Mark instances that have no jobs as idle (if they are not removed from the idle list for a whole
                //period then they will be terminated next time this code runs)
                for (Map.Entry<String, ConcurrentHashMap<String, Job>> entry : _instanceJobMap.entrySet()) {
                    String ip = entry.getKey();
                    ConcurrentHashMap<String, Job> jobMap = entry.getValue();
                    if (jobMap.size() == 0) {
                        _idleInstances.add(ip);
                    }
                }
            }
        }
    }

    private synchronized void addInstance(String ip){
		
		if (_queuedJobs.isEmpty()) {
			// if no pending jobs, add instance normally
			if(!_instanceJobMap.containsKey(ip)) {
				ConcurrentHashMap<String, Job> jobMap = new ConcurrentHashMap<String, Job>();
				_instanceJobMap.put(ip, jobMap);
			}
			System.out.println(cyan("\nAdded instance: ") + ip);
			
		} else {
			try {
				// trigger the threads waiting for a new instance
				_freshInstances.put(ip);
				
			} catch(InterruptedException e) {
				if(!_instanceJobMap.containsKey(ip)) {
					ConcurrentHashMap<String, Job> jobMap = new ConcurrentHashMap<String, Job>();
					_instanceJobMap.put(ip, jobMap);
				}
			}
			System.out.println(cyan("\nAdded instance " + italic("(jobs waiting)") + ": ") + ip);
			
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

    private void terminateInstance(String ip){
        removeInstance(ip);
        TerminateInstanceInAutoScalingGroupRequest terminateRequest = new TerminateInstanceInAutoScalingGroupRequest().withShouldDecrementDesiredCapacity(true);
        HashMap<String, String> instances = getGroupInstances();
        if(instances.containsKey(ip)) {
            System.out.println(red("Terminating idle: ") + ip);
            terminateRequest.setInstanceId(instances.get(ip));
            _amazonAutoScalingClient.terminateInstanceInAutoScalingGroup(terminateRequest);
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
		double costOnBestInstance = 999999;
		int totalRunningJobs = 0;
		System.out.println();
		for(Map.Entry<String, ConcurrentHashMap<String, Job>> ipJobsKeyPair : _instanceJobMap.entrySet()) {
			String ip = ipJobsKeyPair.getKey();
			ConcurrentHashMap<String, Job> jobsForInstance = ipJobsKeyPair.getValue();
			
			// return first instance found with free threads
// 			if (jobsForInstance.size() < THREAD_COUNT_ON_INSTANCES) {
			if (jobsForInstance.size() == THREAD_COUNT_ON_INSTANCES-1) { // ==1
				return ip;
				
			} else {
				if (jobsForInstance.size() >= THREAD_COUNT_ON_INSTANCES)
					System.out.println(red("At capacity: ") + italic(ip) + "\testimated cost on instance: " + italic(""+costOnInstance));
				
				costOnInstance = 0;
				totalRunningJobs += jobsForInstance.size();
				for(Job job : jobsForInstance.values()) {
					costOnInstance += job.getEstimatedCost();
				}
				
				if ((costOnInstance * jobsForInstance.size() + cost) < costOnBestInstance) {
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
		
		if (jobCountOnBestIP > 0) {
			System.out.println(yellow("All ") + _instanceJobMap.size() + yellow(" instances are full, total jobs running: ") + totalRunningJobs);
			
			// FIXME also take into consideration costOnBestInstance
			if (cost > QUEUE_JOB_THRESHOLD) {
				bestIP = QUEUED;
// 				totalRunningJobs += _queuedJobs.size();
			}
			
			int incrementTo = scaleUpTo(totalRunningJobs, _instanceJobMap.size());
			if (incrementTo > 0) {
				System.out.println(italic(cyan("Starting ") + (incrementTo - _instanceJobMap.size()) + cyan(" new instances...")) + "\tDesired total: " + incrementTo);
				setDesiredCapacity(incrementTo);
			}
		}
		
        return bestIP;
    }
    
    
    public int scaleUpTo(final int totalRunningJobs, final int totalInstances) {		
		int desired = -1;
		
// 		double ratio = ((totalRunningJobs + _queuedJobs.size()) / (totalInstances * THREAD_COUNT_ON_INSTANCES));
// // 		System.out.println("div-ratio " + ratio);
// 		
// 		if (ratio  >= NEW_INSTANCE_THRESHOLD) {
// 			desired = (int) Math.ceil(ratio * THREAD_COUNT_ON_INSTANCES);
// 		}

		desired = (int) Math.ceil((totalRunningJobs + _queuedJobs.size()) / THREAD_COUNT_ON_INSTANCES) + 1;
		
		// something failed,
		// 0 or lower will have the job just sent to an instance
		return desired;
    }
    

    public String scheduleJob(Job newJob) throws InterruptedException {
		synchronized (_instanceJobMap) {
            String ip = getIpForJob(newJob);
            
			// job will wait for a new instance to boot
            if (ip.equals(QUEUED)) {
				System.out.println(italic(yellow("-> queued: ")) + italic(newJob.toString()));
				_queuedJobs.put(newJob);
				
			} else {
// 				System.out.println(italic(yellow("NOT queued: ")) + newJob.toString());
				_instanceJobMap.get(ip).put(newJob.getJobId(), newJob);
				_idleInstances.remove(ip);
			}
			
            
            return ip;
        }
    }
    
    
    public String waitForBootAndSchedule() throws InterruptedException {
		String newIP = _freshInstances.take();
		ConcurrentHashMap<String, Job> jobMap;
		
		synchronized (_instanceJobMap) {
			if(!_instanceJobMap.containsKey(newIP)) {
				jobMap = new ConcurrentHashMap<String, Job>();
				
				if (!_queuedJobs.isEmpty()) {			
					Job job = _queuedJobs.take();
					System.out.println(italic(yellow("<- from queue: ")) + italic(job.toString()));
					jobMap.put(job.getJobId(), job);
				}
				
				synchronized (_instanceJobMap) {
					_instanceJobMap.put(newIP, jobMap);
				}
				
				// because it's a new instance try to fetch a second job
				if (_queuedJobs.size() > 0)
					_freshInstances.put(newIP);
				
			} else {
				jobMap = _instanceJobMap.get(newIP);
				
				if (!_queuedJobs.isEmpty()) {			
					Job job = _queuedJobs.take();
					System.out.println(italic(yellow("<- from queue: ")) + italic(job.toString()));
					jobMap.put(job.getJobId(), job);
				}
				
				synchronized (_instanceJobMap) {
					_instanceJobMap.put(newIP, jobMap);
				}
				
			}
			
			_idleInstances.remove(newIP);
		}
		
		return newIP;
    }
    
    
    
    public  void finishJob(Job job, String instanceIp) {
        job.stop();
		
		ConcurrentHashMap<String, Job> jobsForInstance = _instanceJobMap.get(instanceIp);
		synchronized(jobsForInstance) {
			jobsForInstance.remove(job.getJobId());
        }
    }
    

    private void setDesiredCapacity(int capacity) {
        try {
            SetDesiredCapacityRequest desiredCapacity = new SetDesiredCapacityRequest()
                    .withAutoScalingGroupName(AUTOSCALING_GROUP_NAME)
                    .withDesiredCapacity(capacity);
            _amazonAutoScalingClient.setDesiredCapacity(desiredCapacity);
            
        }catch (AmazonAutoScalingException e){
            System.out.println(red("AutoScaling exception: ") + e.getMessage() + "\n");
        }
    }

    public String red(String text) { return "\u001B[31m" + text + "\u001B[0m"; }
    public String green(String text) { return "\u001B[32m" + text + "\u001B[0m"; }
	public String yellow(String text) { return "\u001B[33m" + text + "\u001B[0m"; }
	public String cyan(String text) { return "\u001B[36m" + text + "\u001B[0m"; }
	public String italic(String text) { return "\u001B[03m" + text + "\u001B[0m"; }
	
	
	
	
	
// 	public int scaleUpTo(final int totalRunningJobs, final int totalInstances) {
// 		int desired = -1;
// 		
// 		Executors.newSingleThreadExecutor().execute(new Runnable() {
// 			@Override
// 			public void run() {
// 				start a new instance based on a ration between the exceeding and a threshold
// 				System.out.println(yellow("All instances full, total jobs running: ") + totalRunningJobs);
// 				
// 				double ratio = ((totalRunningJobs + _queuedJobs.size()) / totalInstances);
// 				System.out.println("div-ratio " + ratio);
// 				
// 				if (ratio  >= NEW_INSTANCE_THRESHOLD) {
// 					
// 					desired = (int) Math.ceil(ratio * THREAD_COUNT_ON_INSTANCES) - totalInstances;
// 					
// 					desired = ((int) (totalRunningJobs / THREAD_COUNT_ON_INSTANCES)) + totalInstances;
// 					
// 				}
// 			}
// 		});
// 		
// 		// something failed,
// 		// 0 or lower will have the job just sent to an instance
// 		return desired;
//     }

    
}

