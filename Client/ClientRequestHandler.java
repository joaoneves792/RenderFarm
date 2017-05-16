 
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import java.util.concurrent.Semaphore;


public class ClientRequestHandler {
	
	String _protocol = "http";
	String _host = "localhost:8000/r.html";

	private String _file;
	private int _sc, _sr, _wc, _wr;
	private int _coff, _roff;
	
	private int _height, _width;
	
	
	public ClientRequestHandler() {
		_protocol = "http";
		_host = "localhost:8000/r.html";
		
		_file = "test01.txt";
		_sc = _sr = _wc = _wr = 100;
		_coff = _roff = 0;
		
		_height = 1080;
		_width = 1920;
	}
	
	public ClientRequestHandler(String protocol, String host) {
		this();
		_protocol = protocol;
		_host = host;
	}
	
	
	public void makeRequest(Semaphore sem, String f, int sc, int sr, int wc, int wr, int coff, int roff) {
		
		new Thread( new Runnable() {
			public void run() {
				try {
					sem.acquire();
					
					try {
						URL url = new URL(_protocol + "://" + _host
											+ "?" + getRequest(f, sc, sr, wc, wr, coff, roff));
						
						String response = "";
// 						System.out.print("<- ");
						
						int size = 0;
						BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
						while ((response = br.readLine()) != null) {
	// 		 				System.out.println(response);
							size += response.length();
						}
						
						System.out.println("\n-> " + url);
						System.out.println("<- image returned (" + size + ")");
						
					} catch(Exception e) {
						System.out.println(e.getMessage());
					}
					
					sem.release();
					
				} catch(InterruptedException e) {
					System.out.println(e.getMessage());
				}
			}
		}).start();
	}
	
	public String getRequest() {
		return "f=" + _file
			+ "&sc=" + _sc + "&sr=" + _sr
			+ "&wc=" + _wc + "&wr=" + _wr
			+ "&coff=" + _coff + "&roff=" + _roff;
	}
	
	public String getRequest(String f, int sc, int sr, int wc, int wr, int coff, int roff) {
		return "f=" + f
			+ "&sc=" + sc + "&sr=" + sr
			+ "&wc=" + wc + "&wr=" + wr
			+ "&coff=" + coff + "&roff=" + roff;
	}
	
}



