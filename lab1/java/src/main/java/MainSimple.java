import collector.MetricsCollector;
import collector.SimpleMetricsCollector;

public class MainSimple {

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
                new SimpleMetricsCollector();

        double baseline = Benchmark.measure(
                collector,
                values,
                1
        );

        System.out.printf(
                "Baseline: %.2f mln ops/sec%n",
                baseline / 1000000
        );
//
//        int[] threadCounts = {2, 4, 8, 16};
//
//        for (int threads : threadCounts) {
//
//            collector = new SimpleMetricsCollector();
//
//            double throughput = Benchmark.measure(
//                    collector,
//                    values,
//                    threads
//            );
//
//            System.out.printf(
//                    "Threads=%d: %.2f mln ops/sec%n",
//                    threads,
//                    throughput
//            );
//        }
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