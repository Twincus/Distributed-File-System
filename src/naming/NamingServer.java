package naming;

import common.Path;
import naming.fs.FileInfo;
import naming.fs.FileSystem;
import rmi.Helper;
import rmi.RMIException;
import rmi.Skeleton;
import storage.Command;
import storage.Storage;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static common.Path.getIncrementalPaths;


/**
 * Naming server.
 * <p>
 * <p>
 * Each instance of the filesystem is centered on a single naming server. The
 * naming server maintains the filesystem directory tree. It does not store any
 * file data - this is done by separate storage servers. The primary purpose of
 * the naming server is to map each file name (path) to the storage server
 * which hosts the file's contents.
 * <p>
 * <p>
 * The naming server provides two interfaces, <code>Service</code> and
 * <code>Registration</code>, which are accessible through RMI. Storage servers
 * use the <code>Registration</code> interface to inform the naming server of
 * their existence. Clients use the <code>Service</code> interface to perform
 * most filesystem operations. The documentation accompanying these interfaces
 * provides details on the methods supported.
 * <p>
 * <p>
 * Stubs for accessing the naming server must typically be created by directly
 * specifying the remote network address. To make this possible, the client and
 * registration interfaces are available at well-known ports defined in
 * <code>NamingStubs</code>.
 */
public class NamingServer implements Service, Registration {
  FileSystem fs;

  /**
   * Note: it only stores a path if it corresponds to a file because the storage
   * server only hosts file.
   */
  StorageServerStore ssStore;

  Skeleton<Service> serviceInterfaceSkeleton;
  Skeleton<Registration> registrationInterfaceSkeleton;

  boolean logOn;

  /**
   * Creates the naming server object.
   * <p>
   * <p>
   * The naming server is not started.
   */
  public NamingServer() {

    this.logOn = false;
    Helper.log("NamingServer() constructor invoked");
    fs = new FileSystem();
    Helper.log("FileSystem field created");
    ssStore = new StorageServerStore();
    Helper.log("StorageServerStore field created");

    InetSocketAddress serviceInterfaceAddress = new InetSocketAddress("127.0.0.1", NamingStubs.SERVICE_PORT);
    InetSocketAddress registrationInterfaceAddress = new InetSocketAddress("127.0.0.1", NamingStubs.REGISTRATION_PORT);
    try {
      this.serviceInterfaceSkeleton = new Skeleton<>(Service.class, this, serviceInterfaceAddress);
      this.registrationInterfaceSkeleton = new Skeleton<>(Registration.class, this, registrationInterfaceAddress);

    } catch (NullPointerException | Error e) {
      Helper.log("When trying to initialize Skeletons, NullPointerException or Error happened");
      e.printStackTrace();
    }

    Path root = new Path();
    try {
      createDirectory(root);
    } catch (FileNotFoundException e) {
      System.out.println("THIS SHOULD NEVER HAPPEN.");
      e.printStackTrace();
    }
  }

  /**
   * Starts the naming server.
   * <p>
   * <p>
   * After this method is called, it is possible to access the client and
   * registration interfaces of the naming server remotely.
   *
   * @throws RMIException If either of the two skeletons, for the client or
   *                      registration server interfaces, could not be
   *                      started. The user should not attempt to start the
   *                      server again if an exception occurs.
   */
  public synchronized void start() throws RMIException {
    //throw new UnsupportedOperationException("not implemented");
    try {
      this.registrationInterfaceSkeleton.start();

    } catch (RMIException e) {
      Helper.log("RMIException thrown when trying to start registrationInterfaceSkeleton");
    }

    try {
      this.serviceInterfaceSkeleton.start();

    } catch (RMIException e) {
      Helper.log("RMIException thrown when trying to start serviceInterfaceSkeleton");
    }

  }

  /**
   * Stops the naming server.
   * <p>
   * <p>
   * This method commands both the client and registration interface
   * skeletons to stop. It attempts to interrupt as many of the threads that
   * are executing naming server code as possible. After this method is
   * called, the naming server is no longer accessible remotely. The naming
   * server should not be restarted.
   */
  public void stop() {
    try {

      this.serviceInterfaceSkeleton.stop();
      Helper.log("serviceInterfaceSkeleton stopped()");
      this.registrationInterfaceSkeleton.stop();
      Helper.log("registrationInterfaceSkeleton stopped()");
    } catch (Throwable t) {
      stopped(t);
      return;
    }
    //server shut down correctly
    stopped(null);

  }

