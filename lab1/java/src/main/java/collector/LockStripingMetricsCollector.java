package collector;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class LockStripingMetricsCollector implements MetricsCollector {

    private final long[] buckets = new long[256];
    private final ReentrantLock[] locks = new ReentrantLock[16];
    private AtomicLong min = new AtomicLong(Long.MAX_VALUE);
    private AtomicLong max = new AtomicLong(Long.MIN_VALUE);
    private AtomicLong sum = new AtomicLong(0);
    private AtomicLong count = new AtomicLong(0);

    public LockStripingMetricsCollector() {
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new ReentrantLock();
        }
    }


    @Override
    public void record(long value) {
        int bucket = Math.toIntExact(Math.min((value / 4), 255));
        var lock = locks[bucket % locks.length];

        lock.lock();
        try {
            buckets[bucket]++;
        } finally {
            lock.unlock();
        }

        count.incrementAndGet();
        sum.addAndGet(value);
        updateMin(value);
        updateMax(value);
    }

    private void updateMin(long value) {
        long current;

        do {
            current = min.get();
            if (value >= current) {
                return;
            }
        } while (!min.compareAndSet(current, value));
    }

    private void updateMax(long value) {
        long current;

        do {
            current = max.get();
            if (value <= current) {
                return;
            }
        } while (!max.compareAndSet(current, value));
    }

    @Override
    public Snapshot snapshot() {
        long[] bucketsCopy = new long[256];
        for (int i = 0; i < locks.length; i++) {
            locks[i].lock();
            try {
                for (int j = 0; j < locks.length; j++) {
                    bucketsCopy[i * 16 + j] = buckets[i * 16 + j];
                }
            } finally {
                locks[i].unlock();
            }
        }

        return new Snapshot(
                bucketsCopy,
                count.get(), sum.get(), min.get(), max.get(), percentile(0.5, bucketsCopy),
                percentile(0.99, bucketsCopy));
    }

    private long percentile(double percentile, long[] buckets) {
        if (count.get() == 0) {
            return 0;
        }

        long target = (long) Math.ceil(count.get() * percentile);
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
