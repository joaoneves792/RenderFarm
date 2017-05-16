import java.util.Date;
import java.util.HashMap;
import java.util.UUID;

public class Job {
    private String fileName;
    private int wc, wr, sc, sr, coff, roff;
    private String jobId;
    private double estimatedCount = -1;
    private long jobStartedTime, jobEndedTime;
    private boolean jobIsDone;
    private HashMap<String, Double> coefficientMap;

    public Job(String fileName, int wc, int wr, int sc, int sr, int coff, int roff) {
        this.jobId = UUID.randomUUID().toString();
        this.wc = wc;
        this.wr = wr;
        this.sc = sc;
        this.sr = sr;
        this.coff = coff;
        this.roff = roff;
        this.jobIsDone = false;
        this.fileName = fileName;
        coefficientMap = new HashMap<>();
        coefficientMap.put("test01.txt", 1.0);
        coefficientMap.put("test01.txt", 1.95);
        coefficientMap.put("test01.txt", 1.59);
        coefficientMap.put("test01.txt", 7.66);
        coefficientMap.put("test01.txt", 3.50);
    }
    
    
    public String getJobId() {
        return this.jobId;
    }
    
    
	public void start() {
// 		System.out.println("\nSTART : " + jobId);
		this.jobStartedTime = new Date().getTime();
	}
	
    
    public void stop() {
// 		System.out.println("\nSTOP : " + jobId);
        this.jobEndedTime = new Date().getTime();
        this.jobIsDone = true;
    }
    
    public boolean previousMetricsExists() {
        return this.estimatedCount != -1;
    }
    
    
    public double estimateCost(MetricsManager metricsManager) {
        long methodCalls = metricsManager.getMetrics(fileName, sc, sr, wc, wr, coff, roff);

        // If methodCalls does not exist for this job we have to approximate it.
        if (methodCalls == -1) {
            Double fileCoefficient = coefficientMap.get(fileName);

            // Estimate metrics using regression formula
            // If sc*sr and wc*wr is to small the formula will return a negative number. In this case
            // the mc is and computing cost is so low that we can consider it to be 1.
            long estimatedMc = (long) (((sc*sr) * 1.4 + (wc*wr) * 124 - 2621250) * fileCoefficient);
            methodCalls = estimatedMc > 1 ? estimatedMc : 1;
        }

        if (methodCalls > 0) {
            double cost = metricsManager.estimateCost(methodCalls);
            estimatedCount = cost;
            return cost;
        }

        return -1;
    }
    
    
    public double getEstimatedCost() {
		
		if(estimatedCount > 0)
			return estimatedCount;
			
		else
			return -1;
    }
    
	
    public double getEstimatedCost(MetricsManager metricsManager) {
		
		if(estimatedCount > 0) {
			return estimatedCount;
			
		} else {
			return estimateCost(metricsManager);
		}
    }
    
}

