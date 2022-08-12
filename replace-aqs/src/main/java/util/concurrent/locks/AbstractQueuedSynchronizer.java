package util.concurrent.locks;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.concurrent.locks.AbstractOwnableSynchronizer;
import java.util.concurrent.locks.LockSupport;

/**
 * @author wangguangwu
 */
public abstract class AbstractQueuedSynchronizer
        extends AbstractOwnableSynchronizer
        implements java.io.Serializable {

    private static final long serialVersionUID = 7373984972572414691L;

    protected AbstractQueuedSynchronizer() {
    }

    static final class Node {

        /**
         * 线程以共享的模式等待锁
         */
        static final Node SHARED = new Node();

        /**
         * 线程以独占的方式等待锁
         */
        static final Node EXCLUSIVE = null;

        /**
         * 表示线程获取锁的请求已经取消了
         */
        static final int CANCELED = 1;

        /**
         * 线程已经准备好，等待资源释放
         */
        static final int SIGNAL = -1;

        /**
         * 节点在等待队列中，节点线程等待唤醒
         */
        static final int CONDITION = -2;

        /**
         * 当前线程处于 SHARED 模式下
         */
        static final int PROPAGATE = -3;

        /**
         * 当前节点在队列中的状态
         */
        volatile int waitStatus;

        volatile Node prev;

        volatile Node next;

        volatile Thread thread;

        Node nextWaiter;

        final boolean isShared() {
            return nextWaiter == SHARED;
        }

        final Node predecessor() throws NullPointerException {
            Node p = prev;
            if (p == null) {
                throw new NullPointerException();
            } else {
                return p;
            }
        }

        Node() {

        }

        Node(Thread thread, Node mode) {
            this.nextWaiter = mode;
            this.thread = thread;
        }

        Node(Thread thread, int waitStatus) {
            this.waitStatus = waitStatus;
            this.thread = thread;
        }
    }

    private transient volatile Node head;

    private transient volatile Node tail;


    private static final Unsafe unsafe = AbstractQueuedSynchronizer.reflectGetUnsafe();

    /**
     * 通过反射拿到 unsafe
     * <p>
     * Unsafe.getUnsafe 方法会校验是否是引导类加载，如果不是，就抛出 unsafe 异常
     *
     * @return unsafe
     */
    private static Unsafe reflectGetUnsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return null;
        }
    }


    private static final long stateOffset;
    private static final long headOffset;
    private static final long tailOffset;
    private static final long waitStatusOffset;

    static {
        try {
            stateOffset = unsafe.objectFieldOffset
                    (AbstractQueuedSynchronizer.class.getDeclaredField("state"));
            headOffset = unsafe.objectFieldOffset
                    (AbstractQueuedSynchronizer.class.getDeclaredField("head"));
            tailOffset = unsafe.objectFieldOffset
                    (AbstractQueuedSynchronizer.class.getDeclaredField("tail"));
            waitStatusOffset = unsafe.objectFieldOffset
                    (Node.class.getDeclaredField("waitStatus"));
        } catch (Exception ex) {
            throw new Error(ex);
        }
    }

    /**
     * 同步器状态
     */
    private volatile int state;

    protected final int getState() {
        return state;
    }

    protected final void setState(int newState) {
        state = newState;
    }

    protected final boolean compareAndSetState(int expect, int update) {
        return unsafe.compareAndSwapInt(this, stateOffset, expect, update);
    }


    abstract boolean tryRelease(int releases);

    abstract boolean tryAcquire(int acquires);

    public final void acquire(int arg) {
        if (!tryAcquire(arg) &&
                acquireQueued(addWaiter(Node.EXCLUSIVE), arg)) {
            selfInterrupt();
        }
    }

    final boolean acquireQueued(final Node node, int arg) {
        boolean failed = true;
        try {
            boolean interrupted = false;
            for (; ; ) {
                // 拿到当前节点的前一个节点
                final Node p = node.predecessor();
                // 如果当前节点是除了头节点外的第一个节点
                // 并且尝试获取锁成功
                if (p == head && tryAcquire(arg)) {
                    // 把当前节点设置为头节点
                    setHead(node);
                    // help GC
                    p.next = null;
                    failed = false;
                    return interrupted;
                }
                if (shouldParkAfterFailedAcquire(p, node) &&
                        parkAndCheckInterrupt()) {
                    interrupted = true;
                }
            }
        } finally {
            if (failed) {
                cancelAcquire(node);
            }
        }
    }

    private void cancelAcquire(Node node) {
        throw new UnsupportedOperationException();
    }

    private final boolean parkAndCheckInterrupt() {
        LockSupport.park(this);
        return Thread.interrupted();
    }

    private boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
        int ws = pred.waitStatus;
        if (ws == Node.SIGNAL) {
            return true;
        }
        if (ws > 0) {
            do {
                node.prev = pred = pred.prev;
            } while (pred.waitStatus > 0);
            pred.next = node;
        } else {
            compareAndSetWaitStatus(pred, ws, Node.SIGNAL);
        }

        return false;
    }

    private static boolean compareAndSetWaitStatus(Node node,
                                                   int expect,
                                                   int update) {
        return unsafe.compareAndSwapInt(node, waitStatusOffset, expect, update);
    }

    private Node addWaiter(Node mode) {
        // 把当前线程封装为一个节点
        Node node = new Node(Thread.currentThread(), mode);
        Node pred = tail;
        // 如果尾节点不为 null，就把新建立的节点放在尾部
        if (pred != null) {
            node.prev = pred;
            if (compareAndSetTail(pred, node)) {
                pred.next = node;
                return node;
            }
        }
        // 这个方法的目的
        enq(node);
        return node;
    }

    private void setHead(Node node) {
        head = node;
        node.thread = null;
        node.prev = null;
    }

    /**
     * 将节点插入队列
     *
     * @param node
     */
    private Node enq(Node node) {
        for (; ; ) {
            Node t = tail;
            if (t == null) {
                // 初始化节点
                if (compareAndSetHead(new Node())) {
                    tail = head;
                } else {
                    node.prev = t;
                    if (compareAndSetTail(t, node)) {
                        t.next = node;
                        return t;
                    }
                }
            }
        }
    }

    private boolean compareAndSetHead(Node update) {
        return unsafe.compareAndSwapObject(this, headOffset, null, update);
    }

    private boolean compareAndSetTail(Node expect, Node update) {
        return unsafe.compareAndSwapObject(this, tailOffset, expect, update);
    }


    static void selfInterrupt() {
        // 当前线程中断
        Thread.currentThread().interrupt();
    }

    public final boolean hasQueuedPredecessors() {
        throw new UnsupportedOperationException();
    }

    public final boolean release(int arg) {
        if (tryRelease(arg)) {
            Node h = head;
            if (h != null && h.waitStatus != 0) {
                unparkSuccessor(h);
                return true;
            }
        }
        return false;
    }

    /**
     * 唤醒后面的节点
     */
    private void unparkSuccessor(Node node) {
        int ws = node.waitStatus;
        if (ws < 0) {
            compareAndSetWaitStatus(node, ws, 0);
        }
        Node s = node.next;
        if (s == null || s.waitStatus > 0) {
            s = null;
            for (Node t = tail; t != null && t != node; t = t.prev) {
                if (t.waitStatus <= 0) {
                    s = t;
                }
            }
        }
        if (s != null) {
            LockSupport.unpark(s.thread);
        }
    }

}
