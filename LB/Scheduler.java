import java.util.*;
import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClientBuilder;
import com.amazonaws.services.autoscaling.model.AutoScalingInstanceDetails;
import com.amazonaws.services.autoscaling.model.ScheduledUpdateGroupAction;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;


public class Scheduler {
    static HashMap<String, HashMap<String, Job>> instanceJobMap = new HashMap<String, HashMap<String, Job>>();
    private static final String REGION = "eu-west-1";
    private static final String AUTOSCALING_GROUP_NAME = " RENDERFARM_ASG";
    private static AWSCredentials _credentials;
    private static final int THREAD_COUNT_ON_INSTANCES = 2;

    private static MetricsManager metricsManager;

    private static void loadCredentials() {
        try {
            _credentials = new ProfileCredentialsProvider().getCredentials();
        } catch (Exception e) {
            throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file. " +
                            "Please make sure that your credentials file is at the correct " +
                            "location (~/.aws/credentials), and is in valid format.",
                    e);
        }
    }

    private static List<String> getGroupIPs() {
        List<String> IPs = new LinkedList<>();

        /*Get the instance IDs of the instances in our autoscaling group*/
        AmazonAutoScaling amazonAutoScalingClient = AmazonAutoScalingClientBuilder
                .standard()
                .withRegion(REGION)
                .withCredentials(new AWSStaticCredentialsProvider(_credentials))
                .build();

        List<AutoScalingInstanceDetails> details = amazonAutoScalingClient
                .describeAutoScalingInstances()
                .getAutoScalingInstances();

        List<String> groupInstances = new LinkedList<>();
        for(AutoScalingInstanceDetails detail : details){
            if(detail.getAutoScalingGroupName().equals(AUTOSCALING_GROUP_NAME)) {
                groupInstances.add(detail.getInstanceId());
            }
        }

        /*Now get their public IPs*/
        AmazonEC2 ec2 = AmazonEC2ClientBuilder
                .standard()
                .withRegion(REGION)
                .withCredentials(new AWSStaticCredentialsProvider(_credentials))
                .build();
        DescribeInstancesRequest describeRequest = new DescribeInstancesRequest();
        describeRequest.setInstanceIds(groupInstances);
        DescribeInstancesResult instances = ec2.describeInstances(describeRequest);
        List<Reservation> reservations = instances.getReservations();
        for(Reservation r : reservations){
            for(Instance i : r.getInstances()){
                IPs.add(i.getPublicIpAddress());
            }
        }
        return IPs;
    }

    public static void init() {
        loadCredentials();

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
        return jobId;
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

        // Assuming max two jobs pr server, return server ip with less than two jobs.
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
        Job job1 = new Job("file1", 1000, 1000, 200, 200, 0,0);
        Job job2 = new Job("file1", 1000, 1000, 200, 200, 0,0);
        Job job3 = new Job("file1", 1000, 1000, 200, 200, 0,0);
        Job job4= new Job("file1", 1000, 1000, 200, 200, 0,0);

        String ipForJob1 = Scheduler.getIpForJob(job1);
        Scheduler.scheduleJob(job1, ipForJob1);

        String ipForJob2 = Scheduler.getIpForJob(job2);
        Scheduler.scheduleJob(job2, ipForJob2);

        String ipForJob3 = Scheduler.getIpForJob(job3);
        Scheduler.scheduleJob(job3, ipForJob3);

        String ipForJob4 = Scheduler.getIpForJob(job4);
        Scheduler.scheduleJob(job4, ipForJob4);


        System.out.println(Scheduler.allInstancesAreFull());
    }
}