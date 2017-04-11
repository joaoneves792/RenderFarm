import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import raytracer.RayTracer;

public class WebServer {

    protected static final int THREAD_COUNT = 10;

    protected static AtomicInteger counter = new AtomicInteger(0);

    public static void main(String[] args) throws Exception {
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
            for(String arg : query.split("&")){
                String pair[] = arg.split("=");
                if (pair.length > 1){
                    arguments.put(pair[0], pair[1]);
                }
            }

            try {
                File inFile = new File("raytracer-master/"+ arguments.get("f"));
                File outFile = new File(counter.getAndIncrement() + ".bmp");

                //TODO we are not checking if these actually exist
                int sc = Integer.parseInt(arguments.get("sc"));
                int sr = Integer.parseInt(arguments.get("sr"));
                int wc = Integer.parseInt(arguments.get("wc"));
                int wr = Integer.parseInt(arguments.get("wr"));
                int coff = Integer.parseInt(arguments.get("coff"));
                int roff = Integer.parseInt(arguments.get("roff"));

                RayTracer raytracer = new RayTracer(sc, sr, wc, wr, coff, roff);
                raytracer.readScene(inFile);
                raytracer.draw(outFile);
            }catch (Exception e){
                response = e.getMessage();
                System.out.println(response);
            }
            try {
                response = "Rendering finished!";
                t.sendResponseHeaders(200, response.length());
                OutputStream os = t.getResponseBody();
                os.write(response.getBytes());
                os.close();
            }catch (IOException e){
                //ignore it
            }
        }
    }
}


