package com.sustav.resource;

import com.sustav.resource.exception.CommonPoolException;
import com.sustav.resource.model.DataSource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class ResourcePoolTest {

  @Test
  @DisplayName("The pool shall not allow any resource to be acquired unless the pool is open")
  void acquiredNotAllow_unlessPoolIsOpen() throws InterruptedException {
    ScheduledThreadPoolExecutor threadPoolExecutor = new ScheduledThreadPoolExecutor(10);
    threadPoolExecutor.prestartAllCoreThreads();
    CountDownLatch readySteadyGo = new CountDownLatch(1);

    IResourcePool<DataSource> pool = new ResourcePool<>();
    DataSource dataSource = new DataSource("test");

    threadPoolExecutor.submit(() -> {
      try {
        readySteadyGo.await();
        DataSource acquireRes = pool.acquire();
        System.out.println("Acquire: " + acquireRes);
      } catch (InterruptedException | CommonPoolException e) {
        System.out.println("Error acquire: " + dataSource);
      }
    });
    threadPoolExecutor.submit(() -> {
      try {
        readySteadyGo.await();
        DataSource acquireRes = pool.acquire();
        System.out.println("Acquire: " + acquireRes);
      } catch (InterruptedException | CommonPoolException e) {
        System.out.println("Error acquire: " + dataSource);
      }
    });
    threadPoolExecutor.submit(pool::open);
    threadPoolExecutor.submit(() -> {
      try {
        readySteadyGo.await();
        DataSource acquireRes = pool.acquire();
        System.out.println("Acquire: " + acquireRes);
      } catch (InterruptedException | CommonPoolException e) {
        System.out.println("Error acquire: " + dataSource);
      }
    });
    threadPoolExecutor.submit(() -> {
      try {
        readySteadyGo.await();
        pool.add(dataSource);
        System.out.println("Add: " + dataSource);
      } catch (InterruptedException e) {
        System.out.println("Error add: " + dataSource);
      }
    });

    readySteadyGo.countDown();

    threadPoolExecutor.shutdown();
    threadPoolExecutor.awaitTermination(2L, TimeUnit.SECONDS);
  }

  @org.testng.annotations.Test(threadPoolSize = 1, invocationCount = 10)
  void resourceCanAdd_releaseAnyTime() throws InterruptedException {
    IResourcePool<DataSource> pool = new ResourcePool<>();
    DataSource dataSource = new DataSource("test");
    pool.open();

    Thread add = new Thread(() -> {
      pool.add(dataSource);
      System.out.println("add");
    });
    Thread acquire = new Thread(() -> {
      try {
        pool.acquire();
        System.out.println("acquire");
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    });
    Thread release = new Thread(() -> {
      try {
        Thread.sleep(10);
        pool.release(dataSource);
        System.out.println("release");
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    });

    add.start();
    acquire.start();
    release.start();

    add.join();
    acquire.join();
    release.join();
  }

  @org.testng.annotations.Test(threadPoolSize = 1, invocationCount = 10)
  void acquireShouldBlock_untilResourceAvailable() throws InterruptedException {
    IResourcePool<DataSource> pool = new ResourcePool<>();
    DataSource dataSource = new DataSource("test");
    pool.open();

    Thread add = new Thread(() -> {
        pool.add(dataSource);
        System.out.println("add");
    });
    Thread acquire = new Thread(() -> {
      try {
        DataSource acquiredRes = pool.acquire();
        System.out.println("acquire: " + acquiredRes);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    });

    add.start();
    acquire.start();

    add.join();
    acquire.join();
  }

  @org.testng.annotations.Test(threadPoolSize = 1, invocationCount = 10)
  void acquireTimeoutShouldBlock_untilResourceAvailable() throws InterruptedException {
    IResourcePool<DataSource> pool = new ResourcePool<>();
    DataSource dataSource = new DataSource("test");
    pool.open();

    Thread add = new Thread(() -> {
      try {
        Thread.sleep(10);
        pool.add(dataSource);
        System.out.println("add");
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    });
    Thread acquireNull = new Thread(() -> {
      try {
        DataSource acquireRes = pool.acquire(4, TimeUnit.SECONDS);
        System.out.println("acquire: " + acquireRes);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    });
    Thread acquire = new Thread(() -> {
      try {
        DataSource acquireRes = pool.acquire(6, TimeUnit.SECONDS);
        System.out.println("acquire: " + acquireRes);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    });

    add.start();
    acquireNull.start();
    acquire.start();

    add.join();
    acquireNull.join();
    acquire.join();
  }

  @org.testng.annotations.Test(threadPoolSize = 1, invocationCount = 10)
  void addRemove_shouldReturnBoolean_dependsPollChanged() throws InterruptedException {
    IResourcePool<DataSource> pool = new ResourcePool<>();
    DataSource dataSource = new DataSource("test");
    pool.open();

    Thread addFalse = new Thread(() -> {
      try {
        Thread.sleep(10);
        boolean add = pool.add(dataSource);
        System.out.println("add false result: " + add);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    });
    Thread addTrue = new Thread(() -> {
      boolean add = pool.add(dataSource);
      System.out.println("add true result: " + add);
    });
    Thread removeTrue = new Thread(() -> {
      try {
        Thread.sleep(20);
        boolean remove = pool.removeNow(dataSource);
        System.out.println("remove true result: " + remove);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    });
    Thread removeFalse = new Thread(() -> {
      try {
        Thread.sleep(50);
        boolean remove = pool.removeNow(dataSource);
        System.out.println("remove false result: " + remove);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    });

    addFalse.start();
    addTrue.start();
    removeTrue.start();
    removeFalse.start();

    addFalse.join();
    addTrue.join();
    removeTrue.join();
    removeFalse.join();
  }

  @org.testng.annotations.Test(threadPoolSize = 1, invocationCount = 10)
  void whenClose_shouldWait_forReleaseResources() throws InterruptedException {
    IResourcePool<DataSource> pool = new ResourcePool<>();
    DataSource dataSource = new DataSource("test");
    DataSource dataSource2 = new DataSource("test2");
    pool.open();

    Thread add = new Thread(() -> {
      boolean addRes = pool.add(dataSource);
      System.out.println("add true result: " + addRes);
    });
    Thread add2 = new Thread(() -> {
      boolean addRes = pool.add(dataSource2);
      System.out.println("add true result: " + addRes);
    });
    Thread close = new Thread(() -> {
      try {
        Thread.sleep(20);
        System.out.println("waiting for closed");
        pool.close();
        System.out.println("closed");
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    });
    Thread acquire = new Thread(() -> {
      try {
        Thread.sleep(100);
        DataSource acquireRes = pool.acquire();
        System.out.println("acquire: " + acquireRes);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    });
    Thread acquire2 = new Thread(() -> {
      try {
        Thread.sleep(100);
        DataSource acquireRes = pool.acquire();
        System.out.println("acquire: " + acquireRes);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    });
    Thread release = new Thread(() -> {
      try {
        Thread.sleep(500);
        pool.release(dataSource);
        System.out.println("release");
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    });
    Thread release2 = new Thread(() -> {
      try {
        Thread.sleep(500);
        pool.release(dataSource2);
        System.out.println("release2");
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    });

    add.start();
    add2.start();
    close.start();
    acquire.start();
    acquire2.start();
    release.start();
    release2.start();

    add.join();
    add2.join();
    close.join();
    acquire.join();
    acquire2.join();
    release.join();
    release2.join();
  }

  @org.testng.annotations.Test(threadPoolSize = 1, invocationCount = 10)
  void whenCloseNow_shouldNotWait_forReleaseResources() throws InterruptedException {
    IResourcePool<DataSource> pool = new ResourcePool<>();
    DataSource dataSource = new DataSource("test");
    DataSource dataSource2 = new DataSource("test2");
    pool.open();

    Thread add = new Thread(() -> {
      boolean addRes = pool.add(dataSource);
      System.out.println("add true result: " + addRes);
    });
    Thread add2 = new Thread(() -> {
      boolean addRes = pool.add(dataSource2);
      System.out.println("add true result: " + addRes);
    });
    Thread close = new Thread(() -> {
      try {
        Thread.sleep(20);
        System.out.println("waiting for closed now");
        pool.closeNow();
        System.out.println("closed");
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    });
    Thread acquire = new Thread(() -> {
      try {
        Thread.sleep(100);
        DataSource acquireRes = pool.acquire();
        System.out.println("acquire: " + acquireRes);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    });
    Thread acquire2 = new Thread(() -> {
      try {
        Thread.sleep(100);
        DataSource acquireRes = pool.acquire();
        System.out.println("acquire: " + acquireRes);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    });
    Thread release = new Thread(() -> {
      try {
        Thread.sleep(500);
        pool.release(dataSource);
        System.out.println("release");
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    });
    Thread release2 = new Thread(() -> {
      try {
        Thread.sleep(500);
        pool.release(dataSource2);
        System.out.println("release2");
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    });

    add.start();
    add2.start();
    close.start();
    acquire.start();
    acquire2.start();
    release.start();
    release2.start();

    add.join();
    add2.join();
    close.join();
    acquire.join();
    acquire2.join();
    release.join();
    release2.join();
  }

  @org.testng.annotations.Test(threadPoolSize = 1, invocationCount = 10)
  void whenRemove_shouldWait_forAddAcquire() throws InterruptedException {
    IResourcePool<DataSource> pool = new ResourcePool<>();
    DataSource dataSource = new DataSource("test");
    pool.open();

    Thread remove = new Thread(() -> {
      try {
        pool.remove(dataSource);
        System.out.println("remove");
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    });
    Thread add = new Thread(() -> {
      pool.add(dataSource);
      System.out.println("add");
    });

    remove.start();
    add.start();

    remove.join();
    add.join();
  }

  @org.testng.annotations.Test(threadPoolSize = 1, invocationCount = 10)
  void testMainMethods() throws InterruptedException {
    IResourcePool<DataSource> pool = new ResourcePool<>();
    DataSource dataSource = new DataSource("test");
    pool.open();

    Thread remove = new Thread(() -> {
      pool.removeNow(dataSource);
    });
    Thread release = new Thread(() -> {
      pool.release(dataSource);
    });
    Thread add = new Thread(() -> {
      pool.add(dataSource);
    });
    Thread acquire = new Thread(() -> {
      try {
        pool.acquire(10, TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    });

    System.out.println("^^^^^^^^");
    add.start();
    release.start();
    remove.start();
    acquire.start();

    remove.join();
    release.join();
    add.join();
    acquire.join();
    System.out.println("VVVVVVVVV");
  }

}
