package util.concurrent.locks;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

//import java.util.concurrent.locks.AbstractQueuedSynchronizer;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * @author wangguangwu
 */
public class ReentrantLock implements Lock, java.io.Serializable {

    private final Sync sync;

    public ReentrantLock() {
        sync = new NonfairSync();
    }

    public ReentrantLock(boolean isFair) {
        sync = isFair ? new FairSync() : new NonfairSync();
    }

    //========================sync 类==================================

    abstract static class Sync extends AbstractQueuedSynchronizer {

        private static final long serialVersionUID = -5179523762034025860L;

        abstract void lock();

        final boolean nonfairTryAcquire(int acquires) {
            // 拿到当前线程
            final Thread current = Thread.currentThread();
            // 拿到 state 变量的值
            int c = getState();
            // 非公平锁，只要锁是空闲的
            // 就直接尝试调用 CAS 方法获取锁，而不用判断自己是否需要排队
            if (c == 0) {
                if (compareAndSetState(0, acquires)) {
                    // 成功，则设置自己为持有锁的线程
                    setExclusiveOwnerThread(current);
                    return true;
                }
            } else if (current == getExclusiveOwnerThread()) {
                // 锁不空闲，如果是当前线程持有锁
                int nextc = c + acquires;
                if (nextc < 0) { // overflow
                    throw new Error("Maximum lock count exceeded");
                }
                // 持有锁，则重入
                setState(nextc);
                return true;
            }
            // 获取锁失败，返回 false
            return false;
        }

        @Override
        protected final boolean tryRelease(int releases) {
            // 获取同步变量 state 的值
            int c = getState() - releases;
            if (Thread.currentThread() != getExclusiveOwnerThread()) {
                // 只允许持有线程释放锁
                throw new IllegalMonitorStateException();
            }
            boolean free = false;
            if (c == 0) {
                // 锁被释放
                free = true;
                setExclusiveOwnerThread(null);
            }
            // 设置同步变量 state 的值
            setState(c);
            return free;
        }

    }

    //================================NonfairSync======================================

    static final class NonfairSync extends Sync {

        @Override
        void lock() {
            // 尝试获取锁
            // java.util.concurrent.locks.AbstractQueuedSynchronizer#compareAndSetState
            if (compareAndSetState(0, 1)) {
                // 获取锁成功
                // 将当前线程设置为独占线程
                setExclusiveOwnerThread(Thread.currentThread());
            } else {
                // 获得锁失败，进入 acquire 方法进行后续处理
                // java.util.concurrent.locks.AbstractQueuedSynchronizer#acquire 方法
                acquire(1);
            }
        }

        @Override
        protected final boolean tryAcquire(int acquires) {
            // java.util.concurrent.locks.ReentrantLock.Sync#nonfairTryAcquire
            return nonfairTryAcquire(acquires);
        }

    }

    //================================FairSync==============================

    static final class FairSync extends Sync {

        @Override
        void lock() {
            // java.util.concurrent.locks.AbstractQueuedSynchronizer#acquire 方法
            acquire(1);
        }

        @Override
        protected final boolean tryAcquire(int acquires) {
            // 拿到当前线程
            final Thread current = Thread.currentThread();
            // 拿到 AQS 中 state 的值
            // java.util.concurrent.locks.AbstractQueuedSynchronizer#getState
            int c = getState();
            // 判断资源是否空闲
            if (c == 0) {
                // 判断自己是否需要排队
                // java.util.concurrent.locks.AbstractQueuedSynchronizer#hasQueuedPredecessors
                if (!hasQueuedPredecessors() &&
                        // 尝试获得锁
                        compareAndSetState(0, acquires)) {
                    // 成功，则设置自己为持有锁的线程
                    setExclusiveOwnerThread(current);
                    // 加锁成功，返回 true
                    return true;
                }
            } else if (current == getExclusiveOwnerThread()) {
                // 锁不空闲，是自己是否持有锁
                int nextc = c + acquires;
                // 判断是否超出重入次数
                if (nextc < 0) {
                    throw new Error("Maximum lock count exceeded");
                }
                // 持有锁则重入
                setState(nextc);
                // 加锁成功，返回 true
                return true;
            }
            // 加锁失败，返回 false
            return false;
        }
    }


    //=========================实现了 lock 接口================================

    @Override
    public void lock() {
        sync.lock();
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean tryLock() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void unlock() {
        sync.release(1);
    }

    @Override
    public Condition newCondition() {
        throw new UnsupportedOperationException();
    }
}
