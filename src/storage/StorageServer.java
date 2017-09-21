package storage;

import common.Path;
import naming.Registration;
import rmi.RMIException;
import rmi.Skeleton;
import rmi.Stub;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;


/**
 * Storage server.
 * <p>
 * <p>
 * Storage servers respond to client file access requests. The files accessible
 * through a storage server are those accessible under a given directory of the
 * local filesystem.
 */
public class StorageServer implements Storage, Command {
  private Skeleton<Command> skeletonNaming;
  private Command stubNaming;
  private Skeleton<Storage> skeletonClient;
  private Storage stubClient;
  private int storageServer_port;
  private InetAddress storageServer_ip;
  private int clientPort;
  private int commandPort;
  //overview of the local file system
  private Path[] files;
  //root File directory
  private File root;

  private boolean logOn;

  /**
   * Creates a storage server, given a directory on the local filesystem, and
   * ports to use for the client and command interfaces.
   * <p>
   * <p>
   * The ports may have to be specified if the storage server is running
   * behind a firewall, and specific ports are open.
   *
   * @param root        Directory on the local filesystem. The contents of this
   *                    directory will be accessible through the storage server.
   * @param clientPort  Port to use for the client interface, or zero if the
   *                    system should decide the port.
   * @param commandPort Port to use for the command interface, or zero if
   *                    the system should decide the port.
   * @throws NullPointerException If <code>root</code> is <code>null</code>.
   */
  public StorageServer(File root, int clientPort, int commandPort) {
    //throw new UnsupportedOperationException("not implemented");
    log("Constructor StorageServer(File root, int clientPort, int commandPort) invoked");
    this.logOn = false;

    // check argument validity
    if (root == null) {
      throw new NullPointerException("Root is null. Not acceptable!");
    }
    this.root = root;

    if (clientPort < 0 || commandPort < 0) {
      throw new Error("Negative port number??? Gotta be kidding");
    }
    this.clientPort = clientPort;
    this.commandPort = commandPort;
  }

  /**
   * Creats a storage server, given a directory on the local filesystem.
   * <p>
   * <p>
   * This constructor is equivalent to
   * <code>StorageServer(root, 0, 0)</code>. The system picks the ports on
   * which the interfaces are made available.
   *
   * @param root Directory on the local filesystem. The contents of this
   *             directory will be accessible through the storage server.
   * @throws NullPointerException If <code>root</code> is <code>null</code>.
   */
  public StorageServer(File root) throws NullPointerException {
    this(root, 0, 0);
  }


  /**
   * Starts the storage server and registers it with the given naming
   * server.
   *
   * @param hostname      The externally-routable hostname of the local host on
   *                      which the storage server is running. This is used to
   *                      ensure that the stub which is provided to the naming
   *                      server by the <code>start</code> method carries the
   *                      externally visible hostname or address of this storage
   *                      server.
   * @param naming_server Remote interface for the naming server with which
   *                      the storage server is to register.
   * @throws UnknownHostException  If a stub cannot be created for the storage
   *                               server because a valid address has not been
   *                               assigned.
   * @throws FileNotFoundException If the directory with which the server was
   *                               created does not exist or is in fact a
   *                               file.
   * @throws RMIException          If the storage server cannot be started, or if it
   *                               cannot be registered.
   */
  public synchronized void start(String hostname, Registration naming_server)
      throws UnknownHostException, FileNotFoundException
  //throws RMIException, UnknownHostException, IOException, FileNotFoundException
  {
    if (hostname == null) {
      throw new UnknownHostException("hostname is null");
    }

    if (naming_server == null)
      throw new NullPointerException();

    //start the skeleton and the stub for naming server
    InetSocketAddress addressForNaming = new InetSocketAddress(hostname, commandPort);
    skeletonNaming = new Skeleton<>(Command.class, this, addressForNaming);

    try {
      skeletonNaming.start();
    } catch (RMIException e) {

      //log("skeletonNaming.start() failed. RMI exception thrown");
      throw new Error("skeletonNaming.start() failed. RMI exception thrown");
    }
    stubNaming = Stub.create(Command.class, skeletonNaming);

    //start the skeleton and stub for client
    InetSocketAddress addressForClient = new InetSocketAddress(hostname, clientPort);
    skeletonClient = new Skeleton<>(Storage.class, this, addressForClient);
    try {
      skeletonClient.start();
    } catch (RMIException e) {
      throw new Error("skeletonClient.start() failed. RMI exception thrown");
    }

    stubClient = Stub.create(Storage.class, skeletonClient);

    if (root == null || !root.exists() || root.isFile())
      throw new FileNotFoundException("root on storage server doesn't exist or is not a directory");
    this.files = Path.list(root);

    Path[] toDelete;
    try {
      toDelete = naming_server.register(stubClient, stubNaming, files);

    } catch (RMIException e) {
      throw new Error("naming_server.register failed. RMI exception thrown");
    }

    try {
      for (int i = 0; i < toDelete.length; i++) {
        // check if this file exist in the local directory
        File fileToDelete = toDelete[i].toFile(root);
        if (!fileToDelete.exists()) {
          throw new Error("Naming server told Storage Server to delete non-existing file: " + fileToDelete.getAbsolutePath());
        }
        // try to delete
        delete(toDelete[i]);
      }
    } catch (Exception e) {
      throw new Error("Error happened when trying to delete file. ");
    }

  }

