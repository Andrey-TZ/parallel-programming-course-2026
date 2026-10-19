package collector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class DoubleBufferingMetricsCollector implements MetricsCollector {
    static final class ThreadBuffers {
        final long[][] buckets = new long[2][256];
        final long[] count = new long[2];
        final long[] sum = new long[2];
        final long[] min = {Long.MAX_VALUE, Long.MAX_VALUE};
        final long[] max = {0, 0};

        final AtomicInteger inside = new AtomicInteger(-1);

        void clearBuffer(int i) {
            count[i] = 0;
            sum[i] = 0;
            min[i] = Long.MAX_VALUE;
            max[i] = 0;
            Arrays.fill(buckets[i], 0);
        }
    }

    private final List<ThreadBuffers> allBuffers = new ArrayList<>();
    private final Object listLock = new Object();
    private final AtomicInteger active = new AtomicInteger(0);

    private final long[] buckets = new long[256];
    private long count = 0;
    private long sum = 0;
    private long min = Long.MAX_VALUE;
    private long max = 0;

    private final ThreadLocal<ThreadBuffers> myBuffers = ThreadLocal.withInitial(() -> {
        ThreadBuffers b = new ThreadBuffers();
        synchronized (listLock) {
            allBuffers.add(b);
        }
        return b;
    });

    @Override
    public void record(long value) {
        ThreadBuffers buffers = myBuffers.get();
        int b;

        while (true) {
            b = active.get();
            buffers.inside.set(b);
            break;
//            if (active.get() == b) {
//                break;
//            }
//            buffers.inside.setRelease(-1);
        }

        int bucket = Math.toIntExact(Math.min((value / 4), 255));
        buffers.buckets[b][bucket]++;

        buffers.count[b]++;
        buffers.sum[b] += value;
        buffers.min[b] = Math.min(value, buffers.min[b]);
        buffers.max[b] = Math.max(value, buffers.max[b]);

        buffers.inside.setRelease(-1);
    }

    @Override
    public Snapshot snapshot() {
        synchronized (listLock) {
            int old = active.get();
            active.set(1 - old);
            for (ThreadBuffers buffer : allBuffers) {
                // Switching buffers
                while (buffer.inside.get() == old) {
                    Thread.onSpinWait();
                }

                // Read frozen buffers
                count += buffer.count[old];
                sum += buffer.sum[old];

                for (int i = 0; i < 256; i++) {
                    buckets[i] += buffer.buckets[old][i];
                }
                min = Math.min(min, buffer.min[old]);
                max = Math.max(max, buffer.max[old]);

                //clear buffers
                buffer.clearBuffer(old);
            }
        }

        long p50 = percentile(buckets, count, 0.50);
        long p99 = percentile(buckets, count, 0.99);

        return new Snapshot(buckets, count, sum, min, max, p50, p99);
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