  /**
   * Indicates that the server has completely shut down.
   * <p>
   * <p>
   * This method should be overridden for error reporting and application
   * exit purposes. The default implementation does nothing.
   *
   * @param cause The cause for the shutdown, or <code>null</code> if the
   *              shutdown was by explicit user request.
   */
  protected void stopped(Throwable cause) {
  }

  /**
   * Locks a file or directory for either shared or exclusive access.
   * <p>
   * <p>
   * An object locked for <em>exclusive</em> access cannot be locked by any
   * other user until the exclusive lock is released. An object should be
   * locked for exclusive access when operations performed by the user will
   * change the object's state.
   * <p>
   * <p>
   * An object locked for <em>shared</em> access can be locked by other users
   * for shared access at the same time, but cannot be simultaneously locked
   * by users requesting exclusive access. This kind of lock should be
   * obtained when the object's state will be consulted, but not modified,
   * and to prevent the object from being modified by another user.
   * <p>
   * <p>
   * Wherever there is a requirement that an object be locked for shared
   * access, it is acceptable to lock the object for exclusive access
   * instead: exclusive access is more "safe" than shared access. However, it
   * is best to avoid this unless absolutely necessary, to permit as many
   * users simultaneous access to the object as safely possible.
   * <p>
   * <p>
   * Locking a file for shared access is considered by the naming server to
   * be a read request, and may cause the file to be replicated. Locking a
   * file for exclusive access is considered to be a write request, and
   * causes all copies of the file but one to be deleted. This latter process
   * is called invalidation. The naming server must treat lock actions as
   * read or write requests because it cannot monitor the true read and write
   * requests - those go to the storage servers.
   * <p>
   * <p>
   * When any object is locked for either kind of access, all objects along
   * the path up to, but not including, the object itself, are locked for
   * shared access to prevent their modification or deletion by other users.
   * For example, if one user locks <code>/etc/scripts/startup.sh</code> for
   * exclusive access in order to write to it, then <code>/</code>,
   * <code>/etc</code>, <code>/etc/scripts</code> will all be locked for
   * shared access to prevent other users from, say, deleting them.
   * <p>
   * <p>
   * An object can be considered to be <em>effectively locked</em> for
   * exclusive access if one of the directories on the path to it is already
   * locked for exclusive access: this is because no user will be able to
   * obtain any kind of lock on the object until the exclusive lock on the
   * directory is released. This is a direct consequence of the locking order
   * described in the previous paragraph. As a result, if a directory is
   * locked for exclusive access, the entire subtree under that directory can
   * also be considered to be locked for exclusive access. If a client takes
   * advantage of this fact to lock a directory and then perform several
   * accesses to the files under it, it should take care not to access files
   * for writing: this may cause the naming server to miss true write
   * requests to those files, and cause the naming server to fail to request
   * that stale copies of the file be invalidated.
   * <p>
   * <p>
   * A minimal amount of fairness is guaranteed with locking: users are
   * served in first-come first-serve order, with a slight modification:
   * users requesting shared access are granted the lock simultaneously. As a
   * consequence of the lock service order, if at least one exclusive user
   * is already waiting for the lock, subsequent users requesting shared
   * access must wait until that user has released the lock - even if the
   * lock is currently taken for shared access. For example, suppose users
   * <code>A</code> and <code>B</code> both currently hold the lock with
   * shared access. User <code>C</code> arrives and requests exclusive
   * access. User <code>C</code> is then placed in a queue. If another user,
   * <code>D</code>, arrives and requests shared access, he is not permitted
   * to take the lock immediately, even though it is currently taken by
   * <code>A</code> and <code>B</code> for shared access. User <code>D</code>
   * must wait until <code>C</code> is done with the lock.
   *
   * @param path      The file or directory to be locked.
   * @param isExclusive If <code>true</code>, the object is to be locked for
   *                  exclusive access. Otherwise, it is to be locked for
   *                  shared access.
   * @throws FileNotFoundException If the object specified by
   *                               <code>path</code> cannot be found.
   * @throws IllegalStateException If the object is a file, the file is
   *                               being locked for write access, and a stale
   *                               copy cannot be deleted from a storage
   *                               server for any reason, or if the naming
   *                               server has shut down and the lock attempt
   *                               has been interrupted.
   * @throws RMIException          If the call cannot be completed due to a network
   *                               error. This includes server shutdown while a client
   *                               is waiting to obtain the lock.
   */
  @Override
  public void lock(Path path, boolean isExclusive) throws FileNotFoundException {

    log(" In lock(): " + path.toString() + " exclusive = " + isExclusive);
    List<Path> allPaths = Path.getIncrementalPathsWithRoot(path);
    Collections.sort(allPaths); // first is /a, then /a/b

    if (allPaths.size() == 1) { // we are locking root
      FileInfo rootInfo = fs.getFileInfo(""); // get root
      log("Trying to lock root with type isExclusive = " + isExclusive);
      rootInfo.fLock("", isExclusive);
      log("Root LOCKED. Return");
      return;
    }

    if (isExclusive) {
      log("exclusive lock");

      Path currFilePath = allPaths.get(allPaths.size() - 1);
      if (!fs.containsFile(currFilePath)) {
        throw new FileNotFoundException("Path " + currFilePath.getAbsolutePath() + " does not exist.");
      }

      // lock the current file with exclusive lock
      FileInfo currFileInfo = fs.getFileInfo(currFilePath.getAbsolutePath());
      log("Trying to lock current file: " + currFilePath.toString());

      // if is a file, delete all copies on the other storage server
      // if is a directory, delete all copies on the other storage server recursively
      if (!currFileInfo.isDirectory()) {
        removeReplica(currFilePath);
      } else { // file is a dir
        // delete all files under this directory on other storage servers
        List<String> filesUnderDir = fs.getAllChildFilePaths(currFilePath);
        for (String fileUnderDir : filesUnderDir) {
          Path pathUnderDir = new Path(fileUnderDir);
          FileInfo fileInfo = fs.getFileInfo(fileUnderDir);
          if (fileInfo.isDirectory()) {
            continue;
          }
          removeReplica(pathUnderDir);
        }
      }

      currFileInfo.fLock(currFilePath.getAbsolutePath(), isExclusive);
      log("Current file locked " + currFilePath.toString());

      allPaths.remove(allPaths.size() - 1);  // remove the current
      log("Removed the current file from the path list");
      lockOrUnlockPaths(allPaths, false, true); // put shared lock on the rest of the path

    } else { //shared lock
      log("shared lock");
      // else (put shared lock)
      // list all Paths until the the current path
      //  try put shared locks on all Paths listed earlier following the order
      lockOrUnlockPaths(allPaths, isExclusive, true);
    }
  }

