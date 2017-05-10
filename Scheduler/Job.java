import java.util.Date;
import java.util.UUID;

public class Job {
    private int wc, wr, sc, sr, coff, roff;
    private String jobId;
    private int estimatedCount = -1;
    private long jobStartedTime, jobEndedTime;
    private boolean jobIsDone;

    public Job(String fileName, int wc, int wr, int sc, int sr, int coff, int roff) {
        this.jobId = UUID.randomUUID().toString();
        this.wc = wc;
        this.wr = wr;
        this.sc = sc;
        this.sr = sr;
        this.coff = coff;
        this.roff = roff;
        this.jobIsDone = false;
    }

    public String getJobId() {
        return this.jobId;
    }

    public void start() {
        this.jobStartedTime = new Date().getTime();
    }

    public void stop() {
        this.jobEndedTime = new Date().getTime();
        this.jobIsDone = true;
    }

    public boolean previousMetricsExists() {
        return this.estimatedCount != -1;
    }

}
