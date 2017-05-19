import java.io.*;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import raytracer.RayTracer;

import BIT.samples.*;

public class WebServer {

    protected static final int THREAD_COUNT = 10;

    protected static AtomicInteger counter = new AtomicInteger(0);

    private static MetricsManager metricsManager;

    public static void main(String[] args) throws Exception {

        metricsManager = new MetricsManager();
        metricsManager.init();

    	File outputImgDir = new File("rendered-images");
		outputImgDir.mkdir();
		
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/test", new TestHandler());
        server.createContext("/r.html", new RenderHandler());
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(THREAD_COUNT)); // creates a default executor
        server.start();
    }

    static class TestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            //String response = "This was the query:" + t.getRequestURI().getQuery()+ "##";
            String response = "Server is up.";
            t.sendResponseHeaders(200, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }
    
    static class RenderHandler implements HttpHandler {
		
        @Override
        public void handle(HttpExchange t) throws IOException {
			
            String response;
            Map<String, String> arguments = new HashMap<>();
            String query = t.getRequestURI().getQuery();
            
            for(String arg : query.split("&")) {
                String pair[] = arg.split("=");
                if(pair.length > 1) {
                    arguments.put(pair[0], pair[1]);
                }
            }
			
			System.out.println(arguments);
			
			try {
                File inFile = new File("raytracer-master/" + arguments.get("f"));
                File outFile = new File("rendered-images/" + counter.getAndIncrement() + ".bmp");
                
                //We are not checking if these actually exist here, but we are validating them in the LB
                int sc = Integer.parseInt(arguments.get("sc"));
                int sr = Integer.parseInt(arguments.get("sr"));
                int wc = Integer.parseInt(arguments.get("wc"));
                int wr = Integer.parseInt(arguments.get("wr"));
                int coff = Integer.parseInt(arguments.get("coff"));
                int roff = Integer.parseInt(arguments.get("roff"));
                
                // pass the request arguments to the instrumentation class for the metrics
                MethodCallsCount.setRequestArguments(arguments.get("f"), sc, sr, wc, wr, coff, roff, metricsManager);
                
                RayTracer raytracer = new RayTracer(sc, sr, wc, wr, coff, roff);
                raytracer.readScene(inFile);
                raytracer.draw(outFile);

                FileInputStream fis = new FileInputStream(outFile);

                byte[] bytes = new byte[(int)outFile.length()];
                BufferedInputStream bufferedInputStream = new BufferedInputStream(fis);
                bufferedInputStream.read(bytes, 0, bytes.length);

                t.sendResponseHeaders(200, outFile.length());
                OutputStream os = t.getResponseBody();
                os.write(bytes);
                os.close();
                outFile.delete();

            } catch(InterruptedException e) {
                response = e.getMessage();
                System.out.println(response);
                t.sendResponseHeaders(200, response.length());
                OutputStream os = t.getResponseBody();
                os.write(response.getBytes());
                os.close();
            } catch(IOException e) {
                //ignore it
            }
        }
    }
    
    
}