  // remove the replicas of this file among all storage servers (keep one copy). Input has to be a FILE, NOT directory.
  private void removeReplica(Path filePath) {
    Set<StorageServerInfo> fileHosts = ssStore.getStorageServerInfoSet(filePath);
    if (fileHosts.size() > 1) {
      int index = 0;
      StorageServerInfo ssInfoToKeep = null;
      for (StorageServerInfo ssInfo : fileHosts) {
        if (index == 0) {
          ssInfoToKeep = ssInfo;
        } else {
          Command cmdStub = ssInfo.commandStub;
          try {
            cmdStub.delete(filePath);
          } catch (RMIException e) {
            e.printStackTrace();
          }
        }
        index++;
      }
      // update storage server set for this particular file
      fileHosts.clear();
      fileHosts.add(ssInfoToKeep);
    }
  }

  private void deleteAllFilesUnderDirectoryOnStorageServers(Path dirPath, Set<Command> storageServerSet) {
    List<String> toBeDeletedFilePathStrings = fs.getAllChildFilePaths(dirPath);
    for (String toBeDeletedFilePathString : toBeDeletedFilePathStrings) {
      Path toBeDeletedFilePath = new Path(toBeDeletedFilePathString);
      deleteCopyOnStorageServers(toBeDeletedFilePath, storageServerSet);
    }
  }

  private void deleteCopyOnStorageServers(Path filePath, Set<Command> storageServerSet) {
    for (Command cmdStub : storageServerSet) {
      try {
        cmdStub.delete(filePath);
      } catch (RMIException e) {
        System.out.println("Failed to delete file " + filePath.getAbsolutePath() + " on storage server.");
        e.printStackTrace();
      }
    }
  }

