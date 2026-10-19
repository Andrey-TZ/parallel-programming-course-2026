import collector.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class StressTest {

    public static void main(String[] args) throws InterruptedException {
        long[] values = ZipfGenerator.generate(10000, 42);
        //runThreads(new LockStripingMetricsCollector(), values);
        //runThreads(new ThreadLocalMetricsCollector(), values);
        runThreads(new DoubleBufferingMetricsCollector(), values);

    }

    static void runThreads(MetricsCollector collector, long[] values) throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);

        AtomicBoolean stop = new AtomicBoolean(false);
        long[] ops = new long[4];

        Thread[] workers = new Thread[4];
        for (int i = 0; i < 4; i++) {
            int threadIndex = i;

            Thread thread = new Thread(
                    () -> {
                        long localCount = 0;

                        int k = threadIndex * 1000;
                        if (k >= values.length) {
                            k %= values.length;
                        }

                        try {
                            start.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }

                        while (!stop.get()) {
                            collector.record(values[k]);
                            localCount++;
                            k++;

                            if (k == values.length) {
                                k = 0;
                            }
                        }
                        ops[threadIndex] = localCount;
                    }
            );

            workers[threadIndex] = thread;
            thread.start();
        }

        start.countDown();

        long sumBigger = 0;
        long sumSmaller = 0;
        long sum = 0;

        for (int i = 0; i < 10000; i++) {
            sum = 0;
            //Thread.sleep(10);
            //System.out.println("Snapshot " + i);
            var snapshot = collector.snapshot();
            for (long k : snapshot.buckets()) {
                sum += k;
            }
            if (sum > snapshot.count()) {
                sumBigger++;
            } else if (sum < snapshot.count()) {
                sumSmaller++;
            }
        }
        long count = 0;
        stop.set(true);
        for (int i = 0; i < 4; i ++) {
            workers[i].join();
            count += ops[i];
        }
        Thread.sleep(10);
        var finalSnapshot = collector.snapshot();
        double broken = (double) ((sumSmaller + sumBigger)) / 10000 * 100;
        System.out.println("Числов вызовов записи (сумма счетчиков потоков): " + count);
        System.out.println("Числов вызовов записи (снимок): " + finalSnapshot.count());
        System.out.println("Снимок: sum < count " + sumSmaller);
        System.out.println("Снимок: sum > count " + sumBigger);
        System.out.println("Доля битых снимков (sum != count): " + broken + "%");
    }
}
