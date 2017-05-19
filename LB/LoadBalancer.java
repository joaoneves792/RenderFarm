import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import static java.net.HttpURLConnection.HTTP_OK;


public class LoadBalancer {
	
    public static final String RENDER_RESOURCE = "/r.html";
    public static final String TEST_RESOURCE = "/test";
    public static final int LB_PORT = 8001;
    public static final int WS_PORT = 8000;

    private static Scheduler _scheduler;

    public static void main(String[] args) throws Exception {
		
        HttpServer server = HttpServer.create(new InetSocketAddress(LB_PORT), 0);
        server.createContext(RENDER_RESOURCE, new RenderHandler());
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool()); // creates a default executor
        _scheduler = new Scheduler();
        _scheduler.init();
        server.start();
        System.out.println("\nLoad Balancer" + green(" ready") + "!");
    }
    

    private static Job parseArguments(String query)throws NumberFormatException {
		
        Map<String, String> arguments = new HashMap<>();
        for(String arg : query.split("&")) {
            String pair[] = arg.split("=");
            if (pair.length > 1) {
                arguments.put(pair[0], pair[1]);
            }
        }
        
        if (arguments.containsKey("sc") && arguments.containsKey("sr") &&
                arguments.containsKey("wc") && arguments.containsKey("wr") &&
                arguments.containsKey("coff") && arguments.containsKey("roff") &&
                arguments.containsKey("f")) {
            try {
                int sc = Integer.parseInt(arguments.get("sc"));
                int sr = Integer.parseInt(arguments.get("sr"));
                int wc = Integer.parseInt(arguments.get("wc"));
                int wr = Integer.parseInt(arguments.get("wr"));
                int coff = Integer.parseInt(arguments.get("coff"));
                int roff = Integer.parseInt(arguments.get("roff"));
                String fileName = arguments.get("f");

                return new Job(fileName, wc, wr, sc, sr, coff, roff);
                
            } catch (NumberFormatException e){
                return null;
            }
            
        } else {
            return null;
        }
    }
    
    
    private static boolean isDead(String ip) {
        final int RETRY_INTERVAL = 1000;
        final int TIMEOUT = 2000;
        HttpURLConnection connection = null;

        for(int retries=3; retries > 0; retries--) {
            try {
                connection = (HttpURLConnection) new URL("http", ip, WS_PORT, TEST_RESOURCE).openConnection();
                connection.setConnectTimeout(TIMEOUT);
                
                if(connection.getResponseCode() == HTTP_OK) {
                    connection.disconnect();
                    return false;
                }

                System.out.println("\nServer @ " + ip + " failed to respond! Retrying...");
                System.out.println("\n" + ip + italic(red(" failed to respond!") + " Retrying..."));
                Thread.sleep(RETRY_INTERVAL);
                
            } catch (IOException e) {
                //just retry
                if(null != connection)
                    connection.disconnect();
                    
                try {
//                     System.out.println("\nServer @ " + ip + " failed to respond! Retrying...");
                    Thread.sleep(RETRY_INTERVAL);
                    
                }catch (InterruptedException ex){
                    if(null != connection)
                        connection.disconnect();
                    return false;
                }
                
            } catch (InterruptedException e) {
                if(null != connection)
                    connection.disconnect();
                    
                return false;
            }
        }
        
        System.out.println("\n" + ip + italic(red(" is dead!")));
        if(null != connection)
            connection.disconnect();

        _scheduler.instanceFailure(ip);
        return true; //If all the retries failed to produce a 200 OK then the server must be dead
    }
    

    static class RenderHandler implements HttpHandler {
		
		@Override
		public void handle(HttpExchange t) throws IOException {
			
			Job job = parseArguments(t.getRequestURI().getQuery());
			if(null == job){
				String response = "\nInvalid request!";
				System.out.println(response);
				t.sendResponseHeaders(200, response.length());
				OutputStream os = t.getResponseBody();
				os.write(response.getBytes());
				os.close();
				return;
			}
			
			
			// Create a new job. Add to scheduler
			// The IP of the host to which we want to redirect the request
			String ipForJob = null;
			do {
				try {
					ipForJob = _scheduler.scheduleJob(job);
					
					if (ipForJob.equals(_scheduler.QUEUED)) {
						ipForJob = _scheduler.waitForBootAndSchedule();
					}
				} catch(InterruptedException e) {
					// print error and retry
					System.out.println(red("failed to schedule job: ") + e.getMessage());
				}
				
			} while (isDead(ipForJob));
			
			
			String charset = java.nio.charset.StandardCharsets.UTF_8.name();
			String query = t.getRequestURI().getQuery();
			
			HttpURLConnection connection = (HttpURLConnection) new URL("http", ipForJob, WS_PORT, RENDER_RESOURCE+"?"+query).openConnection();
			connection.setRequestProperty("Accept-Charset", charset);
			
			try {
				job.start(ipForJob);
// 				String jobLog = green("\n[Sent]")
// 								+ italic("\n instance: ") + ipForJob
// 								+ italic("\n job: ") + job.toString()
// 								+ italic("\n estimated job cost: ") + job.getEstimatedCost();
// 				System.out.println(jobLog);
				
				InputStream response = connection.getInputStream();
				ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
				
				job.stop(ipForJob);
				int data;
				while ((data = response.read()) > -1) {
					outputStream.write(data);
				}
				
				t.sendResponseHeaders(200, outputStream.size());
				OutputStream os = t.getResponseBody();
				outputStream.writeTo(os);
				os.close();
				
			} catch(IOException e) {
				e.printStackTrace();
				String response = String.valueOf(red("EXCEPTION: ") + connection.getResponseCode() + "\n");
				System.out.println(response);
				t.sendResponseHeaders(200, response.length());
				OutputStream os = t.getResponseBody();
				os.write(response.getBytes());
				os.close();
			}

			// Job finished. Update scheduler.
			_scheduler.finishJob(job, ipForJob);

		}
	}
	
    public static String red(String text) { return "\u001B[31m" + text + "\u001B[0m"; }
    public static String green(String text) { return "\u001B[32m" + text + "\u001B[0m"; }
	public static String yellow(String text) { return "\u001B[33m" + text + "\u001B[0m"; }
	public static String cyan(String text) { return "\u001B[36m" + text + "\u001B[0m"; }
	public static String italic(String text) { return "\u001B[03m" + text + "\u001B[0m"; }

}


