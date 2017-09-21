package naming.fs;

import java.util.concurrent.Semaphore;

/**
 * A POJO that represents any required information for each file to hold.
 * You can add more fields as we go.
 */
public class FileInfo {
  static final int REPLICATION_THRESHOLD = 20;
  String name; // file name or directory name (component in Path)
  boolean isDirectory;
  //private final ReentrantReadWriteLock rwl;
  private final MySemophore rwl;
  private boolean logOn;
  int writeCount; // todo update this when write lock is acquired
  int readCount;  // todo update this when read lock is acquired

  public FileInfo(boolean isDirectory) {
    this.isDirectory = isDirectory;

    this.logOn = false;
    //rwl = new ReentrantReadWriteLock();
    rwl = new MySemophore();
  }

  public boolean isDirectory() {
    return isDirectory;
  }

  public void fLock(String callerName, boolean isXLock) {

    if (isXLock) { // write lock

      log(callerName + " trying to get X lock...");
      //this.rwl.writeLock().lock();
      this.rwl.lockWrite();
      log(callerName + " got X lock!");

    } else { //

      log(callerName + " trying to get S lock...");
      this.rwl.lockRead();
      log(callerName + " got S lock!");
    }

  }

  public void fUnLock(String callerName, boolean isXLock) {

    if (isXLock) { // write lock

      //this.rwl.writeLock().unlock();
      this.rwl.unlockWrite();
      log(callerName + " released X lock...");

    } else {
      //this.rwl.readLock().unlock();
      this.rwl.unlockRead();
      log(callerName + " released S lock...");

    }
  }

  private void log(String msg) {
    if (this.logOn) {
      System.out.println("In FILE INFO: " + msg);
    }
  }

  synchronized public boolean incrementWriteCount() {
    writeCount++;
    if (writeCount == REPLICATION_THRESHOLD) {
      writeCount = 0;
      return true;
    }
    return false;
  }

  synchronized public boolean incrementReadCount() {
    readCount++;
    if (readCount == REPLICATION_THRESHOLD) {
      readCount = 0;
      return true;
    }
    return false;
  }

  public int getWriteCount() {
    return writeCount;
  }

  public synchronized void setWriteCount(int writeCount) {
    this.writeCount = writeCount;
  }

  public int getReadCount() {
    return readCount;
  }

  public synchronized void setReadCount(int readCount) {
    this.readCount = readCount;
  }
}


class MySemophore {

  final int PERMITS = 100;//Integer.MAX_VALUE;
  Semaphore semaphore = new Semaphore(PERMITS, true); //fairness FIFO

  public void lockRead() {
    try {
      semaphore.acquire(1);

    } catch (InterruptedException e) {
    }
  }

  public void unlockRead() {
    semaphore.release(1);
  }

  //writer lock and unlock
  public void lockWrite() {
    try {
      semaphore.acquire(PERMITS);
    } catch (InterruptedException e) {
      System.out.println("InterruptedException");
    }
  }

  public void unlockWrite() {
    semaphore.release(PERMITS);
  }
}