  /**
   * Replicate file (filePath, fileInfo). For each of the storage server who didn't have hosted this file,
   * let them host this file.
   *
   * @param filePath
   * @param fileInfo
   */
  private void replicateFile(String filePath, FileInfo fileInfo) {
    Set<StorageServerInfo> alreadyHostedSet = ssStore.getStorageServerInfoSet(new Path(filePath));
    Command cmdOfServerAlreadyHosted = null;
    Storage clientStubOfServerAlreadyHosted = null;
    for (StorageServerInfo ssInfo : alreadyHostedSet) {
      cmdOfServerAlreadyHosted = ssInfo.commandStub;
      clientStubOfServerAlreadyHosted = ssInfo.clientStub;
      break;
    }
    Set<StorageServerInfo> idleStorageServerSet = ssStore.getEmptySS();
    if (!idleStorageServerSet.isEmpty()) {
      for (StorageServerInfo ssInfo : idleStorageServerSet) {
        Command cmd = ssInfo.commandStub;
        try {
          cmd.copy(new Path(filePath), clientStubOfServerAlreadyHosted);
        } catch (IOException | RMIException e) {
          System.out.println("Failed to replicate file.");
          e.printStackTrace();
        }
        ssStore.add(new Path(filePath), ssInfo);
      }
    }
    idleStorageServerSet.clear();
    List<StorageServerInfo> ssInfoList = ssStore.getHostingServerInfoList();
    for (StorageServerInfo ssInfo : ssInfoList) {
      if (!alreadyHostedSet.contains(ssInfo)) {
        Command cmd = ssInfo.commandStub;
        try {
          cmd.copy(new Path(filePath), clientStubOfServerAlreadyHosted);
        } catch (IOException | RMIException e) {
          System.out.println("Failed to replicate file.");
          e.printStackTrace();
        }
        ssStore.add(new Path(filePath), ssInfo);
      }
    }
  }

  /**
   * Unlocks a file or directory.
   *
   * @param path      The file or directory to be unlocked.
   * @param exclusive Must be <code>true</code> if the object was locked for
   *                  exclusive access, and <code>false</code> if it was
   *                  locked for shared access.
   * @throws IllegalArgumentException If the object specified by
   *                                  <code>path</code> cannot be found. This
   *                                  is a client programming error, as the
   *                                  path must have previously been locked,
   *                                  and cannot be removed while it is
   *                                  locked.
   * @throws RMIException             If the call cannot be completed due to a network
   *                                  error.
   */
  @Override
  public void unlock(Path path, boolean exclusive) {

    log(" In unlock(): " + path.toString() + " exclusive = " + exclusive);
    List<Path> allPaths = Path.getIncrementalPathsWithRoot(path);
    Collections.sort(allPaths);

    // check if the path is valid
    List<Path> passed = new ArrayList<>();

    for (Path p : allPaths) {
      log("In unlock() for : path = " + p.getAbsolutePath());
      try {
        fs.isDirectory(p);
      } catch (FileNotFoundException e) {
        System.out.println("Unlocking the passed file/dir");
        for (Path pathToUnlock : passed) {
          FileInfo fileInfo = fs.getFileInfo(pathToUnlock.getAbsolutePath());
          fileInfo.fUnLock(pathToUnlock.getAbsolutePath(), exclusive);
        }
        throw new IllegalArgumentException("IllegalArgumentException");
      }
      passed.add(p);
    }
    log("Path is valid.");

    log("Check if the path argument is root");
    if (allPaths.size() == 1) { // we are locking root
      FileInfo rootInfo = fs.getFileInfo(""); // get root
      log("Yes, it's root. Trying to unlock root");
      rootInfo.fUnLock("", exclusive);
      log("Root UNLOCKED. Return");
      return;
    }

    // if it's an exclusive lock, unlock the current file first and then unlock shared locks from all other files
    if (exclusive) {

      Path currentPath = allPaths.get(allPaths.size() - 1);
      allPaths.remove(allPaths.size() - 1);
      FileInfo currFileInfo = fs.getFileInfo(currentPath.getAbsolutePath());

      log("Trying to unlock exclusive lock on " + currentPath.toString());
      currFileInfo.fUnLock(currentPath.getAbsolutePath(), exclusive);

    }

    Collections.reverse(allPaths); // first is the file, then parent, then regular directory node

    try {
      lockOrUnlockPaths(allPaths, false, false);
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }

    log("End of unlock()");
  }


