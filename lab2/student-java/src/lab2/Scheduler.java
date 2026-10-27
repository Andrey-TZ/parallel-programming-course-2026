package lab2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

/* Этап 1: планировщик, который ведёт настоящие потоки строго по одному (передача хода через семафоры).
 * Этап 2: обнаружение deadlock в главном цикле и корректное завершение застрявших потоков.
 * Внутреннее устройство (свои классы, семафоры, вспомогательные методы для Shared и SchedLock) придумываете сами. */
public final class Scheduler {
    private static final ThreadLocal<Scheduler> CURRENT_SCHEDULER = new ThreadLocal<>();
    static final ThreadLocal<Worker> CURRENT = new ThreadLocal<>();

    enum State {
        READY,
        RUNNING,
        WAITING_SHARED,
        WAITING_LOCK,
        FINISHED
    }

    static final class Worker {
        final int id;
        final String name;
        final Runnable body;
        final Semaphore semaphore = new Semaphore(0);
        public BooleanSupplier awaitCond;
        State state = State.READY;
        boolean touchedShared = true;
        Thread thread;
        SchedLock waitingLock = null;


        Worker(int id, Runnable body) {
            this.id = id;
            this.name = String.valueOf((char) ('A' + id));
            this.body = body;
        }

        boolean isAlive() {
            return state == State.READY || state == State.RUNNING
                    || state == State.WAITING_LOCK || state == State.WAITING_SHARED;
        }

    }

    /**
     * Если true, каждая операция с общими данными добавляет строку в log. Выставляет runner.
     */
    boolean logging;
    /**
     * Лог прогона: строки "<поток>  <операция>", например "A  lock mu", "A  balance -> 100".
     */
    final List<String> log = new ArrayList<>();
    /**
     * null, если deadlock не было; иначе "held={a:A, b:B}" (этап 2).
     */
    String deadlock;
    final List<SchedLock> schedLocks = new ArrayList<>();
    final List<Worker> workers = new ArrayList<>();
    final Semaphore schedulerSem = new Semaphore(0);
    final List<String> trace = new ArrayList<>();
    volatile boolean aborted = false;


    public Scheduler() {
    }

    /**
     * Зарегистрировать поток. Потоки получают буквы A, B, C… в порядке вызова spawn.
     */
    public void spawn(Runnable body) {
        int id = workers.size();
        Worker w = new Worker(id, body);
        workers.add(w);

        w.thread = new Thread(() -> {
            CURRENT.set(w);
            CURRENT_SCHEDULER.set(this);
            try {
                w.semaphore.acquire();
                body.run();
            } catch (CancellationException | InterruptedException _) {
                // ignore
            } finally {
                w.state = State.FINISHED;
                CURRENT.remove();
                schedulerSem.release();
            }
        }, w.name);

    }

    /**
     * Запустить потоки и вести их по одному: на каждой паузе спросить strategy.pick(enabled, last).
     */
    public void run(Strategy strategy) {
        for (Worker w : workers) {
            w.thread.start();
            w.semaphore.release();
            schedulerSem.acquireUninterruptibly();
        }

        while (true) {
            boolean anyAlive = false;
            List<Integer> enabled = new ArrayList<>();
            for (Worker w : workers) {
                if (w.isAlive()) {
                    anyAlive = true;
                    System.err.printf("worker %s waiting lock %s lock's owner %s\n",
                            w.name,
                            w.waitingLock != null ? w.waitingLock.name : null,
                            w.waitingLock != null && w.waitingLock.owner != null ? w.waitingLock.owner.name : null);
                    if ((w.awaitCond == null || w.awaitCond.getAsBoolean()) && (w.waitingLock == null || w.waitingLock.owner == null)) {
                        enabled.add(w.id);

                    }
                }
            }
            System.err.println("any alive: " + anyAlive);
            System.err.println("enabled: "  + enabled.stream().map(Object::toString).collect(Collectors.joining()));
            if (!anyAlive) {
                break;
            }
            if (enabled.isEmpty()) {
                System.err.println("deadlock");
                StringBuilder sb = new StringBuilder("held={");
                boolean hasPrevious = false;
                for (SchedLock schedLock : schedLocks) {
                    if (hasPrevious) sb.append(", ");
                    sb.append(schedLock.name).append(":").append(schedLock.owner.name);
                    hasPrevious = true;
                }
                sb.append("}");
                deadlock = sb.toString();
                this.aborted = true;
                System.err.println(deadlock);
                for (Worker w : workers) {
                    if (w.isAlive()) {
                        System.out.println("release worker " + w.name);
                        w.semaphore.release();
                    }
                }
                break;
            }
            int lastId = !trace.isEmpty() ? trace.getLast().charAt(0) - 'A' : -1;
            int selectedId = strategy.pick(enabled, lastId);
            Worker selected = workers.get(selectedId);
            trace.add(selected.name);
            selected.state = State.RUNNING;
            selected.semaphore.release();
            schedulerSem.acquireUninterruptibly();
            CURRENT.remove();

        }
        try {
            for (Worker w : workers) {
                w.thread.join();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * Трасса прогона: буквы потоков, выбранных во всех решениях подряд, например "AAAABB".
     */
    public String traceString() {
        return String.join("", trace);
    }

    /**
     * window() из тестовых программ: пауза перед следующей операцией с общими данными обязательна.
     */
    public static void point() {
        Worker w = CURRENT.get();
        Scheduler s = CURRENT_SCHEDULER.get();
        if (s.logging) {
            s.log.add(w.name + "  point");
        }
        w.touchedShared = true;

    }

    /**
     * Ждать, пока cond не станет true. До этого поток не попадает в enabled.
     */
    public static void await(BooleanSupplier cond) {
        Worker w = CURRENT.get();
        Scheduler s = CURRENT_SCHEDULER.get();
        if (w.touchedShared || !cond.getAsBoolean()) {
            w.awaitCond = cond; // регистрируем условие ожидания
            w.touchedShared = false;
            s.schedulerSem.release();
            w.semaphore.acquireUninterruptibly();
            w.awaitCond = null; // проснулись — значит планировщик проверил, что cond == true
        }
        log(w.name + "  await done");
        w.touchedShared = true;
    }

    public static void pauseIfNeeded(boolean force) {
        Worker w = CURRENT.get();
        if (force || w.touchedShared) {
            w.touchedShared = false;
            w.state = State.WAITING_SHARED;
            Scheduler s = CURRENT_SCHEDULER.get();
            s.schedulerSem.release();
            w.semaphore.acquireUninterruptibly();
        }
    }

    public static Scheduler getScheduler() {
        return CURRENT_SCHEDULER.get();
    }

    public static void log(String msg) {
        Scheduler s = CURRENT_SCHEDULER.get();
        if (s.logging) {
            Worker w = CURRENT.get();
            s.log.add(w.name + "  " + msg);
        }
    }

    public static void touchShared() {
        Worker w = CURRENT.get();
        w.touchedShared = true;
    }

    public void registerLock(SchedLock lock) {
        schedLocks.add(lock);
    }
}
