package util.concurrent.locks;

import com.sun.corba.se.impl.orbutil.concurrent.Sync;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
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
            if (c == 0) {
                // CAS 成功，把当前线程设置为独占线程
                if (compareAndSetState(0, acquires)) {
                    setExclusiveOwnerThread(current);
                    return true;
                }
            }
            // 判断当前线程是不是独占线程
            else if (current == getExclusiveOwnerThread()) {
                int nextc = c + acquires;
                if (nextc < 0) // overflow
                    throw new Error("Maximum lock count exceeded");
                setState(nextc);
                return true;
            }
            return false;
        }

        @Override
        protected final boolean tryRelease(int releases) {
            int c = getState() - releases;
            // 当前线程不是占有 state 变量的线程，抛出异常
            if (Thread.currentThread() != getExclusiveOwnerThread()) {
                throw new IllegalMonitorStateException();
            }
            // state 操作后是否为 0
            boolean free = false;
            if (c == 0) {
                // 锁被释放
                free = true;
                setExclusiveOwnerThread(null);
            }
            setState(c);
            return free;
        }

    }

    //================================NonfairSync======================================

    static final class NonfairSync extends Sync {

        @Override
        void lock() {
            if (compareAndSetState(0, 1)) {
                setExclusiveOwnerThread(Thread.currentThread());
            } else {
                acquire(1);
            }
        }

        @Override
        protected final boolean tryAcquire(int acquires) {
            return nonfairTryAcquire(acquires);
        }

    }

    //================================FairSync==============================

    static final class FairSync extends Sync {

        @Override
        void lock() {
            acquire(1);
        }

        @Override
        protected final boolean tryAcquire(int acquires) {
            // 拿到当前线程
            final Thread current = Thread.currentThread();
            int c = getState();
            // 资源已经释放
            if (c == 0) {
                // 当前线程是队列中第一个线程并且获得资源成功
                if (!hasQueuedPredecessors() &&
                        compareAndSetState(0, acquires)) {
                    setExclusiveOwnerThread(current);
                    return true;
                }
            } else if (current == getExclusiveOwnerThread()) {
                // 当前线程重入
                int nextc = c + acquires;
                if (nextc < 0) {
                    throw new Error("Maximum lock count exceeded");
                }
                setState(nextc);
                return true;
            }
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
//        return false;
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        throw new UnsupportedOperationException();
//        return false;
    }

    @Override
    public void unlock() {
        sync.release(1);
    }

    @Override
    public Condition newCondition() {
        throw new UnsupportedOperationException();
//        return null;
    }
}
