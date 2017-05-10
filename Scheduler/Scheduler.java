import java.util.*;
import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClientBuilder;
import com.amazonaws.services.autoscaling.model.AutoScalingInstanceDetails;
import com.amazonaws.services.autoscaling.model.PutLifecycleHookRequest;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;


public class Scheduler {
    static HashMap<String, HashMap<String, Job>> instanceJobMap = new HashMap<String, HashMap<String, Job>>();
    private static final String REGION = "eu-west-1";
    private static final String AUTOSCALING_GROUP_NAME = " RENDERFARM_ASG";
    private static AWSCredentials _credentials;

    private static AWSCredentials loadCredentials() {
        AWSCredentials credentials = null;
        try {
            credentials = new ProfileCredentialsProvider().getCredentials();
        } catch (Exception e) {
            throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file. " +
                            "Please make sure that your credentials file is at the correct " +
                            "location (~/.aws/credentials), and is in valid format.",
                    e);
        }
        return credentials;
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
        _credentials = loadCredentials();
        List<String> ips = getGroupIPs();
        for (String ip : ips) {
            instanceJobMap.put("ip", new HashMap<String, Job>());
        }

        AmazonAutoScaling amazonAutoScalingClient = AmazonAutoScalingClientBuilder
                .standard()
                .withRegion(REGION)
                .withCredentials(new AWSStaticCredentialsProvider(_credentials))
                .build();

        PutLifecycleHookRequest request = new PutLifecycleHookRequest()
                .withAutoScalingGroupName(AUTOSCALING_GROUP_NAME)
                .withLifecycleHookName("on-termination")
                .withLifecycleTransition("autoscaling:EC2_INSTANCE_TERMINATING");
        // TODO: Find out how to attach lambda function to remove instance.

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

    public static void finishJob(Job job, String instanceIp) {
        job.stop();
        HashMap<String, Job> jobsForInstance = instanceJobMap.get(instanceIp);
        jobsForInstance.remove(job.getJobId());
    }

    public static boolean shouldLaunchNewIstance() {
        for(Map.Entry<String, HashMap<String, Job>> entry : instanceJobMap.entrySet()) {
            String key = entry.getKey();
            HashMap<String, Job> jobsForInstance = entry.getValue();
            if (key.length() < 2)
                return false;
        }
        return true;
    }

    public static String getIpForJob(Job newJob) {
        // Assuming max two jobs pr server, return server ip with less than two jobs.
        for(Map.Entry<String, HashMap<String, Job>> ipJobsKeyPair : instanceJobMap.entrySet()) {
            String ip = ipJobsKeyPair.getKey();
            HashMap<String, Job> jobsForInstance = ipJobsKeyPair.getValue();

            if (jobsForInstance.size() < 2) {
                return ip;
            }
        }

        // If we come here it means that all the server where fully loaded
        // Now we have to select by job size or according two time.

        return "";
    }

    public static void main(String[] args) {
        Job job = new Job("file1", 1000,1000, 200, 200, 0,0);
        String jobId = job.getJobId();
        Scheduler.scheduleJob(job, "123");
        Scheduler.finishJob(job, "123");
        System.out.println(Scheduler.instanceJobMap.get("123").get(jobId));
    }
}