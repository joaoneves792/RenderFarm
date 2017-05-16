
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.Semaphore;


public class ServerTestFull {
	
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
		
		// for every file, render a bigmak picture and then fractions of it
		for (int i = 1; i <= 5; i++) {
			
			// generate the full sized scenne for a given file
			file = "test0" + i + ".txt";
			client.makeRequest(sem, file, width, height, width, height, coff, roff);
			
			// break the scenne into smaller chunks
			for(coff = -500 ; coff < width/2 ; coff += 100) {
				for(roff = -500 ; roff < height/2 ; roff += 100) {
					client.makeRequest(sem, file, width, height, sc, sr, coff, roff);
				}
			}
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


