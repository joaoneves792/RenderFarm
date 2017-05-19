
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.Semaphore;


public class ServerTestSimple {
	
    public static void main(String[] args) {
   		final Semaphore sem = new Semaphore(99999, true);
		
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
		
		// simple, single request
// 		client.makeRequest(sem, "test01.txt", 400, 300, 400, 300, 0, 0);
		client.makeRequest(sem, "test05.txt", 20000, 15000, 3250, 2500, 5000, 6500);
		
		
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


