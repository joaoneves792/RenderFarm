import java.util.*;
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

    static HashMap<String, HashMap<String, Job>> instanceJobMap = new HashMap<String, HashMap<String, Job>>();
    private static AWSCredentials _credentials;
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
        try {
            _credentials = new ProfileCredentialsProvider().getCredentials();
        } catch (Exception e) {
            throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file. " +
                            "Please make sure that your credentials file is at the correct " +
                            "location (~/.aws/credentials), and is in valid format.",
                    e);
        }

        _amazonAutoScalingClient = AmazonAutoScalingClientBuilder
                .standard()
                .withRegion(REGION)
                .withCredentials(new AWSStaticCredentialsProvider(_credentials))
                .build();

        _ec2Client = AmazonEC2ClientBuilder
                .standard()
                .withRegion(REGION)
                .withCredentials(new AWSStaticCredentialsProvider(_credentials))
                .build();

        // Populate our instance map with the ips to the instances in our auto-scaling group.
        List<String> ips = getGroupIPs();
        for (String ip : ips) {
            instanceJobMap.put(ip, new HashMap<String, Job>());
        }

        metricsManager = new MetricsManager();
        metricsManager.init();

    }

    public static String scheduleJob(Job newJob, String instanceIp) {
        if (_credentials == null) {
            throw new Error("Please call init() before attempting to schedule jobs.");
        }

        String jobId = newJob.getJobId();
        newJob.start();

        if (instanceJobMap.containsKey(instanceIp)) {
            instanceJobMap.get(instanceIp).put(jobId, newJob);
        } else {
            HashMap<String, Job> instanceJobs = new HashMap<String, Job>();
            instanceJobs.put(jobId, newJob);
            instanceJobMap.put(instanceIp, instanceJobs);
        }
        latestJobForInstance.put(instanceIp, new Date().getTime());

        if (Scheduler.allInstancesAreFull()) {
            // Start new instance
        }

        return jobId;
    }

    static void launchNewInstance() {
        String id = "sg-c113d1b8";
        System.out.println(id);

        RunInstancesRequest runInstancesRequest = new RunInstancesRequest()
                .withImageId("ami-61d7de07")
                .withInstanceType("t2.micro")
                .withMinCount(1)
                .withMaxCount(1)
                .withKeyName("project-aws-new")
                .withSecurityGroupIds(id);

        RunInstancesResult result = _ec2Client.runInstances(runInstancesRequest);
        Instance newInstance = result.getReservation().getInstances().get(0);
        String instanceId = newInstance.getInstanceId();

        // Big problem: We have to wait until the instance state is "running" before we can add to asg.
        // I'm not sure what to do here:
        // Idea: Run this on a seperate thread, do some sort of "sleep for 60 seconds" then check state. If state is running we add to asg.

        AttachInstancesRequest request = new AttachInstancesRequest()
                .withInstanceIds(instanceId)
                .withAutoScalingGroupName(AUTOSCALING_GROUP_NAME);

        _amazonAutoScalingClient.attachInstances(request);

    }

    public static void finishJob(Job job, String instanceIp) {
        if (_credentials == null) {
            throw new Error("Please call init() before attempting to schedule jobs.");
        }

        job.stop();
        HashMap<String, Job> jobsForInstance = instanceJobMap.get(instanceIp);
        jobsForInstance.remove(job.getJobId());
    }

    public static boolean allInstancesAreFull() {
        if (_credentials == null) {
            throw new Error("Please call init() before attempting to schedule jobs.");
        }

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

        //Get the estimated cost for running this job
        double cost = job.estimateCost(metricsManager);
        System.out.println("Estimated cost: " + cost);

        // Assuming max THREAD_COUNT_ON_INSTANCES jobs pr server, return server ip with less than THREAD_COUNT_ON_INSTANCES jobs.
        for(Map.Entry<String, HashMap<String, Job>> ipJobsKeyPair : instanceJobMap.entrySet()) {
            String ip = ipJobsKeyPair.getKey();
            HashMap<String, Job> jobsForInstance = ipJobsKeyPair.getValue();

            if (jobsForInstance.size() < THREAD_COUNT_ON_INSTANCES) {
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