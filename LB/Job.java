import java.util.Date;
import java.util.HashMap;
import java.util.UUID;

public class Job {
    private String fileName;
    private int wc, wr, sc, sr, coff, roff;
    private String _jobId;
    private double _estimatedCount = -1;
    private long _jobStartedTime, _jobEndedTime;
    private boolean _jobIsDone;
    private HashMap<String, Double> _coefficientMap;

    public Job(String fileName, int wc, int wr, int sc, int sr, int coff, int roff) {
        _jobId = UUID.randomUUID().toString();
        this.wc = wc;
        this.wr = wr;
        this.sc = sc;
        this.sr = sr;
        this.coff = coff;
        this.roff = roff;
        this.fileName = fileName;
        _jobIsDone = false;
        _coefficientMap = new HashMap<>();
        _coefficientMap.put("test01.txt", 1.0);
        _coefficientMap.put("test02.txt", 1.95);
        _coefficientMap.put("test03.txt", 1.59);
        _coefficientMap.put("test04.txt", 7.66);
        _coefficientMap.put("test05.txt", 3.50);
    }
    
    
    public String getJobId() {
        return _jobId;
    }
     
	public String toString() {
        return fileName + ", " + sc + ", " + wc + ", " + wr + ", " + sr + ", " + coff + ", " + roff;
    }
    
    
	public void start() {
		System.out.println("\n\u001B[03m START \u001B[0m" + ": " + toString());
		_jobStartedTime = new Date().getTime();
	}
	
    
    public void stop() {
		System.out.println("\n\u001B[03m STOP \u001B[0m" + ": " + toString());
        _jobEndedTime = new Date().getTime();
        _jobIsDone = true;
    }
    
    public boolean previousMetricsExists() {
        return _estimatedCount != -1;
    }
    
    
    public double estimateCost(MetricsManager metricsManager) {
        long methodCalls = metricsManager.getMetrics(fileName, sc, sr, wc, wr, coff, roff);

        // If methodCalls does not exist for this job we have to approximate it.
        if (methodCalls < 0) {
			System.out.println("\u001B[03m" + "-> missed metric <-" + "\u001B[0m");
            Double fileCoefficient = _coefficientMap.get(fileName);
            
            // Estimate metrics using regression formula
            // If sc*sr and wc*wr is to small the formula will return a negative number. In this case
            // the mc is and computing cost is so low that we can consider it to be 1.
            double estimatedMc =  (((sc*sr) * 1.4 + (wc*wr) * 124 - 2621250) * fileCoefficient);
//             double cost = metricsManager.estimateCost(Math.ceil(estimatedMc));			
            estimatedMc = (estimatedMc > 1)? estimatedMc : 1;
            double cost = metricsManager.estimateCost(Math.round(estimatedMc));
            _estimatedCount = cost;
            return _estimatedCount;
            
        } else {
            double cost = metricsManager.estimateCost(methodCalls);
            _estimatedCount = cost;
            return cost;
        }
        
//         return -1;
    }
    
    
    public double getEstimatedCost() {
		
		if(_estimatedCount > 0)
			return _estimatedCount;
			
		else
			return -1;
    }
    
	
    public double getEstimatedCost(MetricsManager metricsManager) {
		
		if(_estimatedCount > 0) {
			return _estimatedCount;
			
		} else {
			return estimateCost(metricsManager);
		}
    }
    
}

