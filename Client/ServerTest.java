
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.Semaphore;
import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;


public class ServerTest {
	
    public static void main(String[] args) throws InterruptedException {
		
		
    	String file;
		int sc, sr, wc, wr;
		int coff, roff;
		
		int height, width;
		
		String protocol = "http";
		String host = "localhost:8001/r.html";
// 		String host = "34.251.67.226:8001/r.html";
// 		String host = "54.194.8.65:8001/r.html";
		
		file = "test05.txt";
		sc = sr = wc = wr = 100;
		coff = roff = 0;
		
		width = height = 1000;
		
		// auxiliar class
		ClientRequestHandler client = new ClientRequestHandler(protocol, host);
		
		
		// loop similar requets -> answers 7 requets
		for (int i = 0; i < 10; i=i+1) {
// 			client.makeRequest("test04.txt", 400, 300, 400, 300, i, i);
//			client.makeRequest("test04.txt", 8000, 6000, 1600, 1200, i, 400+i);	
 			client.makeRequest("test04.txt", 20000, 15000, 3250, 2500, 5000+i, 6500+i);
//          client.makeRequest("test05.txt", 20000, 15000, 3250, 2500, 5000+i, 6500+i);
		}
		
// 		Thread.sleep(2000);
// 		for (int i = 1; i < 10; i=i+1) {
// 			client.makeRequest(sem, "test04.txt", 400, 300, 400, 300, i, i);
// 		}
		

	}
	
}


