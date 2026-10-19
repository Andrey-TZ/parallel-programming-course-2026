import collector.DoubleBufferingMetricsCollector;
import collector.MetricsCollector;

public class MainDoubleBuffering {
    private static final int VALUES_SIZE = 1_000_000;
    private static final long SEED = 42L;

    public static void main(String[] args) throws InterruptedException {

        long[] values = ZipfGenerator.generate(
                VALUES_SIZE,
                SEED
        );
        System.out.println("Values generated.");
        printDistribution(values);

        MetricsCollector collector =
                new DoubleBufferingMetricsCollector();

        int[] threadCounts = {1, 2, 4, 8, 10};
        for (int threads : threadCounts) {

            double throughput = Benchmark.measure(
                    collector,
                    values,
                    threads
            );

            System.out.printf(
                    "Threads=%d: %.2f millions ops/sec%n",
                    threads,
                    throughput / 1000000
            );
        }
    }

    private static void printDistribution(long[] values) {
        long bucket0 = 0;

        for (long value : values) {
            if (value / 4 == 0) {
                bucket0++;
            }
        }

        double percentage =
                100.0 * bucket0 / values.length;

        System.out.printf(
                "Bucket 0: %d / %d (%.2f%%)%n",
                bucket0,
                values.length,
                percentage
        );
    }
}
