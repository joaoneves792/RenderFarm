
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;


public class ServerTest {
	
    public static void main(String[] args) {
		
    	String file;
		int sc, sr, wc, wr;
		int coff, roff;
		
		int height, width;
		
		String protocol = "http";
		String host = "localhost:8000/r.html";
		
		file = "test05.txt";
		sc = sr = wc = wr = 100;
		coff = roff = 0;
		
		width = height = 1000;
		
		// auxiliar class
		ClientRequestHandler client = new ClientRequestHandler(protocol, host);
		
		for (int i = 1; i <= 5; i++) {
			
			// generate the full sized scenne for a given file
			file = "test0" + i + ".txt";
			client.makeRequest(file, width, height, width, height, coff, roff);
			
			// break the scenne into smaller chunks
			for(coff = -500 ; coff < width/2 ; coff += 100) {
				for(roff = -500 ; roff < height/2 ; roff += 100) {
					client.makeRequest(file, width, height, sc, sr, coff, roff);
				}
			}
		}
	
	}
	
}


