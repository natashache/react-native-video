package com.brentvatne.utils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Debouncer<T> {

    private final ScheduledExecutorService sched = Executors.newScheduledThreadPool(1);
    private final ConcurrentHashMap<T, TimerTask> delayedMap = new ConcurrentHashMap<T, TimerTask>();

    public Debouncer() {
    }

    public void call(T key, Runnable runnable, int interval, TimeUnit timeUnit) {
        TimerTask task = new TimerTask(key, runnable, interval, timeUnit);

        TimerTask prev;
        do {
            prev = delayedMap.putIfAbsent(key, task);
            if (prev == null)
                sched.schedule(task, interval, timeUnit);
        } while (prev != null && !prev.extend());
    }

    public void terminate() {
        sched.shutdownNow();
    }

    private class TimerTask implements Runnable {
        private final T key;
        private final Runnable runnable;
        private final int interval;
        private final TimeUnit timeUnit;
        private long dueTime;
        private final Object lock = new Object();

        public TimerTask(T key, Runnable runnable, int interval, TimeUnit timeUnit) {
            this.key = key;
            this.runnable = runnable;
            this.interval = interval;
            this.timeUnit = timeUnit;
            extend();
        }

        public boolean extend() {
            synchronized (lock) {
                if (dueTime < 0)
                    return false;
                dueTime = System.currentTimeMillis() + TimeUnit.MILLISECONDS.convert(interval, timeUnit);
                return true;
            }
        }

        public void run() {
            synchronized (lock) {
                long remaining = dueTime - System.currentTimeMillis();
                if (remaining > 0) { // Re-schedule task
                    sched.schedule(this, remaining, TimeUnit.MILLISECONDS);
                } else { // Mark as terminated and invoke callback
                    dueTime = -1;
                    try {
                        runnable.run();
                    } finally {
                        delayedMap.remove(key);
                    }
                }
            }
        }
    }
}