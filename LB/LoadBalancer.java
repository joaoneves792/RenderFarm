import java.io.*;
import java.net.*;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClientBuilder;
import com.amazonaws.services.autoscaling.model.AutoScalingInstanceDetails;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;


public class LoadBalancer {
    private static final String RENDER_RESOURCE = "/r.html";
    private static final int LB_PORT = 8001;
    private static final int WS_PORT = 8000;
    private static final String REGION = "eu-west-1";
    private static final String AUTOSCALING_GROUP_NAME = " RENDERFARM_ASG";

    private static int _roundRobin = 0;

    private static AWSCredentials _credentials;
    private static List<String> _IPs;

    private static AWSCredentials loadCredentials(){
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
        AmazonAutoScaling amazonAutoScalingClient = AmazonAutoScalingClientBuilder.standard().withRegion(REGION).withCredentials(new AWSStaticCredentialsProvider(_credentials)).build();
        List<AutoScalingInstanceDetails> details = amazonAutoScalingClient.describeAutoScalingInstances().getAutoScalingInstances();
        List<String> groupInstances = new LinkedList<>();
        for(AutoScalingInstanceDetails detail : details){
            if(detail.getAutoScalingGroupName().equals(AUTOSCALING_GROUP_NAME)) {
                groupInstances.add(detail.getInstanceId());
            }
        }

        /*Now get their public IPs*/
        AmazonEC2 ec2 = AmazonEC2ClientBuilder.standard().withRegion(REGION).withCredentials(new AWSStaticCredentialsProvider(_credentials)).build();
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

    private static synchronized int getNextRoundRobin(){
        return (_roundRobin = (_roundRobin + 1)%_IPs.size());
    }

    public static void main(String[] args) throws Exception {

        _credentials = loadCredentials();
        _IPs = getGroupIPs();


        System.out.println("Available hosts:");
        for(String ip : _IPs){
            System.out.println(ip);
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(LB_PORT), 0);
        server.createContext(RENDER_RESOURCE, new RenderHandler());
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool()); // creates a default executor
        server.start();
    }


    static class RenderHandler implements HttpHandler {
		
        @Override
        public void handle(HttpExchange t) throws IOException{

            String host; //The IP of the host to which we want to redirect the request
            host = _IPs.get(getNextRoundRobin());

            String charset = java.nio.charset.StandardCharsets.UTF_8.name();
            String query = t.getRequestURI().getQuery();
            HttpURLConnection connection = (HttpURLConnection)new URL("http", host, WS_PORT, RENDER_RESOURCE+"?"+query).openConnection();
            connection.setRequestProperty("Accept-Charset", charset);
		    try{

                InputStream response = connection.getInputStream();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                int data;
                while ( (data = response.read()) > -1){
                    outputStream.write(data);
                }

                t.sendResponseHeaders(200, outputStream.size());
                OutputStream os = t.getResponseBody();
                outputStream.writeTo(os);
                os.close();

            } catch(IOException e) {
		        e.printStackTrace();
                String response = String.valueOf(connection.getResponseCode());
                System.out.println(response);
                t.sendResponseHeaders(200, response.length());
                OutputStream os = t.getResponseBody();
                os.write(response.getBytes());
                os.close();
            }
        }
    }
    
    
}


