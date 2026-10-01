public class Main {
    public static void main(String[] args) {
        int processors = Runtime.getRuntime().availableProcessors();
        System.out.println("Logical processors: " + processors);
    }
}
