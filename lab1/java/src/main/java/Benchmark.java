import collector.MetricsCollector;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class Benchmark {

    private static final int WARMUP_SECONDS = 5;
    private static final int MEASURE_SECONDS = 5;
    private static final int MEASURE_RUNS = 5;

    private Benchmark() {
    }

    public static double run(
            MetricsCollector collector,
            long[] values,
            int threads,
            int seconds
    ) throws InterruptedException {

        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean stop = new AtomicBoolean(false);

        long[] ops = new long[threads];
        Thread[] workers = new Thread[threads];

        for (int k = 0; k < threads; k++) {
            final int threadIndex = k;

            workers[k] = new Thread(() -> {
                long localCount = 0;

                int i = threadIndex * 1000;
                if (i >= values.length) {
                    i %= values.length;
                }

                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                while (!stop.get()) {
                    collector.record(values[i]);
                    localCount++;
                    i++;
                    if (i == values.length) i = 0;
                }
                ops[threadIndex] = localCount;
            });
            workers[k].start();
        }

        long t0 = System.nanoTime();

        start.countDown();

        Thread.sleep(seconds * 1000L);

        stop.set(true);

        for (Thread worker : workers) {
            worker.join();
        }

        long t1 = System.nanoTime();

        long totalOps = 0;

        for (long threadOps : ops) {
            totalOps += threadOps;
        }

        double elapsedSeconds = (t1 - t0) / 1_000_000_000.0;

        return totalOps / elapsedSeconds;
    }

    public static double measure(
            MetricsCollector collector,
            long[] values,
            int threads
    ) throws InterruptedException {
        System.out.println("Warmup");
        Benchmark.run(collector, values, threads, WARMUP_SECONDS);

        double[] results = new double[MEASURE_RUNS];
        for (int i = 0; i < MEASURE_RUNS; i++) {
            results[i] = Benchmark.run(collector, values, threads, MEASURE_SECONDS);
            System.out.printf("Run %d: %.2f mln ops/sec%n", i + 1, results[i] / 1000000);
        }
        System.out.println("snapshot.count = " + collector.snapshot().count());

        return median(results);
    }


    private static double median(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int middle = sorted.length / 2;
        if (sorted.length % 2 == 1) {
            return sorted[middle];
        }

        return (sorted[middle - 1] + sorted[middle]) / 2.0;
    }
}