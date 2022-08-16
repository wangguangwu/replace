package com.wangguangwu.test;

import util.concurrent.locks.ReentrantLock;

import java.util.concurrent.TimeUnit;

/**
 * @author wangguangwu
 */
public class TestReentrantLock {

    /**
     * 定义一个 ReentrantLock
     * 默认是非公平锁
     */
    private static final ReentrantLock LOCK = new ReentrantLock();

    public static void main(String[] args) {
        int times = 5;
        for (int i = 0; i < times; i++) {
            Thread thread = new Thread(() -> {
//                System.out.println(Thread.currentThread() + "-lock");
                LOCK.lock();
                try {
//                    System.out.println(Thread.currentThread() + "-execute");
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
//                    System.out.println(Thread.currentThread() + "-release");
                    LOCK.unlock();
                }
            });
            thread.start();
        }
    }

}
