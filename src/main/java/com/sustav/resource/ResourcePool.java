package com.sustav.resource;

import static com.sustav.resource.exception.ErrorCode.POOL_IS_CLOSED;

import com.sustav.resource.exception.CommonPoolException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ResourcePool<R> implements IResourcePool<R> {
  private final AtomicBoolean isOpen = new AtomicBoolean(false);
  private final Set<R> resources = ConcurrentHashMap.newKeySet();
  private final BlockingQueue<R> pool;
  private final BlockingQueue<R> inUse;

  private final Map<R, Lock> resourceLocks = new ConcurrentHashMap<>();
  private final Map<R, Condition> notInUse = new ConcurrentHashMap<>();
  private final Map<R, Condition> notInRelease = new ConcurrentHashMap<>();
  private final Map<R, Condition> notAvailable = new ConcurrentHashMap<>();

  private final Lock emptyPoolLock = new ReentrantLock();
  private final Condition notEmptyCondition = emptyPoolLock.newCondition();

  public ResourcePool() {
    this.pool = new LinkedBlockingQueue<>();
    this.inUse = new LinkedBlockingQueue<>();
  }

  public ResourcePool(BlockingQueue<R> pool, BlockingQueue<R> inUse) {
    this.pool = pool;
    this.inUse = inUse;
  }

  @Override
  public void open() {
    isOpen.set(true);
  }

  @Override
  public boolean isOpen() {
    return isOpen.get();
  }

  @Override
  public synchronized boolean add(R resource) {
    emptyPoolLock.lock();
    try {
      if (resources.add(resource)) {
        ReentrantLock reentrantLock = new ReentrantLock();
        Condition notInUseCondition = reentrantLock.newCondition();
        Condition notInReleaseCondition = reentrantLock.newCondition();
        Condition notAvailableCondition = reentrantLock.newCondition();
        resourceLocks.put(resource, reentrantLock);
        notInUse.put(resource, notInUseCondition);
        notInRelease.put(resource, notInReleaseCondition);
        notAvailable.put(resource, notAvailableCondition);
        notEmptyCondition.signalAll();
        return pool.offer(resource);
      }
      return false;
    } finally {
      emptyPoolLock.unlock();
    }
  }

  @Override
  public boolean remove(R resource) throws InterruptedException {
    if (!resources.contains(resource)) {
      return false;
    }
    Lock resourceLock = getLock(resource);
    Condition notInUseResourceCondition = notInUse.get(resource);
    Condition notInReleaseResourceCondition = notInRelease.get(resource);
    resourceLock.lock();
    try {
      while (inUse.contains(resource)) {
        notInUseResourceCondition.await();
      }
      resources.remove(resource);
      boolean removed = pool.remove(resource);

      notInReleaseResourceCondition.signalAll();
      return removed;
    } finally {
      resourceLock.unlock();
    }
  }

  @Override
  public void release(R resource) {
    if (!resources.contains(resource)) {
      return;
    }
    Lock resourceLock = getLock(resource);
    Condition notInUseResourceCondition = notInUse.get(resource);
    Condition notInReleaseResourceCondition = notInRelease.get(resource);
    resourceLock.lock();
    emptyPoolLock.lock();
    try {
      if (inUse.contains(resource)) {
        inUse.remove(resource);
        pool.offer(resource);
        notEmptyCondition.signalAll();
      }
      notInUseResourceCondition.signalAll();
    } finally {
      emptyPoolLock.unlock();
      resourceLock.unlock();
    }
  }

  @Override
  public R acquire() throws InterruptedException {
    if (!isOpen.get()) {
      throw CommonPoolException.from(POOL_IS_CLOSED);
    }
    emptyPoolLock.lock();
    try {
      while (pool.isEmpty()) {
        notEmptyCondition.await();
      }
      return pool.remove();
    } finally {
      emptyPoolLock.unlock();
    }
  }

  @Override
  public R acquire(long timeout, TimeUnit timeUnit) throws InterruptedException {
    if (!isOpen.get()) {
      throw CommonPoolException.from(POOL_IS_CLOSED);
    }
    long nanos = timeUnit.toNanos(timeout);
    emptyPoolLock.lock();
    try {
      while (pool.isEmpty()) {
        if (nanos <= 0L) {
          return null;
        }
        nanos = notEmptyCondition.awaitNanos(nanos);
      }
      return pool.remove();
    } finally {
      emptyPoolLock.unlock();
    }
  }

  @Override
  public boolean removeNow(R resource) {
    if (!resources.contains(resource)) {
      return false;
    }
    resources.remove(resource);
    return pool.remove(resource);
  }

  @Override
  public void close() throws InterruptedException {
    isOpen.set(false);
    emptyPoolLock.lock();
    try {
      while (!pool.containsAll(resources)) {
        notEmptyCondition.await();
      }
    } finally {
      emptyPoolLock.unlock();
    }
  }

  @Override
  public void closeNow() {
    isOpen.set(false);
    pool.clear();
    inUse.clear();
    resources.clear();
    resourceLocks.clear();
    notInUse.clear();
    notInRelease.clear();
    notAvailable.clear();
  }

  private synchronized Lock getLock(R resource) {
    return resourceLocks.get(resource);
  }

}