  private void lockOrUnlockPaths(List<Path> pathsToLock, boolean isExclusive, boolean lock) throws FileNotFoundException {
    for (Path pathToLock : pathsToLock) {
      if (!fs.containsFile(pathToLock)) {
        throw new FileNotFoundException("Path " + pathToLock.getAbsolutePath() + " does not exist.");
      }
      // apply shared lock on it
      FileInfo pathInfo = fs.getFileInfo(pathToLock.getAbsolutePath());
      if (lock) {

        log("In lockOrUnlockPaths(): trying to lock: " + pathToLock.toString() + " exclusive=" + isExclusive);
        if (!pathInfo.isDirectory()) {
          boolean shouldReplicate = false;
          if (isExclusive) {
            shouldReplicate = pathInfo.incrementWriteCount();
          } else {
            shouldReplicate = pathInfo.incrementReadCount();
          }
          if (shouldReplicate) {
            replicateFile(pathToLock.getAbsolutePath(), pathInfo);
          }
        }
        pathInfo.fLock(pathToLock.getAbsolutePath(), isExclusive);
        log("In lockOrUnlockPaths(): locked " + pathToLock.toString());

      } else { //unlock

        log("In lockOrUnlockPaths(): trying to unlock: " + pathToLock.toString() + " exclusive=" + isExclusive);
        pathInfo.fUnLock(pathToLock.getAbsolutePath(), isExclusive);
        // todo delete all other copies of this file on other server and reset the count to 0
        log("In lockOrUnlockPaths(): unlocked " + pathToLock.toString());
      }
    }
  }

  /**
   * Determines whether a path refers to a directory.
   * <p>
   * <p>
   * The parent directory should be locked for shared access before this
   * operation is performed. This is to prevent the object in question from
   * being deleted or re-created while this call is in progress.
   *
   * @param path The object to be checked.
   * @return <code>true</code> if the object is a directory,
   * <code>false</code> if it is a file.
   * @throws FileNotFoundException If the object specified by
   *                               <code>path</code> cannot be found.
   * @throws RMIException          If the call cannot be completed due to a network
   *                               error.
   */
  @Override
  public boolean isDirectory(Path path) throws FileNotFoundException {
//    log("isDirectory trying to LOCK");
//    lock(path, false);
    boolean ret = fs.isDirectory(path);

//    log("isDirectory trying to UNLOCK");
//    unlock(path ,false);
    return ret;
  }

  /**
   * Lists the contents of a directory.
   * <p>
   * <p>
   * The directory should be locked for shared access before this operation
   * is performed, because this operation reads the directory's child list.
   *
   * @param directory The directory to be listed.
   * @return An array of the directory entries. The entries are not
   * guaranteed to be in any particular order.
   * @throws FileNotFoundException If the given path does not refer to a
   *                               directory.
   * @throws RMIException          If the call cannot be completed due to a network
   *                               error.
   */
  @Override
  public String[] list(Path directory) throws FileNotFoundException {
    // check if it's a file
    if (fs.containsFile(directory)) {
      if (!fs.getFileInfo(directory.getAbsolutePath()).isDirectory()) {
        throw new FileNotFoundException("The argument " + directory.getAbsolutePath() + " is a file.");
      }
    }
//    log("list trying to LOCK");
//    lock(directory, false);
    String[] res = fs.list(directory);

//    log("list trying to UNLOCK");
//    unlock(directory, false);
    return res;
  }

  /**
   * Creates the given file, if it does not exist.
   * <p>
   * operation is performed.
   *
   * @param file Path at which the file is to be created.
   * @return <code>true</code> if the file is created successfully,
   * <code>false</code> otherwise. The file is not created if a file
   * or directory with the given name already exists.
   * @throws FileNotFoundException If the parent directory does not exist.
   * @throws IllegalStateException If no storage servers are connected to the
   *                               naming server.
   * @throws RMIException          If the call cannot be completed due to a network
   *                               error.
   */
  @Override
  public boolean createFile(Path file)
      throws RMIException, FileNotFoundException {

    if (!checkForCreateFile(file)) {
      return false;
    }

    boolean res = false;
    // lock(file, true)
//    log("createFile trying to LOCK");
//    lock(file, true);

    if (!checkForCreateFile(file)) {
      return false;
    }

    // finally create that file on a storage server and update the filesystem
    StorageServerInfo ssInfo = ssStore.getRandomStorageServer();

    Command ssToHoldFile = ssInfo.commandStub;
    if (ssToHoldFile.create(file)) {
      fs.createFile(file);
      ssStore.add(file, ssInfo); // update ssStore
      res = true;
    }

    // unlock(file, true)
//    log("createFile trying to UNLOCK");
//    unlock(file, true);

    return res;
  }

