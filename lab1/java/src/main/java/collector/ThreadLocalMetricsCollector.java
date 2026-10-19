package collector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

public class ThreadLocalMetricsCollector implements MetricsCollector{

    static final class ThreadState {
        final AtomicLongArray buckets = new AtomicLongArray(256);
        final AtomicLong count = new AtomicLong();
        final AtomicLong sum = new AtomicLong();
        final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
        final AtomicLong max = new AtomicLong(0);
    }

    private final List<ThreadState> allStates = new ArrayList<>();
    private final Object listLock = new Object();

    private final ThreadLocal<ThreadState> myState = ThreadLocal.withInitial(() -> {
        ThreadState s = new ThreadState();
        synchronized (listLock) {
            allStates.add(s);
        }
        return s;
    });

    @Override
    public void record(long value) {
        ThreadState s = myState.get();
        int b = (int) Math.min(value/4, 255);

        s.buckets.setRelease(b, s.buckets.getPlain(b) + 1);
        s.count.setRelease(s.count.getPlain() + 1);
        s.sum.set(s.sum.getPlain() + value);

        if (value < s.min.getPlain()) s.min.setRelease(value);
        if (value > s.max.getPlain()) s.max.setRelease(value);

    }

    @Override
    public Snapshot snapshot() {
        List<ThreadState> copyOfStates = new ArrayList<>();
        synchronized (listLock) {
            copyOfStates = List.copyOf(allStates);
        }

        long[] out = new long[256];
        long count = 0, sum = 0, min = Long.MAX_VALUE, max = 0;

        for (ThreadState s : copyOfStates) {
            for (int i = 0; i < 256; i++) out[i] += s.buckets.get(i);
            count += s.count.get();

            sum   += s.sum.get();
            min    = Math.min(min, s.min.get());
            max    = Math.max(max, s.max.get());
        }
        // считаем p50 и p99 кумулятивно по массиву out (см. Этап 0)
        long p50 = percentile(out, count, 0.50);
        long p99 = percentile(out, count, 0.99);
        return new Snapshot(out, count, sum, min, max, p50, p99);
    }

    private long percentile(long[] buckets, long count, double percentile) {
        if (count == 0) {
            return 0;
        }

        long target = (long) Math.ceil(count * percentile);
        long accumulated = 0;

        for (int i = 0; i < buckets.length; i++) {
            accumulated += buckets[i];

            if (accumulated >= target) {
                return (long) i * 4;
            }
        }

        return 255L * 4;
    }
}
