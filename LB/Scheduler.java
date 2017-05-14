import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);


    private static HashMap<String, HashMap<String, Job>> instanceJobMap = new HashMap<String, HashMap<String, Job>>();
    private static MetricsManager metricsManager;
    private static HashMap<String, Long> latestJobForInstance;
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

        metricsManager = new MetricsManager();
        metricsManager.init();
    }


    public static String scheduleJob(Job newJob, String instanceIp) {
        String jobId = newJob.getJobId();
        newJob.start();

        if (instanceJobMap.containsKey(instanceIp)) {
            instanceJobMap.get(instanceIp).put(jobId, newJob);
        } else {
            HashMap<String, Job> instanceJobs = new HashMap<String, Job>();
            instanceJobs.put(jobId, newJob);
            instanceJobMap.put(instanceIp, instanceJobs);
        }

        return jobId;
    }

    private static void pollIPsFromAGS() {
        List<String> ipsInAGS = Scheduler.getGroupIPs();

        // Add ip's that are not in the group.
        // This is the case where we have added a new instance.
        for(String ip : ipsInAGS) {
            if (!instanceJobMap.containsKey(ip) && ip != null) {
                instanceJobMap.put(ip, new HashMap<String, Job> ());
                System.out.println("Added: " + ip);
            }
        }

        // Check if some of the IPs in the instanceJobMap does not exist
        // in the ASG. (This is the case where we have removed an instance.
        for(String ip: instanceJobMap.keySet()) {
            if (!ipsInAGS.contains(ip)) {
                instanceJobMap.remove(ip);
                System.out.println("Removed: " + ip);
            }
        }

    }

    private static void setDesiredCapacity(int capacity) {
        SetDesiredCapacityRequest desiredCapacity = new SetDesiredCapacityRequest()
                    .withAutoScalingGroupName(AUTOSCALING_GROUP_NAME)
                    .withDesiredCapacity(capacity);
        _amazonAutoScalingClient.setDesiredCapacity(desiredCapacity);

    }

    public static void finishJob(Job job, String instanceIp) {
        job.stop();
        HashMap<String, Job> jobsForInstance = instanceJobMap.get(instanceIp);
        jobsForInstance.remove(job.getJobId());
    }

    public static boolean allInstancesAreFull() {
        if (instanceJobMap.size() == 0)
            return true;

        for(Map.Entry<String, HashMap<String, Job>> entry : instanceJobMap.entrySet()) {
            HashMap<String, Job> jobsForInstance = entry.getValue();
            if (jobsForInstance.size() < THREAD_COUNT_ON_INSTANCES)
                return false;
        }
        return true;
    }

    public static String getIpForJob(Job job) {
        // Refresh instances before we select server.
        Scheduler.pollIPsFromAGS();

        //Get the estimated cost for running this job
        double cost = job.estimateCost(metricsManager);
        System.out.println("Estimated cost: " + cost);

        // Assuming max two jobs pr server, return server ip with less than two jobs.
        for(Map.Entry<String, HashMap<String, Job>> ipJobsKeyPair : instanceJobMap.entrySet()) {
            String ip = ipJobsKeyPair.getKey();
            HashMap<String, Job> jobsForInstance = ipJobsKeyPair.getValue();

            if (jobsForInstance.size() < THREAD_COUNT_ON_INSTANCES) {
                // TODO: We have to make sure that the instance of the IP has not been terminated
                return ip;
            }
        }

        // If we come here it means that all the server where fully loaded
        // Now we have to select by job size or according two time.

        return "";
    }

    public static void main(String[] args) {
        Scheduler.init();

    }
}