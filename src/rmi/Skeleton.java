package rmi;

import java.lang.Object;
import java.lang.Error;
import java.lang.Exception;

//import java.rmi.RMIException; ??
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.io.*;
import java.net.*;
import java.util.*;
//import java.net.ServerSocket;
//import java.net.Socket;
/** RMI skeleton

    <p>
    A skeleton encapsulates a multithreaded TCP server. The server's clients are
    intended to be RMI stubs created using the <code>Stub</code> class.

    <p>
    The skeleton class is parametrized by a type variable. This type variable
    should be instantiated with an interface. The skeleton will accept from the
    stub requests for calls to the methods of this interface. It will then
    forward those requests to an object. The object is specified when the
    skeleton is constructed, and must implement the remote interface. Each
    method in the interface should be marked as throwing
    <code>RMIException</code>, in addition to any other exceptions that the user
    desires.

    <p>
    Exceptions may occur at the top level in the listening and service threads.
    The skeleton's response to these exceptions can be customized by deriving
    a class from <code>Skeleton</code> and overriding <code>listen_error</code>
    or <code>service_error</code>.
*/
public class Skeleton<T>
{

    T localServer;
    Class<T> interfaceClass;

    int port;           //port number used to create the server socket
    InetAddress addr;
    //String hostname;    // ???
    ServerSocket ssock; // server socket
    boolean serverRunning;
    static int aliveWorkerThread; //keep track of how many worker threads are currently alive
    static final Object countLock = new Object();

    static final Object arrayLock = new Object();
    listeningSocket listner; // object of the listening thread
    Thread listenerThread;   

    List<worker> workerList;
    List<Thread> threadList;

    /** Creates a <code>Skeleton</code> with no initial server address. The
        address will be determined by the system when <code>start</code> is
        called. Equivalent to using <code>Skeleton(null)</code>.

        <p>
        This constructor is for skeletons that will not be used for
        bootstrapping RMI - those that therefore do not require a well-known
        port.

        @param c An object representing the class of the interface for which the
                 skeleton server is to handle method call requests.
        @param server An object implementing said interface. Requests for method
                      calls are forwarded by the skeleton to this object.
        @throws Error If <code>c</code> does not represent a remote interface -
                      an interface whose methods are all marked as throwing
                      <code>RMIException</code>.
        @throws NullPointerException If either of <code>c</code> or
                                     <code>server</code> is <code>null</code>.
     */

    public Skeleton(Class<T> c, T server) throws NullPointerException, Error
    {
        
        Helper.log("Constructor called: Skeleton(Class<T> c, T server)");
        //check exceptions
        checkExceptions(c, server);
        Helper.log("checkExceptions passed");
        // initialize fields
        this.localServer = server;
        this.interfaceClass = c;
        this.port = 0; //default port
        this.addr = null;
        this.serverRunning = false;

        this.workerList = new ArrayList<worker>();
        this.threadList = new ArrayList<Thread>();

        // for debug purpose
        Helper.log("port is: " + Integer.toString(this.port));
        Helper.log("addr is: null");
    }

    /** Creates a <code>Skeleton</code> with the given initial server address.

        <p>
        This constructor should be used when the port number is significant.

        @param c An object representing the class of the interface for which the
                 skeleton server is to handle method call requests.
        @param server An object implementing said interface. Requests for method
                      calls are forwarded by the skeleton to this object.
        @param address The address at which the skeleton is to run. If
                       <code>null</code>, the address will be chosen by the
                       system when <code>start</code> is called.
        @throws Error If <code>c</code> does not represent a remote interface -
                      an interface whose methods are all marked as throwing
                      <code>RMIException</code>.
        @throws NullPointerException If either of <code>c</code> or
                                     <code>server</code> is <code>null</code>.
     */
    public Skeleton(Class<T> c, T server, InetSocketAddress address)
    {
        //throw new UnsupportedOperationException("not implemented");
        //check exceptions
        
        Helper.log("Constructor called: Skeleton(Class<T> c, T server, InetSocketAddress address) ");
        checkExceptions(c, server);
        Helper.log("checkExceptions passed");

        // initialize fields
        this.localServer = server;
        this.interfaceClass = c;
        if( address == null){
            this.port = 0; //default port
            this.addr = null; //"localhost";
        }else{
            this.port = address.getPort();
            this.addr = address.getAddress();
        }

        this.serverRunning = false;

        this.workerList = new ArrayList<worker>();
        this.threadList = new ArrayList<Thread>();
        
        // for debug purpose
        
        if(this.port != 0)    Helper.log("port is: " + Integer.toString(this.port));
        if(this.addr != null) Helper.log("addr is: " + this.addr.toString());
    }

