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
        static final int CANCELLED = 1;

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

        /**
         * 前驱指针
         */
        volatile Node prev;

        /**
         * 后继指针
         */
        volatile Node next;

        /**
         * 处于该节点的线程
         */
        volatile Thread thread;

        /**
         * 指向下一个处于 CONDITION 状态的节点
         */
        Node nextWaiter;


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
    private static final long nextOffset;

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
            nextOffset = unsafe.objectFieldOffset
                    (Node.class.getDeclaredField("next"));
        } catch (Exception ex) {
            throw new Error(ex);
        }
    }

    /**
     * 当前临界资源的获锁情况
     */
    private volatile int state;

    /**
     * 获取 state 的值
     *
     * @return state
     */
    protected final int getState() {
        return state;
    }

    /**
     * 设置 state 的值
     *
     * @param newState 新的 state 的值
     */
    protected final void setState(int newState) {
        state = newState;
    }

    /**
     * 使用 CAS 方式更新 state
     *
     * @param expect 期望的状态
     * @param update 更新后的状态
     * @return boolean
     */
    protected final boolean compareAndSetState(int expect, int update) {
        // See below for intrinsics setup to support this
        return unsafe.compareAndSwapInt(this, stateOffset, expect, update);
    }

    static final long spinForTimeoutThreshold = 1000L;

    /**
     * 将节点插入队列
     *
     * @param node
     */
    private Node enq(Node node) {
        // for 循环中操作，保证入队成功
        for (; ; ) {
            Node t = tail;
            if (t == null) {
                // 尾节点为 null，说明是第一次入队，当前队列为空
                // 先创建一个头节点
                if (compareAndSetHead(new Node())) {
                    // 将头节点赋值给尾节点
                    tail = head;
                }
            } else {
                // 不是第一次入队，队列中至少有一个节点
                // 将当前节点加到队列的尾部
                node.prev = t;
                if (compareAndSetTail(t, node)) {
                    t.next = node;
                    return t;
                }
            }
        }
    }

    private Node addWaiter(Node mode) {
        // 把当前线程包装为一个节点
        Node node = new Node(Thread.currentThread(), mode);
        // Try the fast path of enq; backup to full enq on failure
        Node pred = tail;
        if (pred != null) {
            // 尾节点不为 null，说明链表中有数据
            node.prev = pred;
            // 执行一次快速入队操作
            if (compareAndSetTail(pred, node)) {
                pred.next = node;
                return node;
            }
        }
        // this.enq
        // 队列为空，获取上面的快速入队操作失败
        // 将节点插到队列中
        enq(node);
        return node;
    }

    private void setHead(Node node) {
        // 将当前节点设置为虚节点，但是不修改 waitStatus，因为后续还需要使用
        head = node;
        node.thread = null;
        node.prev = null;
    }

    /**
     * 唤醒后面的节点
     */
    private void unparkSuccessor(Node node) {
        // 拿到当前节点的 waitStatus
        int ws = node.waitStatus;
        if (ws < 0) {
            // 如果当前节点的 waitStatus 小于 0，即在独占锁场景下处于等待资源释放、等待唤醒的情况，将节点的 waitStatus 更新为 0
            compareAndSetWaitStatus(node, ws, 0);
        }
        // 当前节点的下一个节点
        Node s = node.next;
        // 如果为空或者被取消
        if (s == null || s.waitStatus > 0) {
            s = null;
            // 从队列尾部向前遍历找到最前面的一个 waitStatus <= 0 的节点
            for (Node t = tail; t != null && t != node; t = t.prev) {
                if (t.waitStatus <= 0) {
                    s = t;
                }
            }
        }
        if (s != null) {
            // 唤醒节点，但并不表示它持有锁，要从阻塞的地方开始执行
            LockSupport.unpark(s.thread);
        }
    }

    static void selfInterrupt() {
        // 当前线程中断
        Thread.currentThread().interrupt();
    }

    final boolean acquireQueued(final Node node, int arg) {
        // 标记是否成功拿到资源
        boolean failed = true;
        try {
            // 标记等待过程中是否中断过
            boolean interrupted = false;
            // 开始自旋，要么获取锁，要么中断
            for (;;) {
                // 获取当前节点的前驱节点
                final Node p = node.predecessor();
                // 如果 p 是头节点，说明当前节点是队列中第一个有真实线程的节点
                // 尝试获取锁
                if (p == head && tryAcquire(arg)) {
                    // 获取锁成功，头指针移动到当前 node
                    setHead(node);
                    p.next = null; // help GC
                    failed = false;
                    return interrupted;
                }
                // 两种情况
                // 1. P 不为头节点
                // 2. p 为头节点但是获取锁失败（可能是非公平锁）
                // 此时判断当前 node 是否要被阻塞（被阻塞条件：当前线程的前驱节点的 waitStatus = -1），防止无限循环浪费资源
                if (shouldParkAfterFailedAcquire(p, node) &&
                        // 挂起当前线程
                        parkAndCheckInterrupt())
                    interrupted = true;
            }
        } finally {
            if (failed)
                // 将 node 节点的状态标记为 CANCELED
                cancelAcquire(node);
        }
    }

    // 处理异常退出的 node
    private void cancelAcquire(Node node) {
        if (node == null)
            return;

        // 设置该节点不再关联任何线程
        node.thread = null;

        // 跳过 CANCELLED 节点，找到一个有效的前继节点
        Node pred = node.prev;
        while (pred.waitStatus > 0)
            node.prev = pred = pred.prev;

        // 获取过滤后的有效节点的后继节点
        Node predNext = pred.next;

        // 设置状态为取消
        node.waitStatus = Node.CANCELLED;

        // 当前节点是为尾节点
        if (node == tail && compareAndSetTail(node, pred)) {
            compareAndSetNext(pred, predNext, null);
        } else {

            int ws;
            if (pred != head &&
                    ((ws = pred.waitStatus) == Node.SIGNAL ||
                            (ws <= 0 && compareAndSetWaitStatus(pred, ws, Node.SIGNAL))) &&
                    pred.thread != null) {
                Node next = node.next;
                if (next != null && next.waitStatus <= 0)
                    compareAndSetNext(pred, predNext, next);
            } else {
                unparkSuccessor(node);
            }

            node.next = node; // help GC
        }
    }

    protected boolean tryAcquire(int arg) {
        throw new UnsupportedOperationException();
    }

    protected boolean tryRelease(int arg) {
        throw new UnsupportedOperationException();
    }

    public final void acquire(int arg) {
        if (!tryAcquire(arg) &&
                acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
            selfInterrupt();
    }

    private final boolean parkAndCheckInterrupt() {
        LockSupport.park(this);
        return Thread.interrupted();
    }

    // 通过前驱节点判断当前线程是否应该被阻塞
    private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
        // 获取前驱节点的节点状态
        int ws = pred.waitStatus;
        // 如果前驱节点处于唤醒状态，说明当前节点应该被阻塞
        if (ws == Node.SIGNAL)
            return true;
        if (ws > 0) {
            // ws > 0，说明是取消状态
            do {
                // 循环向前找到取消节点，把取消节点从队列中删除
                node.prev = pred = pred.prev;
            } while (pred.waitStatus > 0);
            pred.next = node;
        } else {
            // 设置前驱节点等待状态为 SIGNAL
            // 代表释放锁的时候需要唤醒后面的线程
            compareAndSetWaitStatus(pred, ws, Node.SIGNAL);
        }
        // 当前节点不该被阻塞
        return false;
    }

    private static boolean compareAndSetWaitStatus(Node node,
                                                   int expect,
                                                   int update) {
        return unsafe.compareAndSwapInt(node, waitStatusOffset, expect, update);
    }


    private boolean compareAndSetHead(Node update) {
        return unsafe.compareAndSwapObject(this, headOffset, null, update);
    }

    private boolean compareAndSetTail(Node expect, Node update) {
        return unsafe.compareAndSwapObject(this, tailOffset, expect, update);
    }

    public final boolean hasQueuedPredecessors() {
        Node t = tail; // Read fields in reverse initialization order
        Node h = head;
        Node s;
        return h != t &&
                ((s = h.next) == null || s.thread != Thread.currentThread());
    }

    public final boolean release(int arg) {
        // java.util.concurrent.locks.ReentrantLock.Sync#tryRelease
        // tryRelease 中具体的解锁逻辑，需要子类去实现
        // 当 state = 0，表示锁被释放，tryRelease 方法返回 true，此时需要唤醒阻塞对流中的线程
        if (tryRelease(arg)) {
            Node h = head;
            // h != null，说明同步队列中有数据
            // h.waitStatus != 0，分为两种情况
            // h.waitStatus < 0，需要唤醒下一个线程
            // h.waitStatus > 0，说明头节点因为发生异常被设置为取消
            if (h != null && h.waitStatus != 0) {
                // 唤醒后继节点
                unparkSuccessor(h);
                // 释放成功，返回 true
                return true;
            }
        }
        // 释放失败，返回 false
        return false;
    }

    /**
     * CAS next field of a node.
     */
    private static final boolean compareAndSetNext(Node node,
                                                   Node expect,
                                                   Node update) {
        return unsafe.compareAndSwapObject(node, nextOffset, expect, update);
    }

    public final Thread getFirstQueuedThread() {
        // handle only fast path, else relay
        return (head == tail) ? null : fullGetFirstQueuedThread();
    }

    /**
     * Version of getFirstQueuedThread called when fastpath fails
     */
    private Thread fullGetFirstQueuedThread() {
        /*
         * The first node is normally head.next. Try to get its
         * thread field, ensuring consistent reads: If thread
         * field is nulled out or s.prev is no longer head, then
         * some other thread(s) concurrently performed setHead in
         * between some of our reads. We try this twice before
         * resorting to traversal.
         */
       Node h, s;
        Thread st;
        if (((h = head) != null && (s = h.next) != null &&
                s.prev == head && (st = s.thread) != null) ||
                ((h = head) != null && (s = h.next) != null &&
                        s.prev == head && (st = s.thread) != null))
            return st;

        /*
         * Head's next field might not have been set yet, or may have
         * been unset after setHead. So we must check to see if tail
         * is actually first node. If not, we continue on, safely
         * traversing from tail back to head to find first,
         * guaranteeing termination.
         */

        Node t = tail;
        Thread firstThread = null;
        while (t != null && t != head) {
            Thread tt = t.thread;
            if (tt != null)
                firstThread = tt;
            t = t.prev;
        }
        return firstThread;
    }


}
