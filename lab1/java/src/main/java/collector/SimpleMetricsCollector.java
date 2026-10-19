package collector;

import java.util.Arrays;

public class SimpleMetricsCollector implements MetricsCollector {
    private final long[] buckets = new long[256];
    private long min = Long.MAX_VALUE ;
    private long max = Long.MIN_VALUE;
    private long sum = 0;
    private long count = 0;

    public SimpleMetricsCollector() {

    }

    @Override
    public void record(long value) {
        int bucket = Math.toIntExact(Math.min((value / 4), 255));
        buckets[bucket]++;

        count++;
        sum += value;
        min = Math.min(min, value);
        max = Math.max(max, value);
    }

    @Override
    public Snapshot snapshot() {

        return new Snapshot(
                Arrays.copyOf(buckets, 256),
                count, sum, min, max, percentile(0.5),
                percentile(0.99));
    }

    private long percentile(double percentile) {
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