    /** Called when the listening thread exits.

        <p>
        The listening thread may exit due to a top-level exception, or due to a
        call to <code>stop</code>.

        <p>
        When this method is called, the calling thread owns the lock on the
        <code>Skeleton</code> object. Care must be taken to avoid deadlocks when
        calling <code>start</code> or <code>stop</code> from different threads
        during this call.

        <p>
        The default implementation does nothing.

        @param cause The exception that stopped the skeleton, or
                     <code>null</code> if the skeleton stopped normally.
     */
    protected void stopped(Throwable cause)
    {
    }

    /** Called when an exception occurs at the top level in the listening
        thread.

        <p>
        The intent of this method is to allow the user to report exceptions in
        the listening thread to another thread, by a mechanism of the user's
        choosing. The user may also ignore the exceptions. The default
        implementation simply stops the server. The user should not use this
        method to stop the skeleton. The exception will again be provided as the
        argument to <code>stopped</code>, which will be called later.

        @param exception The exception that occurred.
        @return <code>true</code> if the server is to resume accepting
                connections, <code>false</code> if the server is to shut down.
     */
    protected boolean listen_error(Exception exception)
    {
        return false;
    }

    /** Called when an exception occurs at the top level in a service thread.

        <p>
        The default implementation does nothing.

        @param exception The exception that occurred.
     */
    protected void service_error(rmi.RMIException exception)
    {
    }

    public int getPort()
    {
        return this.port;
    }

    /** Starts the skeleton server.

        <p>
        A thread is created to listen for connection requests, and the method
        returns immediately. Additional threads are created when connections are
        accepted. The network address used for the server is determined by which
        constructor was used to create the <code>Skeleton</code> object.

        @throws RMIException When the listening socket cannot be created or
                             bound, when the listening thread cannot be created,
                             or when the server has already been started and has
                             not since stopped.
     */
    public synchronized void start() throws rmi.RMIException
    {
        //throw new UnsupportedOperationException("not implemented");

        // server has already been started
        rmi.Helper.log("Trying to start Skeleton...");
        if(this.serverRunning){
            throw new RMIException("TCP server has already been started");
        }

        //create listening thread
        try{

            ServerSocket ssock;
            if(this.addr == null){
                // let the system pick address automatically
                ssock = new ServerSocket(this.port);
                this.addr = ssock.getInetAddress();
                // for debug purpose
                Helper.log("addr automatically created in start() is: " + this.addr.toString());

            }else{ // this.addr != null
                ssock = new ServerSocket(); // 50 is the maximum number of backlog
                ssock.bind(new InetSocketAddress(this.addr, this.port));
            }

            if(this.port == 0){ //automatically bind with a port
                this.port = ssock.getLocalPort();
                Helper.log("port automatically created in start() is: " + Integer.toString(this.port));
            }

            this.listner = new listeningSocket(ssock);
            this.listenerThread = new Thread(this.listner);
            this.listenerThread.start();

        }catch(Exception e){
            // listening socket cannot be created or bound
            throw new RMIException(e);
        }
        this.serverRunning = true;
        Helper.log("start() ends");
    }

