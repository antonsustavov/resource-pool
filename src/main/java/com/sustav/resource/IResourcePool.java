package com.sustav.resource;

import java.util.concurrent.TimeUnit;

public interface IResourcePool<R> {

  void open();
  boolean isOpen();
  boolean add(R resource);
  boolean remove(R resource) throws InterruptedException;
  boolean removeNow(R resource);
  void release(R resource);
  R acquire() throws InterruptedException;
  R acquire(long timeout, TimeUnit timeUnit) throws InterruptedException;
  void close() throws InterruptedException;
  void closeNow();

}
