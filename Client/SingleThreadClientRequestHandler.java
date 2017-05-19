 
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import java.util.concurrent.Semaphore;


public class SingleThreadClientRequestHandler {
	
	String _protocol = "http";
	String _host = "localhost:8000/r.html";

	private String _file;
	private int _sc, _sr, _wc, _wr;
	private int _coff, _roff;
	
	private int _height, _width;
	
	
	public SingleThreadClientRequestHandler() {
		_protocol = "http";
		_host = "localhost:8000/r.html";
		
		_file = "test01.txt";
		_sc = _sr = _wc = _wr = 100;
		_coff = _roff = 0;
		
		_height = 1080;
		_width = 1920;
	}
	
	public SingleThreadClientRequestHandler(String protocol, String host) {
		this();
		_protocol = protocol;
		_host = host;
	}
	
	
	public void makeRequest(String f, int sc, int sr, int wc, int wr, int coff, int roff) {
		
		try {
			URL url = new URL(_protocol + "://" + _host
								+ "?" + getRequest(f, sc, sr, wc, wr, coff, roff));
			System.out.println("\n-> " + url);
			
			String response = "";
			
			int size = 0;
			BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
			while ((response = br.readLine()) != null) {
				size += response.length();
			}
			
			System.out.println("<-- image returned (" + size + ")");
			
		} catch(Exception e) {
			System.out.println(e.getMessage());
		}
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