    /** Stops the skeleton server, if it is already running.

        <p>
        The listening thread terminates. Threads created to service connections
        may continue running until their invocations of the <code>service</code>
        method return. The server stops at some later time; the method
        <code>stopped</code> is called at that point. The server may then be
        restarted.
     */
    public synchronized void stop()
    {
        Helper.log("stop() on the Skeleton is called.");
        //throw new UnsupportedOperationException("not implemented");
        if( !this.serverRunning ){ //already stoped
            Helper.log("Skeleton is not running");
            return;
        }

        //tell the listening thread to stop
        this.listner.stopListenner();
        //block until the listener thread goes down
        while( this.listenerThread.isAlive() ){
            try{
                listenerThread.join();
                Helper.log("Listening thread joined");
            }catch(Exception e){
                // do nothing
                Helper.log("Listening thread joined failed. Try ask isAlive() again");
            }
        }
        stopped(null); //stopped normally
        Helper.log("Listening thread finished. stopped() called");
        //log("Listening thread finished.");

        //tell all worker threads to stop
        for(int i = workerList.size() - 1; i >= 0; i-- ){
            workerList.get(i).stopWorker();
        }
        Helper.log("Told all worker threads to stop");

        //wait until all worker threads to finish
        int initialSize = threadList.size();
        Helper.myAssert( initialSize == workerList.size() , "Two lists have different sizes!" );
        for(int i = initialSize - 1; i >= 0; i--){

            //block
            while( threadList.get(i).isAlive() ){
                try{
                    threadList.get(i).join(); //wait until the worker thread finishes
                    Helper.log("One worker thread joined!");
                }catch(Exception e){
                    // do nothing
                    Helper.log("Trying to join worker thread EXCEPTION");
                }
            }
            threadList.remove(i);
            workerList.remove(i);
            Helper.log("Removed a dead thread from lists...");
        }

        Helper.log("All worker thread finished.");
        Helper.myAssert( threadList.size() == workerList.size() , "Two lists have different sizes!" );
        Helper.myAssert( threadList.size() == 0, "List not empty!");
        this.serverRunning = false;
        Helper.log("Server stopped normally by itself because stop() is called on it !");
        Helper.log("stopped() called");
    }


    // helper
    private void checkExceptions(Class<T> c, T server)
    {
        if(c == null){
            throw new NullPointerException("object class is null");
        }

        if(server == null){
            throw new NullPointerException("local server object is null");
        }

        if( !allMethodHasRMIException(c) ){
            throw new Error(" Not all methods are marked as throwing RMIException");
        }
    }


    private boolean allMethodHasRMIException(Class<T> interfaceClass)
    {   
        Method [] allMethods = interfaceClass.getMethods();
        // check excpetions thrown by every methods in the interface
        for (Method m : allMethods){
            //Class[] exceptionTypes = m.getExceptionTypes();
            if( ! hasRMIException( m.getExceptionTypes() ) ){
                return false;
            }
        }
        return true;
    }

    private boolean hasRMIException(Class [] exceptionTypes){
        // If any of the exceptions is "RMIException", return true. Otherwise return false
        for (Class excep : exceptionTypes){
            String exceptionName = excep.getName();
            if( exceptionName.contains( "RMIException") ){
                return true;
            }
        }
        return false;
    }

/*
  _      _     _                       
 | |    (_)   | |                      
 | |     _ ___| |_ ___ _ __   ___ _ __ 
 | |    | / __| __/ _ \ '_ \ / _ \ '__|
 | |____| \__ \ ||  __/ | | |  __/ |   
 |______|_|___/\__\___|_| |_|\___|_|   
                                      
*/  
   // A class for listening thread which is responsible for creating a new TCP socket and
   // keep listening. Whenever a request for connection comes in, create a new thread to
   // run the worker
   private class listeningSocket implements Runnable{

      ServerSocket ssock;
      boolean stopListening;

      listeningSocket(ServerSocket s) throws Exception {
         this.ssock = s;
         this.stopListening = false;
         Helper.log("Listener created.");
      }

      // when listener thread starts running, run() is called
      public void run() {
           Helper.log("Listener started running...");
           Throwable except = null;
           while (!stopListening) {
                try { //try to start the listening thread
                    Socket sock = ssock.accept();
                    Helper.log("New connection from Stub, trying to start a new worker thread");
                    worker w = new worker(sock);
                    Thread t = new Thread(w);

                    // might have a problem later
                    synchronized (arrayLock) {
                        // remove all the dead thread from the array

                        Helper.log("Lock obtained.");
                        int initialSize = threadList.size();
                        Helper.myAssert(initialSize == workerList.size(), "Two lists have different sizes!");

                        for (int i = initialSize - 1; i >= 0; i--) {
                            if (!threadList.get(i).isAlive()) { // already dead
                                threadList.remove(i);
                                workerList.remove(i);
                                Helper.log("Removed a dead thread from lists");
                            }
                        }
                        // add the created array to the global arraylist
                        threadList.add(t);
                        workerList.add(w);
                        Helper.myAssert(threadList.size() == workerList.size(), "Two lists have different sizes!");
                        Helper.log("Added the new thread from lists");
                        Helper.log("Lock Release");
                    }
                    t.start(); //start worker execution
                } catch (IOException e) {
                    if (stopListening) {
                        Helper.log("Listener stopped because Skeleton told it to.");
                        return; // do not shut down the server here

                    } else {
                        Helper.log("Listener stopped because of some problem.");
                        //Called when an exception occurs at the top level in the listening thread
                        if (listen_error(e)) {
                            // continues to accept connections
                            Helper.log("listen_error() told the listener to continue");
                            continue;
                        }
                        stopListening = true;
                        stopped(e);
                        Helper.log("listen_error() returns false, server is shutting down...");
                    }
                }


               if(stopListening){ //time to shutdown the server
                    Helper.log("Listner trying to shut down the server");
                    
                    if(serverRunning){ //if server is curently running
                        listenerShutDownServer();
                        Helper.log("Server stopped");
                    }
                } // else stopListening == false, continue try listening

           }
      }

