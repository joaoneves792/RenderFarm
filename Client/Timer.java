
import java.util.concurrent.TimeUnit;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


public class Timer {

    long _start;
	DateTimeFormatter _dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");


    private Timer() {
        reset();
    }

    public static Timer start() {
        return new Timer();
    }

    public Timer reset() {
		_start = System.nanoTime();
        return this;
    }

    public long time() {
        long end = System.nanoTime();
        return end - _start;
    }

    public long time(TimeUnit unit) {
        return unit.convert(time(), TimeUnit.NANOSECONDS);
    }

    public String toMinuteSeconds(){
        return String.format("%d min, %d sec", time(TimeUnit.MINUTES),
                time(TimeUnit.SECONDS) - time(TimeUnit.MINUTES));
    }
}

