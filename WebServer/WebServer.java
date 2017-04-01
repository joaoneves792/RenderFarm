import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class WebServer {

    protected static final int THREAD_COUNT = 10;

    protected static AtomicInteger counter = new AtomicInteger(0);

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/test", new MyHandler());
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(THREAD_COUNT)); // creates a default executor
        server.start();
    }

    static class MyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            //String response = "This was the query:" + t.getRequestURI().getQuery()+ "##";
            String response = ""+WebServer.counter.getAndIncrement();
            t.sendResponseHeaders(200, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    private static class RenderExecutor implements Executor {
        public void execute (Runnable task){
            task.run();
        }

    }

}