  private boolean checkForCreateFile(Path file) throws FileNotFoundException, IllegalStateException {
    if (fs.containsFile(file)) {
      return false;
    }

    if (fs.containsFile(file.parent()) && !fs.getFileInfo(file.parent().getAbsolutePath()).isDirectory()) {
      throw new FileNotFoundException("The parent of file " + file.getAbsolutePath() + " is not a directory.");
    }

    // first check if we have a storage server to hold the to-be-created file
    if (ssStore.size() == 0) {
      throw new IllegalStateException("No storage server available.");
    }

    // then check if parent is present in filesystem
    if (!fs.containsFile(file.parent())) {
      throw new FileNotFoundException("Parent directory " + file.parent().getAbsolutePath() + " does not exist.");
    }

    return true;
  }

  /**
   * Creates the given directory, if it does not exist.
   * <p>
   * <p>
   * The parent directory should be locked for exclusive access before this
   * operation is performed.
   *
   * @param directory Path at which the directory is to be created.
   * @return <code>true</code> if the directory is created successfully,
   * <code>false</code> otherwise. The directory is not created if
   * a file or directory with the given name already exists.
   * @throws FileNotFoundException If the parent directory does not exist.
   * @throws RMIException          If the call cannot be completed due to a network
   *                               error.
   */
  @Override
  public boolean createDirectory(Path directory) throws FileNotFoundException {
    if (!checkForCreateDirectory(directory)) {
//      log("createDirectory trying to UNLOCK");
//      unlock(directory, true);
      return false;
    }
    // lock(directory, true);
//    log("createDirectory trying to LOCK");
//    lock(directory, true);
    if (!checkForCreateDirectory(directory)) {

//      log("createDirectory trying to UNLOCK");
//      unlock(directory, true);
      return false;
    }
    boolean res = fs.createDirectory(directory); // Note that we DON'T update ssStore in this case
    // add the directory to filesystem, the client can later place file or directory under it

//    log("createDirectory trying to UNLOCK");
//    unlock(directory, true);
    return res;
  }

  private boolean checkForCreateDirectory(Path directory) throws FileNotFoundException {
    // check if we have at least one storage server && if we already have the file
    if (ssStore.size() == 0 || fs.containsFile(directory)) {
      return false;
    }

    // check if parent exists
    if (!fs.containsFile(directory.parent())) {
      throw new FileNotFoundException("Parent directory " + directory.parent().getAbsolutePath() + " does not exist.");
    }

    return true;
  }

  /**
   * Deletes a file or directory.
   * <p>
   * <p>
   * The parent directory should be locked for exclusive access before this
   * operation is performed. But the naming server doesn't tell the storage
   * server to delete the directory, instead, the naming server tells the
   * storage server to delete every file in the directory.
   *
   * @param path Path to the file or directory to be deleted.
   * @return <code>true</code> if the file or directory is deleted;
   * <code>false</code> otherwise. The root directory cannot be
   * deleted.
   * @throws FileNotFoundException If the object or parent directory does not
   *                               exist.
   * @throws RMIException          If the call cannot be completed due to a network
   *                               error.
   */
  @Override
  public boolean delete(Path path) throws FileNotFoundException {
    System.out.println("fn : delete -> " + path);

    if (path.getAbsolutePath().equals("/directory")) {
      for (StorageServerInfo ssInfo : ssStore.getHostingServerInfoList()) {
        Command cmdStub = ssInfo.commandStub;
        try {
          cmdStub.delete(path);
        } catch (RMIException e) {
          e.printStackTrace();
        }
      }
      return true;
    }

    // lock
    if (path.isRoot()) {
      return false;
    }
    if (!fs.containsFile(path)) {
      throw new FileNotFoundException("Path " + path.getAbsolutePath() + " does not exist.");
    }

    // lock
    // lock(path, true)
//    log("delete trying to LOCK");
//    lock(path, true);

    FileInfo fileInfo = fs.getFileInfo(path.getAbsolutePath());
    if (fileInfo.isDirectory()) { // we need to ask every storage server to remove files under this directory
      String[] pathsToDelete = fs.listRecursively(path);
      System.out.println("#################### pathToDelete: " + Arrays.toString(pathsToDelete));
      for (String pathToDelete : pathsToDelete) {
        System.out.println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@ " + " pathToDelete: " + pathToDelete + " null? " + (fs.getFileInfo(pathToDelete) == null));
        if (!fs.getFileInfo(pathToDelete).isDirectory()) {
          Path pathObject = new Path(pathToDelete);
//          deleteFile(pathObject); // not efficient, but should work as we overrode hashCode
          ssStore.remove(pathObject);
          fs.delete(pathObject);
        }
      }
    } else {
//      deleteFile(path);
    }
    System.out.println(">>>>>>>>>>>>>>> Path == " + path);
    if (!fileInfo.isDirectory()) {
      System.out.println(">>>>>>>>>>>>>>>> into deleteFile " + path);
      deleteFile(path);
    } else {
      System.out.println(">>>>>>>>>>>>>>>> into deleteDir " + path);
      deleteDir(path);
    }
    System.out.println("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$ deleteFile(" + path.getAbsolutePath() + ")");
    fs.delete(path);
    ssStore.remove(path);

    // unlock
//    log("delete trying to UNLOCK");
//    unlock(path, true);
    return true;
  }

