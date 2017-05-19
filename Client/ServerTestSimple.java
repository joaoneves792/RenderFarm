
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.Semaphore;


public class ServerTestSimple {
	
    public static void main(String[] args) {
		
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
		
		// simple, single request
// 		client.makeRequest("test01.txt", 400, 300, 400, 300, 0, 0);
		client.makeRequest("test04.txt", 8000, 6000, 1600, 1200, 0, 400);
// 		client.makeRequest("test05.txt", 20000, 15000, 3250, 2500, 5000, 6500);
		

	}
	
}


