package com.wangguangwu.test;

//import java.util.concurrent.locks.ReentrantLock;

import util.concurrent.locks.ReentrantLock;

/**
 * @author wangguangwu
 */
public class TestR {

    private static final ReentrantLock LOCK = new ReentrantLock();

    public static void main(String[] args) {
        int times = 10;
        for (int i = 0; i < times; i++) {
            Thread thread = new Thread(() -> {
                LOCK.lock();
                try {
                    Thread.sleep(1000);
                    System.out.println("Hello World");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    LOCK.unlock();
                }
            });
            thread.start();
        }
        System.out.println("Hello wangguangwu");
    }

}
