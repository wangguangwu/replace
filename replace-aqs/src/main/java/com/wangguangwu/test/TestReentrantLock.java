package com.wangguangwu.test;

//import java.util.concurrent.locks.ReentrantLock;

import util.concurrent.locks.ReentrantLock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
        int times = 10;
        for (int i = 0; i < times; i++) {
            Thread thread = new Thread(() -> {
                LOCK.lock();
                try {
                    TimeUnit.SECONDS.sleep(1);
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
