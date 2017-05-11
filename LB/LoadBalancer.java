import java.io.*;
import java.net.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;


public class LoadBalancer {
    private static final String RENDER_RESOURCE = "/r.html";
    private static final int LB_PORT = 8001;
    private static final int WS_PORT = 8000;

    public static void main(String[] args) throws Exception {

        HttpServer server = HttpServer.create(new InetSocketAddress(LB_PORT), 0);
        server.createContext(RENDER_RESOURCE, new RenderHandler());
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(5)); // creates a default executor
        Scheduler.init();
        server.start();
    }


    static class RenderHandler implements HttpHandler {
		
        @Override
        public void handle(HttpExchange t) throws IOException {

            //The IP of the host to which we want to redirect the request
            String host = Scheduler.getIpForJob();

            String charset = java.nio.charset.StandardCharsets.UTF_8.name();
            String query = t.getRequestURI().getQuery();

            Job job = new Job("file1", 1000, 1000, 300, 300, 0, 0);
            String ipForJob = Scheduler.getIpForJob();
            Scheduler.scheduleJob(job, ipForJob);

            HttpURLConnection connection = (HttpURLConnection) new URL("http", host, WS_PORT, RENDER_RESOURCE+"?"+query).openConnection();
            connection.setRequestProperty("Accept-Charset", charset);
		    try {
		        System.out.println("Sent job to: " + ipForJob);
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

            Scheduler.finishJob(job, ipForJob);

        }
    }
    
    
}


