package lab2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/* Стратегии выбора следующего потока.
 * FirstAlive — этап 1, Replay — этап 2, RandomPick — этап 3, Dfs — этап 4 (ограничение bound — этап 5).
 * enabled — номера потоков, которые могут сделать шаг, по возрастанию; last — прошлый выбранный (-1 в начале). */
public final class Strategies {
    private Strategies() {
    }

    /**
     * Всегда первый поток из enabled.
     */
    public static final class FirstAlive implements Strategy {
        public int pick(List<Integer> enabled, int last) {
            return enabled.getFirst();
        }
    }

    /**
     * Проигрывает трассу: на i-м решении выбирает поток trace[i] ('A' -> 0, 'B' -> 1).
     */
    public static final class Replay implements Strategy {
        final String trace;
        int pos = 0;

        public Replay(String trace) {
            this.trace = trace;
        }

        /**
         * Сбросить позицию в трассе перед прогоном.
         */
        public void beginRun() {
            pos = 0;
        }

        public int pick(List<Integer> enabled, int last) {
            if (pos >= trace.length()) {
                throw new IllegalStateException("Trace ended");
            }
            char ch = trace.charAt(pos++);
            int targetId = ch - 'A';

            if (!enabled.contains(targetId)) {
                throw new IllegalStateException("Thread " + ch + " is not enabled at step " + pos);
            }
            return targetId;
        }
    }

    /**
     * Случайный поток: enabled.get(rnd.nextInt(enabled.size())), rnd = new java.util.Random(seed).
     */
    public static final class RandomPick implements Strategy {
        private final Random rnd;

        public RandomPick(long seed) {
            rnd = new Random(seed);
        }

        public int pick(List<Integer> enabled, int last) {
            return enabled.get(rnd.nextInt(enabled.size()));
        }
    }

    /**
     * Stateless DFS по дереву выборов: каждый прогон с нуля, повторяет префикс прошлого и берёт следующий вариант.
     * bound — максимум вытеснений (этап 5); для dfs runner передаёт Integer.MAX_VALUE.
     */
    public static final class Dfs implements Strategy {
        public static class Node {
            final List<Integer> choices;
            final int last;
            final boolean wasLastEnabled;
            int idx = 0;

            Node(List<Integer> choices, int last, boolean wasLastEnabled) {
                this.choices = choices;
                this.last = last;
                this.wasLastEnabled = wasLastEnabled;
            }

            int currentChoice() {
                return choices.get(idx);
            }

            boolean isPreemption() {
                return wasLastEnabled && (currentChoice() != last);
            }
        }

        private final int bound;
        private final List<Node> path = new ArrayList<>();
        private int depth = 0;
        private int preemptions = 0;

        public Dfs(int bound) {
            this.bound = bound;
        }

        /**
         * Подготовиться к новому прогону (позиция в пути, счётчик вытеснений).
         */
        public void beginRun() {
            depth = 0;
            preemptions = 0;
        }

        public int pick(List<Integer> enabled, int last) {
            Node node;
            if (depth < path.size()) {
                node = path.get(depth);
            } else {
                boolean canContinueLast = (last != -1) && enabled.contains(last);
                List<Integer> choices;

                if (canContinueLast && preemptions >= bound) {
                    choices = Collections.singletonList(last);
                    System.err.println();
                } else {
                    choices = new ArrayList<>(enabled);
                }

                node = new Node(choices, last, canContinueLast);
                path.add(node);
            }
            int chosen = node.currentChoice();

            if (node.isPreemption()) {
                preemptions++;
            }

            depth++;
            return chosen;
        }

        /**
         * Выбрать следующий путь; false — дерево обойдено.
         */
        public boolean endRun() {
            System.err.println("finished " + currentBranch());
            while (!path.isEmpty()) {
                Node top = path.getLast();
                if (top.idx + 1 < top.choices.size()) {
                    top.idx++;
                    return true;
                }
                path.removeLast();
            }
            return false;
        }

        private String currentBranch() {
            return path.stream()
                    .map(Node::currentChoice)
                    .collect(StringBuilder::new,
                            (sb, x) -> sb.append((char) ('A' + x)),
                            StringBuilder::append)
                    .toString();

        }
    }
}