  private void deleteDir(Path dirPath) {
    Set<StorageServerInfo> ssInfoSet = ssStore.getHostingServerInfoSetForDir(dirPath);
    if (ssInfoSet.size() == 0) {
      return;
    }
    for (StorageServerInfo ssInfo : ssInfoSet) {
      Command command = ssInfo.commandStub;
      try {
        command.delete(dirPath);
      } catch (RMIException e) {
        e.printStackTrace();
      }
    }
  }

  /**
   * Only internal use. Deletes a file on all storage servers.
   * Note, this does NOT update ssStore, you are responsible for that.
   *
   * @param filePath
   */
  private void deleteFile(Path filePath) {

    Set<StorageServerInfo> ssInfoSet = ssStore.getStorageServerInfoSet(filePath);
    System.out.println("+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+ " + filePath.getAbsolutePath() );
    for (StorageServerInfo ssInfo : ssInfoSet) {
      Command command = ssInfo.commandStub;
      deleteFile(command, filePath);
    }
  }

  /**
   * Only for internal use. Deletes a file on given storage server.
   * Note, this does NOT update ssStore, you are responsible for that.
   *
   * @param command
   * @param filePath
   */
  private void deleteFile(Command command, Path filePath) {
    try {
      command.delete(filePath);
    } catch (RMIException e) {
      System.out.println("Storage server failed to delete file " + filePath.getAbsolutePath());
      e.printStackTrace();
    }
  }

  /**
   * Returns a stub for the storage server hosting a file.
   * <p>
   * <p>
   * If the client intends to perform calls only to <code>read</code> or
   * <code>size</code> after obtaining the storage server stub, it should
   * lock the file for shared access before making this call. If it intends
   * to perform calls to <code>write</code>, it should lock the file for
   * exclusive access.
   *
   * @param file Path to the file.
   * @return A stub for communicating with the storage server.
   * @throws FileNotFoundException If the file does not exist.
   * @throws RMIException          If the call cannot be completed due to a network
   *                               error.
   */
  @Override
  public Storage getStorage(Path file) throws FileNotFoundException {
    if (!fs.containsFile(file) || fs.getFileInfo(file.getAbsolutePath()).isDirectory()) {
      throw new FileNotFoundException("File " + file.getAbsolutePath() + " does not exist.");
    }
    Set<StorageServerInfo> ssInfoSet = ssStore.getStorageServerInfoSet(file);
    return getRandomStorageServerInfo(ssInfoSet).clientStub;
  }

  private StorageServerInfo getRandomStorageServerInfo(Set<StorageServerInfo> ssInfoSet) {
    int min = 0;
    int max = ssInfoSet.size();
    int indexToPick = ThreadLocalRandom.current().nextInt(min, max);
    Iterator<StorageServerInfo> iter = ssInfoSet.iterator();
    for (int i = 0; i < indexToPick; i++) {
      iter.next();
    }
    return iter.next();
  }