  /**
   * Stops the storage server.
   * <p>
   * <p>
   * The server should not be restarted.
   */
  public void stop() {
    try {
      this.skeletonClient.stop();
      this.skeletonNaming.stop();
      stopped(null);
    } catch (Exception e) {
      stopped(e);
    }
  }

  /**
   * Called when the storage server has shut down.
   *
   * @param cause The cause for the shutdown, if any, or <code>null</code> if
   *              the server was shut down by the user's request.
   */
  protected void stopped(Throwable cause) {
    if (cause == null)
      System.out.println("The storage server was shut down by the user's request");
    else
      cause.printStackTrace();
  }

  
    // The following methods are documented in Storage.java.
    // assume all the file here is the directory of the distributed system
    @Override
    public synchronized long size(Path file) throws RMIException, FileNotFoundException
    {
        if (file == null) {
            throw new NullPointerException();
        }

        File localFile = file.toFile(root);

        if(!localFile.exists()){
            throw new FileNotFoundException("the file doesn't exist");
        }

        if(localFile.isDirectory()){

            throw new FileNotFoundException("the file is a directory");
        }
      long sz =  localFile.length();
      return sz;
    }


  @Override
  public synchronized byte[] read(Path file, long offset, int length)
      throws FileNotFoundException, IOException, RMIException {

    log("read(Path file, long offset, int length). Trying to read " + file.toString() + " " + length + " bytes " + " at offset " + offset);
    if (file == null) {
      log("read(Path file, long offset, int length): file == null");
      throw new NullPointerException();
    }

    if (offset < 0 || length < 0 || size(file) < offset + length) {
      log("read(Path file, long offset, int length): offset < 0 || length < 0 || size(file) < offset + length");
      throw new IndexOutOfBoundsException("read file out of bounds");
    }
    //convert the file directory in the distributed system to that of the local file system
    File localFile = file.toFile(root);
    if (!localFile.exists() || localFile.isDirectory()) {
      log("read(Path file, long offset, int length): !localFile.exists() || localFile.isDirectory()");
      throw new FileNotFoundException();
    }

    //read the file at certain offset
    RandomAccessFile ra = new RandomAccessFile(localFile, "r");
    byte[] result;
    int index = 0;
    result = new byte[length];
    ra.seek(offset);
    try {
      while (index < length)
        result[index++] = ra.readByte();
    } finally {
      //log("read(Path file, long offset, int length): readByte() throws exception!");
      ra.close();
    }

    log("read(Path file, long offset, int length) is done!");
    return result;
  }

  @Override
  public synchronized void write(Path file, long offset, byte[] data)
      throws FileNotFoundException, IOException, RMIException {
    log("In write(Path file, long offset, byte[] data). Trying to write to " + file.toString());
    if (file == null || data == null) {
      log("In write(Path file, long offset, byte[] data): file or data is null. Throw EXCEPTION!");
      throw new NullPointerException();
    }

    //throw new UnsupportedOperationException("not implemented");
    if (offset < 0) {
      log("In write(Path file, long offset, byte[] data): offset<0. Throw EXCEPTION!");
      throw new IndexOutOfBoundsException("offset is negative");
    }
    File localFile = file.toFile(root);

    log("In write(Path file, long offset, byte[] data): check if file already exist");
    if (!localFile.exists()) {
      log("In write(Path file, long offset, byte[] data): !localFile.exists()");
      throw new FileNotFoundException("to be written file doesn't exist");
    }

    log("In write(Path file, long offset, byte[] data): check localFile.isDirectory()");
    if (localFile.isDirectory()) {
      log("In write(Path file, long offset, byte[] data): localFile.isDirectory()");
      throw new FileNotFoundException();
    }

    //write the file at certain offset
    RandomAccessFile ra = new RandomAccessFile(localFile, "rw");
    try {
      ra.seek(offset);
      int index = 0;
      while (index < data.length)
        ra.writeByte(data[index++]);
    } finally {
      //log("In write(Path file, long offset, byte[] data): writeByte throws exception. Trying to clsoe fd");
      ra.close();
    }

    log("In write(Path file, long offset, byte[] data): write doen!");

  }


