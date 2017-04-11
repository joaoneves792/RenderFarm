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


public class MethodCallsCount {
    private static PrintStream out = null;
    public static ConcurrentHashMap<Long, Long> counters = new ConcurrentHashMap<>();

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


					//We only want to print the information at the end of the renderHandler method (after each request for rendering at the server)
                    if(ci.getClassName().equals("WebServer$RenderHandler")){
                        if (routine.getMethodName().equals("handle")){
                           routine.addAfter("MethodCallsCount", "printMCount", ci.getClassName());
                        }
                    }

                }
                ci.write(argv[1] + System.getProperty("file.separator") + infilename);
            }
        }
    }
    
    public static synchronized void printMCount(String foo) {
        long threadID = Thread.currentThread().getId();
        System.out.println(counters.get(threadID) + " method calls were executed.");
        counters.put(threadID, new Long(0));
    }
    
    public static synchronized void mcount(int incr) {
        long threadID = Thread.currentThread().getId();
        if(counters.containsKey(threadID)){
            counters.put(threadID, counters.get(threadID)+incr);
        }else{
            counters.put(threadID, new Long(incr));
        }
    }
}

