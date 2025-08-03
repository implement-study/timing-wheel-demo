package club.shengsheng;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author gongxuanzhangmelt@gmail.com
 **/
public class MpscTaskQueue {

    private final AtomicReference<TimerWheel.DelayTask> head = new AtomicReference<>(null);


    public void pushTask(TimerWheel.DelayTask delayTask) {
        while (true) {
            TimerWheel.DelayTask oldHead = head.get();
            delayTask.next = oldHead;
            if (head.compareAndSet(oldHead, delayTask)) {
                return;
            }
        }
    }

    public List<Runnable> removeAndReturnShouldRun(long tickTime) {
        //  z->e->d->A->B->C
        List<Runnable> result = new ArrayList<>();
        TimerWheel.DelayTask current = head.get();
        TimerWheel.DelayTask pre = null;
        while (current != null) {
            if (current.deadline > tickTime) {
                pre = current;
                current = current.next;
                continue;
            }
            TimerWheel.DelayTask next = current.next;
            if (pre != null) {
                pre.next = next;
                result.add(current.runnable);
                current.next = null;
                current = next;
                continue;
            }
            if (head.compareAndSet(current, next)) {
                result.add(current.runnable);
                current.next = null;
                current = next;
                continue;
            }
            current = head.get();
        }
        return result;
    }

}