  /** Creates a file on the storage server.

   @param file Path to the file to be created. The parent directory will be
   created if it does not exist. This path may not be the root
   directory.
   @return <code>true</code> if the file is created; <code>false</code>
   if it cannot be created.
   @throws RMIException If the call cannot be completed due to a network
   error.
   */
  // The following methods are documented in Command.java.
  @Override
  public synchronized boolean create(Path file) throws RMIException {
    log("In create(Path file)");
    if (file == null) {
      throw new NullPointerException();
    }

    log("In create(Path file). Trying to create: " + file.toString());
    File localFile = file.toFile(root);
    if (localFile.exists()) {
      log("In create(Path file), create file failed. File already exists!");
      return false;
    }

    log("Check if the parent of the file exist");
    if (!localFile.getParentFile().exists()) {
      //if the parent direcotory does not exist, create it
      log("parent directory not exist. Ready to create it!");
      if (!localFile.getParentFile().mkdirs()) {
        log("failed to create parent directory!");
        return false;
      }
    }

    log("Check if the parent of the file is a directory");
    if(!localFile.getParentFile().isDirectory()){
      log("The parent of the file is NOT a directory! Ready to create it!");
      if (!localFile.getParentFile().getParentFile().mkdirs()) {
        log("failed to create parent directory!");
        return false;
      }
    }

    try {
      //create the target file
      if (localFile.createNewFile()) {
        log("In create(Path file), succeeded to create target file! :) " + file.toString() );
        return true;
      } else {
        log("In create(Path file), failed to create target file! :( " + file.toString() );
        return false;
      }
    } catch (IOException e) {
      log("In create(Path file), IOException happens in createNewFile() " + file.toString());
      e.printStackTrace();
      return false;
    }
  }

  @Override
  public synchronized boolean delete(Path path) throws RMIException {
    File localFile = path.toFile(root);
    //the root dir
    if (!localFile.exists()) {
      log("In delete(Path file), The file to be deleted doesn't exist! " + path.toString());
      return false;
    }
    if (localFile.isDirectory()) {
      if (localFile.equals(root)) {
        log("In delete(Path file), root cannot be deleted");
        return false;
      }
      return deleteDir(localFile);

    }else {
      return deleteFile(localFile);
    }
  }

  /**
   * delete file
   *
   * @param file
   * @return
   */
  private boolean deleteFile(File file) throws RMIException {
    File parent = file.getParentFile();
    boolean flag = file.delete();
    if (parent.equals(root) || parent.list().length != 0)
      return flag;
    return deleteDir(parent);
  }

  /**
   * delete directory
   *
   * @param dir
   * @return
   */
  private boolean deleteDir(File dir) throws RMIException {
    String[] children = dir.list();
    for (int i = 0; i < children.length; i++) {
      File child = new File(dir, children[i]);
      boolean success = false;
      if (child.isFile()) {
        success = deleteFile(child);
      } else {
        success = deleteDir(child);
      }
      if (!dir.exists()) return success;
      if (!success) return false;
    }
    File parent = dir.getParentFile();
    if (!dir.delete()) return false;
    return parent.list().length != 0 || deleteDir(parent);
  }



  /** Copies a file from another storage server.

   @param file Path to the file to be copied.
   @param server Storage server from which the file is to be downloaded.
   @return <code>true</code> if the file is successfully copied;
   <code>false</code> otherwise.
   @throws FileNotFoundException If the file is not present on the remote
   storage server, or the path refers to a
   directory.
   @throws IOException If an I/O exception occurs either on the remote or
   on this storage server.
   @throws RMIException If the call cannot be completed due to a network
   error, whether between the caller and this storage
   server, or between the two storage servers.
   */
  @Override
  public synchronized boolean copy(Path file, Storage server)
      throws RMIException, FileNotFoundException, IOException {

    log("\nIn copy(Path file, Storage server): Trying to copy " + file.toString() + " from another Storage Server");
    File localFile = file.toFile(root);

    long pos = 0;
    int length = 1024 * 1024; //1Mb
    log("Probing the remote server to query size of the file to be copied");
    long size = server.size(file);
    log("In copy(Path file, Storage server): remote file size is: " + size);
    log("In copy(Path file, Storage server): trying to copy remote file...");

    log("In copy(Path file, Storage server): check (localFile.isDirectory()");
    if (localFile.isDirectory()) {
      log("In copy(Path file, Storage server), To be copied file is a directory");
      throw new FileNotFoundException();
    }

    log("In copy(Path file, Storage server): check localFile.exists()");
    if (localFile.exists()) {
      log("In copy(Path file, Storage server): " + file.toString() + " already exists. Trying to delete this local file");
      delete(file);

    }

    log("In copy(Path file, Storage server): trying to create an empty local file with the same name");
    create(file);

    while (size >= length) {
      write(file, pos, server.read(file, pos, length));
      pos += length;
      size -= length;
    }
    if (size > 0) {
      write(file, pos, server.read(file, pos, (int) size));
    }
    log("In copy(Path file, Storage server): copying done!");
    return true;
  }

  private void log(String msg){
    if(this.logOn){
      System.out.println("In STORAGE SERVER: " + msg);
    }
  }

}
