import java.util.Random;

public class ZipfGenerator {
    private static final int MAX_VALUE = 1023;
    private static final double EXPONENT = 1.15;

    private ZipfGenerator() {
    }

    public static long[] generate(int size, long seed) {
        double[] cumulative = buildDistribution();

        Random random = new Random(seed);
        long[] values = new long[size];

        for (int i = 0; i < size; i++) {
            double probability = random.nextDouble();

            int value = findValue(cumulative, probability);
            values[i] = value;
        }

        return values;
    }

    private static double[] buildDistribution() {
        double[] probabilities = new double[MAX_VALUE];

        double sum = 0.0;

        for (int k = 1; k <= MAX_VALUE; k++) {
            sum += 1.0 / Math.pow(k, EXPONENT);
        }

        double cumulative = 0.0;

        for (int k = 1; k <= MAX_VALUE; k++) {
            cumulative += (1.0 / Math.pow(k, EXPONENT)) / sum;
            probabilities[k - 1] = cumulative;
        }

        probabilities[MAX_VALUE - 1] = 1.0;

        return probabilities;
    }

    private static int findValue(double[] cumulative, double probability) {
        int left = 0;
        int right = cumulative.length - 1;

        while (left < right) {
            int middle = (left + right) >>> 1;

            if (probability <= cumulative[middle]) {
                right = middle;
            } else {
                left = middle + 1;
            }
        }

        return left + 1;
    }
}
