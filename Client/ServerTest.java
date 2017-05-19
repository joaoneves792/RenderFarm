
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
		
		final Semaphore sem = new Semaphore(99999, true);
		DateFormat dtf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss:L");

		
    	String file;
		int sc, sr, wc, wr;
		int coff, roff;
		
		int height, width;
		
		String protocol = "http";
		String host = "localhost:8001/r.html";
		
		file = "test05.txt";
		sc = sr = wc = wr = 100;
		coff = roff = 0;
		
		width = height = 1000;
		
		// auxiliar class
		ClientRequestHandler client = new ClientRequestHandler(protocol, host);
		
		Timer timer = Timer.start();
		
		
		// loop similar requets -> answers 7 requets
		for (int i = 1; i < 10; i=i+1) {
// 			client.makeRequest(sem, "test04.txt", 400, 300, 400, 300, i, i);
// 			client.makeRequest(sem, "test04.txt", 8000, 6000, 1600, 1200, i, 6400+i);	
			client.makeRequest(sem, "test04.txt", 20000, 15000, 3250, 2500, 5000+i, 6500+i);
		}
		
// 		Thread.sleep(2000);
		for (int i = 1; i < 10; i=i+1) {
			client.makeRequest(sem, "test04.txt", 400, 300, 400, 300, i, i);
		}
		
		
		try {
			// FIXME not working use "time make run instead"
			// print elapsed time
			sem.acquire();
			System.out.println("\n" + timer.time(TimeUnit.MILLISECONDS) + " miliseconds.");
			System.out.println("this time means nothing. use 'time make run' instead.");
			
		} catch(InterruptedException e) {
			System.out.println(e.getMessage());
		}
	}
	
}