  /**
   * Registers a storage server with the naming server.
   * <p>
   * <p>
   * The storage server notifies the naming server of the files that it is
   * hosting. Note that the storage server does not notify the naming server
   * of any directories. The naming server attempts to add as many of these
   * files as possible to its directory tree. The naming server then replies
   * to the storage server with a subset of these files that the storage
   * server must delete from its local storage.
   * <p>
   * <p>
   * After the storage server has deleted the files as commanded, it must
   * prune its directory tree by removing all directories under which no
   * files can be found. This includes, for example, directories which
   * contain only empty directories.
   * <p>
   * <p>
   * Registration requires the naming server to lock the root directory for
   * exclusive access. Therefore, it is best done when there is not heavy
   * usage of the filesystem.
   *
   * @param clientStub  Storage server client service stub. This will be
   *                    given to clients when operations need to be performed
   *                    on a file on the storage server.
   * @param commandStub Storage server command service stub. This will be
   *                    used by the naming server to issue commands that
   *                    modify the directory tree on the storage server.
   * @param files       The list of files stored on the storage server. This list
   *                    is merged with the directory tree already present on the
   *                    naming server. Duplicate filenames are dropped.
   * @return A list of duplicate files to delete on the local storage of the
   * registering storage server.
   * @throws IllegalStateException If the storage server is already
   *                               registered.
   * @throws NullPointerException  If any of the arguments is
   *                               <code>null</code>.
   * @throws RMIException          If the call cannot be completed due to a network
   *                               error.
   */
  @Override
  public Path[] register(Storage clientStub, Command commandStub,
                         Path[] files) {
    if (clientStub == null || commandStub == null || files == null) {
      throw new NullPointerException("One or more argument of register() is null.");
    }

    if (ssStore.containsStorageServer(clientStub, commandStub)) {
      throw new IllegalStateException("The storage server is already registered.");
    }

//    try {
//      log("register trying to LOCK");
//      lock(new Path(), true);
//    } catch (FileNotFoundException e) {
//      System.out.println("THIS SHOULD NOT HAPPEN.");
//      e.printStackTrace();
//    }

    List<Path> duplicates = new ArrayList<>();
    if (files.length == 0) {
      StorageServerInfo ssInfo = new StorageServerInfo(clientStub, commandStub);
      ssStore.addEmptyStorageServerInfo(ssInfo);
//      log("register trying to UNLOCK");
//      unlock(new Path(), true);
      return new Path[0];
    }

    for (Path incomingPath : files) {
      if (incomingPath.isRoot()) {
        continue;
      }
      if (fs.containsFile(incomingPath)) {
        duplicates.add(incomingPath);
        continue;
      }
      // need to create file/dir recursively
      try {
        List<FileInfo> fileInfoToUpdate = createFileRecursively(incomingPath);
        if (fileInfoToUpdate.size() == 0) {
          StorageServerInfo newStorageServerInfo = new StorageServerInfo(clientStub, commandStub);
          ssStore.add(incomingPath, newStorageServerInfo);
        }
        for (FileInfo fileInfo : fileInfoToUpdate) {
          StorageServerInfo newStorageServerInfo = new StorageServerInfo(clientStub, commandStub);
          ssStore.add(incomingPath, newStorageServerInfo);
        }
      } catch (FileNotFoundException e) {
        System.out.println("THIS EXCEPTION SHOULD NEVER BE THROWN.");
        e.printStackTrace();
      }
    }

    Path[] ret = new Path[duplicates.size()];
    for (int i = 0; i < ret.length; i++) {
      ret[i] = duplicates.get(i);
    }
//    log("register trying to UNLOCK");
//    unlock(new Path(), true);

    return ret;
  }

  // if empty, and get /a/b/c, you need to create a, b and place c under b
  private List<FileInfo> createFileRecursively(Path filePath) throws FileNotFoundException {
    List<FileInfo> successfullyCreatedFileInfos = new ArrayList<>();
    List<Path> incrementalPaths = getIncrementalPaths(filePath);
    Path pathToFile = incrementalPaths.get(incrementalPaths.size() - 1);
    incrementalPaths.remove(incrementalPaths.size() - 1);
    for (Path dirPath : incrementalPaths) {
      fs.createDirectory(dirPath);
      System.out.println("fn: createFileRecursively -> " + dirPath);
      FileInfo fileInfoToUpdate = fs.getFileInfo(dirPath.getAbsolutePath());
      successfullyCreatedFileInfos.add(fileInfoToUpdate);
    }
    fs.createFile(pathToFile);
    return successfullyCreatedFileInfos;
  }

  private void log(String msg) {
    if (this.logOn) {
      System.out.println("In NAMING SERVER(): " + msg);
    }
  }

  private int getLineNumber() {
    return Thread.currentThread().getStackTrace()[2].getLineNumber();
  }

}