      // Skeleton uses this method to stop the listenning thread
      public void stopListenner() {
        Helper.log("stopListenner() is called");
        if(this.stopListening == true){
            Helper.log("Listener already stopped. Do nothing");
        }

        this.stopListening = true;
        try{
            this.ssock.close();

        }catch (IOException e) {
            Helper.log("Listener socket has problem to close");
            if( !listen_error(e) ){
                Helper.log("Listner trying to shut down the server");
                stopped(e);

                if(serverRunning){ //if server is curently running
                    listenerShutDownServer();
                    Helper.log("after listenerShutDownServer()");
                }

            } //else ignore the error continue listening
        }
      }

      // for listening thread to shut down the server
      // it tells all worker thread to stop first, and then wait until all worker thread to die
      private void listenerShutDownServer(){
        //stop all worker threads
        //synchronized (arrayLock) {  
            //tell all worker threads to stop
            Helper.log("Lock obtained");
            for(int i = workerList.size() - 1; i >= 0; i-- ){
                workerList.get(i).stopWorker();
                Helper.log("Told a worker thread to stop");
            }

            //wait until all worker threads to finish
            int initialSize = threadList.size();
            Helper.myAssert( initialSize == workerList.size() , "Two lists have different sizes!" );

            for(int i = initialSize - 1; i >= 0; i--){
                //try{
                while( threadList.get(i).isAlive() ){
                    try{
                        threadList.get(i).join();
                        Helper.log("Wait a worker thread to stop");

                    }catch(Exception e){
                        Helper.log("Trying to join worker thread EXCEPTION");
                        System.out.println(e);
                    }
                }
                threadList.remove(i);
                workerList.remove(i);
                Helper.log("Removed a dead thread from lists");
                // both array lists should be empty at this point
            }

            Helper.log("All worker thread finished.");
            serverRunning = false;
            Helper.log("Lock Release");
            Helper.myAssert( threadList.size() == workerList.size() , "Two lists have different sizes!" );
            Helper.log("Server stopped by the listener!");
        //}
      }
   }


/*
 __          __        _               _______ _                        _    _____ _               
 \ \        / /       | |             |__   __| |                      | |  / ____| |              
  \ \  /\  / /__  _ __| | _____ _ __     | |  | |__  _ __ ___  __ _  __| | | |    | | __ _ ___ ___ 
   \ \/  \/ / _ \| '__| |/ / _ \ '__|    | |  | '_ \| '__/ _ \/ _` |/ _` | | |    | |/ _` / __/ __|
    \  /\  / (_) | |  |   <  __/ |       | |  | | | | | |  __/ (_| | (_| | | |____| | (_| \__ \__ \
     \/  \/ \___/|_|  |_|\_\___|_|       |_|  |_| |_|_|  \___|\__,_|\__,_|  \_____|_|\__,_|___/___/
*/

   // A class for worker thread, which is responsible for unmarshalling and invoke method on the server object
   private class worker implements Runnable{

       Socket csocket;
       boolean commandReceived;
       boolean toldToStop;
       boolean reportException;
       Throwable invokedMethodExceptionCause;

       worker(Socket csocket){
           this.csocket = csocket;
           this.commandReceived = false;
           this.toldToStop = false;
           this.reportException = false;
           this.invokedMethodExceptionCause = null;
       }

