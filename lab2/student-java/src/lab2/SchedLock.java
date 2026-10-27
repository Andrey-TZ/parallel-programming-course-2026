package lab2;

import java.util.concurrent.CancellationException;

/* Этап 2: лок, о котором знает планировщик. Поток, ждущий занятый лок, не попадает в enabled;
 * обычный synchronized / ReentrantLock здесь нельзя — поток уснёт с ходом в руках и всё зависнет.
 * Лог: "lock <имя>", "unlock <имя>". Планировщик должен знать все локи, чтобы печатать held={a:A, b:B}. */
public final class SchedLock {
    final String name;
    final Scheduler scheduler;

    Scheduler.Worker owner = null; 

    /** Создаётся в setup() тестовой программы; зарегистрируйте лок в планировщике s. */
    public SchedLock(Scheduler s, String name) {
        this.name = name;
        this.scheduler = s;
        s.registerLock(this);
    }

    /** Пауза; если лок занят — ждать, не участвуя в выборе; затем стать владельцем. */
    public void lock() {
        Scheduler.Worker worker = Scheduler.CURRENT.get();
        System.err.println(worker.name + " trying to get lock " + this.name);

        if (worker.touchedShared) {
            worker.touchedShared = false;
            worker.state = Scheduler.State.WAITING_LOCK;
            //if (owner != null)
            worker.waitingLock = this;
            scheduler.schedulerSem.release();
            worker.semaphore.acquireUninterruptibly();
        }

        if (scheduler.aborted) {
            System.err.println(worker.name + " unlocked because of the aborting");
            throw new CancellationException();
        }

        while (owner != null) {
            worker.state = Scheduler.State.WAITING_LOCK;
            worker.waitingLock = this;
            worker.touchedShared = false;
            scheduler.schedulerSem.release();
            worker.semaphore.acquireUninterruptibly();
            if (scheduler.aborted) {
                System.err.println(worker.name + " unlocked because of the aborting");
                throw new CancellationException();
            }
        }

        worker.waitingLock = null;
        this.owner = worker;
        System.err.println(worker.name + " got lock " + this.name);
        Scheduler.log(worker.name + "  lock " + this.name);

        worker.touchedShared = true;
    }

    /** Пауза; освободить лок, ждавшие потоки снова могут быть выбраны. */
    public void unlock() {
        Scheduler.Worker worker = Scheduler.CURRENT.get();
        Scheduler.pauseIfNeeded(false);
        owner.state = Scheduler.State.READY;
        owner = null;

        Scheduler.log(worker.name + "  unlock " + this.name);

        worker.touchedShared = true;

    }
}
