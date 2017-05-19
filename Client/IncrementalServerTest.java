
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.Semaphore;
import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;


public class IncrementalServerTest {
	
    public static void main(String[] args) throws InterruptedException {
		
    	String file;
		int sc, sr, wc, wr;
		int coff, roff;
		
		int height, width;
		
		String protocol = "http";
		String host = "localhost:8001/r.html";
// 		String host = "52.31.49.163:8001/r.html";
		
		file = "test05.txt";
		sc = sr = wc = wr = 100;
		coff = roff = 0;
		
		width = height = 1000;
		
		// auxiliar class
		SingleThreadClientRequestHandler client = new SingleThreadClientRequestHandler(protocol, host);
		
		
		Timer timer;
		
		for (int i = 0; i < 100; i=i+50) {
			timer = Timer.start();
			
			client.makeRequest("test04.txt", 400+i, 300+i, 400+i, 300+i, 0, i);
			
			System.out.println("\n" + timer.time(TimeUnit.SECONDS) + " seconds.");			
		}
		
	}
	
}


