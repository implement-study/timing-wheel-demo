package club.shengsheng;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

/**
 * @author gongxuanzhangmelt@gmail.com
 **/
public class TimerWheel {

    private volatile long startTime;

    private final MpscTaskQueue[] wheel;

    private final Ticker ticker;

    private final AtomicBoolean started;

    private final CountDownLatch startTimeLatch;

    private final ExecutorService executor;

    public TimerWheel() {
        wheel = new MpscTaskQueue[10];
        ticker = new Ticker();
        started = new AtomicBoolean(false);
        startTimeLatch = new CountDownLatch(1);
        executor = Executors.newFixedThreadPool(6);
        for (int i = 0; i < wheel.length; i++) {
            wheel[i] = new MpscTaskQueue();
        }
    }


    public void addDelayTask(Runnable runnable, long delayMs) {
        start();
        DelayTask task = new DelayTask(runnable, delayMs);
        int index = Math.toIntExact((task.deadline - startTime) / 100 % wheel.length);
        MpscTaskQueue slot = wheel[index];
        slot.pushTask(task);
    }


    private void start() {
        if (started.compareAndSet(false, true)) {
            ticker.start();
        }
        try {
            startTimeLatch.await();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void stop() {
        if (started.compareAndSet(true, false)) {
            LockSupport.unpark(ticker);
        }
    }


    public class DelayTask {
        final Runnable runnable;
        final long deadline;
        DelayTask next;
        DelayTask pre;

        public DelayTask(Runnable runnable, long delayMs) {
            this.runnable = runnable;
            this.deadline = System.currentTimeMillis() + delayMs;
        }
    }

    public class Slot {

        DelayTask head;
        DelayTask tail;

        public synchronized void runWithDeadline(long tickTime) {
            // mpscQueue
            // multiple producer single consumer queue
            DelayTask current = head;
            while (current != null) {
                DelayTask next = current.next;
                if (current.deadline <= tickTime) {
                    removeTask(current);
                    executor.execute(current.runnable);
                }
                current = next;
            }
        }

        private void removeTask(DelayTask current) {
            if (current.pre != null) {
                current.pre.next = current.next;
            }
            if (current.next != null) {
                current.next.pre = current.pre;
            }
            if (current == head) {
                head = current.next;
            }
            if (current == tail) {
                tail = current.pre;
            }
            current.pre = null;
            current.next = null;
        }

        public synchronized void pushDelayTask(DelayTask delayTask) {
            if (head == null) {
                head = tail = delayTask;
            } else {
                tail.next = delayTask;
                delayTask.pre = tail;
                tail = delayTask;
            }
        }
    }

    public class Ticker extends Thread {

        int tickCount = 0;

        @Override
        public void run() {
            startTime = System.currentTimeMillis();
            startTimeLatch.countDown();
            while (started.get()) {
                long tickTime = startTime + (tickCount + 1) * 100L;
                while (System.currentTimeMillis() <= tickTime) {
                    LockSupport.parkUntil(tickTime);
                    if (!started.get()) {
                        return;
                    }
                }
                int index = tickCount % wheel.length;
                MpscTaskQueue queue = wheel[index];
                List<Runnable> runnables = queue.removeAndReturnShouldRun(tickTime);
                runnables.forEach(executor::execute);
                tickCount++;
            }
        }
    }

}