        // when thread starts running, run() is called
        public void run() {

            Helper.log("Worker thread starts running...");

            ObjectOutputStream out = null;
            ObjectInputStream ois = null;

            try {
                
                out = new ObjectOutputStream( csocket.getOutputStream() );
                out.flush();
                ois = new ObjectInputStream( csocket.getInputStream() );
                // read the object which contains function names and list of argument
                transferContainer e = (transferContainer) ois.readObject();
                this.commandReceived = true;
                Helper.log("A transferContainer e read from the ObjectInputStream");

                // unmarshalling
                String funcName = e.funcName;
                Helper.log("Function name is: " + funcName);

                if(e.funcArgs != null){
                    //convert ArrayList to normal array
                    Class [] paraTypes =  new Class[ e.parameterTypes.size() ];
                    e.parameterTypes.toArray(paraTypes);

                    Helper.log("Parameter tyeps are: ");
                    for(Class eachPara : paraTypes) {
                        Helper.log(eachPara.getName());
                    }
                    Object [] arguments = new Object[ e.funcArgs.size() ];
                    Helper.log("funcArgs.size(): " + e.funcArgs.size());
                    e.funcArgs.toArray(arguments);
                    Helper.log("Arguments are");
                    /*
                    for(Object eachArg : arguments){
                        Helper.log(eachArg.toString());
                    }
                    */

                    // create an object on which a method is going to be invoked
                    Helper.log("Done unmarshalling");

                    Method methodToCall = interfaceClass.getMethod(funcName, paraTypes);
                    Helper.log("methodToCall obtained from the interface");
                    Object returnValue = methodToCall.invoke(localServer, arguments);
                    Helper.log("Invoked and returnValue object obtained");
                    //create an object which is to be serialized
                    transferContainer container = new transferContainer("returnValue", new Object[]{returnValue}, new Class[]{} );
                    //send the result from the invoked method back to the Stub clients
                    out.writeObject(container);
                    Helper.log("returnValue object sent to client proxy");
                
                }else{ //no argument
                    Helper.log("No need to unmarshal");
                    Method methodToCall = interfaceClass.getMethod(funcName);
                    Object returnValue = methodToCall.invoke(localServer);
                    Helper.log("Invoked and returnValue object obtained");
                    //create an object which is to be serialized
                    transferContainer container = new transferContainer("returnValue", new Object[]{returnValue}, new Class[]{} );
                    //send the result from the invoked method back to the Stub clients
                    out.writeObject(container);
                    Helper.log("returnValue object sent to client proxy");
                }

                //transferContainer returnValueContainer = invokedLocalMethodCall(e);

            } catch (IOException e) {

                if(this.toldToStop){
                    System.out.println("Worker was told to stop.");
                }else{
                    System.out.println("Something bad happened to make the worker to stop");
                    //Called when an exception occurs at the top level in a service thread
                    service_error(new RMIException("IO exception happened during worker thread service"));
                }

            } catch (NoSuchMethodException e) {
                System.out.println("NoSuchMethodException happend.");
                System.out.println(e);
                service_error(new RMIException("NoSuchMethodException happend"));

            } catch (InvocationTargetException e) { // invoked method threw exceptions
                //System.out.println("InvocationTargetException happend. That means exception thrown by the invoked method");
                Helper.log("InvocationTargetException happend. That means exception thrown by the invoked method");
                this.reportException = true;
                this.invokedMethodExceptionCause = e.getCause();              

            } catch (ClassNotFoundException e) {
                System.out.println("ClassNotFoundException happend.");
                System.out.println(e);
                service_error(new RMIException("ClassNotFoundException happend"));

            } catch (IllegalAccessException e) {
                System.out.println("IllegalAccessException happend.");
                System.out.println(e);
                service_error(new RMIException("IllegalAccessException happend"));
            }

            try{

                if(this.reportException){
                    Throwable cause = this.invokedMethodExceptionCause;
                    transferContainer container = new transferContainer("returnValue", new Object[]{(Object)cause, (Object)cause}, new Class[]{} );
                    out.writeObject(container);
                    Helper.log("Invoked method excpetion object reported to client proxy");   
                }

                if(ois != null) ois.close();
                if(out != null) out.close();
                csocket.close();
            } catch (IOException e) {
                System.out.println("IOException happend when trying to close socket and object streams");
                service_error(new RMIException("IOException happend when trying to close socket and object streams") );
            }
            Helper.log("Input/Output stream closed as well as the socket");
            Helper.log("Worker thread finished!\n-------------------");
        } // end of run()

        // to stop worker
        public void stopWorker() {

            // if it's already received commands from
            if( !this.commandReceived ){
                this.toldToStop = true;
                try{
                    this.csocket.close();
                }catch(IOException e) {
                    Helper.log("Worker socket has problem to close");
                    //Called when an exception occurs at the top level in a service thread
                    service_error(new RMIException("IO exception happened when trying to close the socket of a worker thread"));                    
                }
            }
        } // end of stopWorker()

   } // end of worker class

}
