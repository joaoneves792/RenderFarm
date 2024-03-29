/* ICount.java
* Sample program using BIT -- counts the number of instructions executed.
*
* Copyright (c) 1997, The Regents of the University of Colorado. All
* Rights Reserved.
* 
* Permission to use and copy this software and its documentation for
* NON-COMMERCIAL purposes and without fee is hereby granted provided
* that this copyright notice appears in all copies. If you wish to use
* or wish to have others use BIT for commercial purposes please contact,
* Stephen V. O'Neil, Director, Office of Technology Transfer at the
* University of Colorado at Boulder (303) 492-5647.
*/

import BIT.highBIT.*;

import java.io.File;
import java.io.PrintStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;

import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;


public class MethodCallsCount {
	private static ThreadLocal<Long> counter;
	private static ThreadLocal<String> input_file;
	private static ThreadLocal<Integer> sc;
	private static ThreadLocal<Integer> sr;
	private static ThreadLocal<Integer> wc;
	private static ThreadLocal<Integer> wr;
	private static ThreadLocal<Integer> coff;
	private static ThreadLocal<Integer> roff;
	private static final DateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");


	private static MetricsManager metricsManager;

	/* main reads in all the files class files present in the input directory,
	* instruments them, and outputs them to the specified output directory.
	*/
	public static void main(String argv[]) {
		// create file for the storing of metrics if it does not exist
		
		// instrumentation
		File file_in = new File(argv[0]);
		String infilenames[] = file_in.list();
		
		for (int i = 0; i < infilenames.length; i++) {
			String infilename = infilenames[i];
			if (infilename.endsWith(".class")) {
				// create class info object
				ClassInfo ci = new ClassInfo(argv[0] + System.getProperty("file.separator") + infilename);
				
				// loop through all the routines
				for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
					Routine routine = (Routine) e.nextElement();
					routine.addBefore("MethodCallsCount", "mcount", new Integer(1));
					
					//We only want to print the information from the renderHandler method
					if (ci.getClassName().equals("WebServer$RenderHandler")) {
						if (routine.getMethodName().equals("handle")) {
							routine.addAfter("MethodCallsCount", "saveMetrics", ci.getClassName());
							routine.addBefore("MethodCallsCount", "initializeMCount", 0);
							routine.addBefore("MethodCallsCount", "resetRequestArguments", 0);
						}
					}
				}
				
				ci.write(argv[1] + System.getProperty("file.separator") + infilename);
			}
		}
	}
	
	public static synchronized void saveMetrics(String foo) {
		metricsManager.saveMetrics(input_file.get(), sc.get(), sr.get(), wc.get(), wr.get(), coff.get(), roff.get(), counter.get());
	}
	
	// legacy function to get the methods counter from the RenderHandler
	public static long getMethodsCounter() {
		return counter.get();
	}

	public static void mcount(int incr) {
		if(null != counter)
			counter.set(counter.get()+1);
	}

	public static void initializeMCount(int value) {
		if(null == counter){
			counter = new ThreadLocal<Long>() {
				@Override protected Long initialValue() {
					return (long) 0;
				}
			};
		}
		counter.set((long) 0);
	}
	
	public static void resetRequestArguments(int value) {
		if(input_file == null){
			input_file = new ThreadLocal<String>();
		}
		if(sc == null){
			sc = new ThreadLocal<Integer>() {
				@Override protected Integer initialValue() { return -1; }
			};
		}	
		if(sr == null){
			sr = new ThreadLocal<Integer>() {
				@Override protected Integer initialValue() { return -1; }
			};
		}	
		if(wc == null){
			wc = new ThreadLocal<Integer>() {
				@Override protected Integer initialValue() { return -1; }
			};
		}	
		if(wr == null){
			wr = new ThreadLocal<Integer>() {
				@Override protected Integer initialValue() { return -1; }
			};
		}	
		if(coff == null){
			coff = new ThreadLocal<Integer>() {
				@Override protected Integer initialValue() { return -1; }
			};
		}
		if(roff == null){
			roff = new ThreadLocal<Integer>() {
				@Override protected Integer initialValue() { return -1; }
			};
		}
		
	}
	
	// set the thread local atributes acording to the request
	public static void setRequestArguments(String file, int scR, int srR, int wcR, int wrR, int coffR, int roffR, MetricsManager metrics) {
		input_file.set(file);
		sc.set(scR);
		sr.set(srR);
		wc.set(wcR);
		wr.set(wrR);
		coff.set(coffR);
		roff.set(roffR);

		metricsManager = metrics;
	}
	
}

