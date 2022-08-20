package com.wangguangwu.test;

import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * @author wangguangwu
 */
public class MyLock {

    private static class Sync extends AbstractQueuedSynchronizer {
        @Override
        protected boolean tryAcquire(int arg) {
            return compareAndSetState(0,1);
        }

        @Override
        protected boolean tryRelease(int arg) {
            setState(0);
            return true;
        }

        @Override
        protected boolean isHeldExclusively() {
            return getState() == 1;
        }
    }

    private Sync sync = new Sync();

    public void lock() {
        sync.acquire(1);
    }

    public void unlock() {
        sync.release(1);
    }

}

class TestMyLock {

    static int count = 0;
    static MyLock lock = new MyLock();

    public static void main(String[] args) throws InterruptedException {
        Runnable runnable = (() -> {
            lock.lock();
            try {
                for (int i = 0; i < 1000; i++) {
                    count++;
                }
            } finally {
                lock.unlock();
            }
        });
        Thread thread1 = new Thread(runnable);
        Thread thread2 = new Thread(runnable);
        Thread thread3 = new Thread(runnable);
        thread1.start();
        thread2.start();
        thread3.start();
        thread1.join();
        thread2.join();
        thread3.join();
        System.out.println(count);
    }

}