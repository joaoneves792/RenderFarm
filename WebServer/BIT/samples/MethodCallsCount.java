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
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;


public class MethodCallsCount {
    private static PrintStream out = null;
    private static ThreadLocal<Integer> counter;

    /* main reads in all the files class files present in the input directory,
     * instruments them, and outputs them to the specified output directory.
     */
    public static void main(String argv[]) {
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
                    if(ci.getClassName().equals("WebServer$RenderHandler")){
                        if (routine.getMethodName().equals("handle")){
                           routine.addAfter("MethodCallsCount", "printMCount", ci.getClassName());
                           routine.addBefore("MethodCallsCount", "initializeMCount", 0);
                        }
                    }
                    
                }
                ci.write(argv[1] + System.getProperty("file.separator") + infilename);
            }
        }
    }
    
    public static synchronized void printMCount(String foo) {
//         System.out.println(counter.get() + " method calls were executed.");
			
		FileWriter fw = null;
		BufferedWriter bw = null;
			
		try {
			long threadId = Thread.currentThread().getId();
			
			String newLine = "Thread " + threadId + " executed " + counter.get() + " methods.";
			File file =new File("_instrumentationData.txt");
			
			if(!file.exists()){
				file.createNewFile();
			}
			
			fw = new FileWriter(file, true);
			bw = new BufferedWriter(fw);
			
			bw.write(newLine);
			
		} catch(IOException e) {
			e.printStackTrace();
			
		} finally {
			try {
				if(bw != null) bw.close();
				if(fw != null) fw.close();
				
			} catch(IOException e) {
				e.printStackTrace();
			}
		}
	}
    
    public static void mcount(int incr) {
        if(null != counter)
            counter.set(counter.get()+1);
    }

    public static void initializeMCount(int value){
        if(null == counter){
            counter = new ThreadLocal<Integer>(){
                @Override protected Integer initialValue() {
                    return 0;
                }
            };
        }
        counter.set(0);
    }
}

