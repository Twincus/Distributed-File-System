package rmi;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
/** RMI stub factory.

    <p>
    RMI stubs hide network communication with the remote server and provide a
    simple object-like interface to their users. This class provides methods for
    creating stub objects dynamically, when given pre-defined interfaces.

    <p>
    The network address of the remote server is set when a stub is created, and
    may not be modified afterwards. Two stubs are equal if they implement the
    same interface and carry the same remote server address - and would
    therefore connect to the same skeleton. Stubs are serializable.
 */
public abstract class Stub
{

    /** Creates a stub, given a skeleton with an assigned adress.

        <p>
        The stub is assigned the address of the skeleton. The skeleton must
        either have been created with a fixed address, or else it must have
        already been started.

        <p>
        This method should be used when the stub is created together with the
        skeleton. The stub may then be transmitted over the network to enable
        communication with the skeleton.

        @param c A <code>Class</code> object representing the interface
                 implemented by the remote object.
        @param skeleton The skeleton whose network address is to be used.
        @return The stub created.
        @throws IllegalStateException If the skeleton has not been assigned an
                                      address by the user and has not yet been
                                      started.
        @throws UnknownHostException When the skeleton address is a wildcard and
                                     a port is assigned, but no address can be
                                     found for the local host.
        @throws NullPointerException If any argument is <code>null</code>.
        @throws Error If <code>c</code> does not represent a remote interface
                      - an interface in which each method is marked as throwing
                      <code>RMIException</code>, or if an object implementing
                      this interface cannot be dynamically created. ???
     */
    public static <T> T create(Class<T> c, Skeleton<T> skeleton) throws UnknownHostException
    {
      //throw new UnsupportedOperationException("not implemented");
      //assume skeleton is always running on the same local host

      //check exception
      Helper.log("public static <T> T create(Class<T> c, Skeleton<T> skeleton) called");
      if(c == null || skeleton == null){
        throw new NullPointerException("Some argument(s) is null");
      }

      if( !c.isInterface() ){
        throw new Error("Need interface");
      }

      Helper.log("Neither arguements are null");
      //If the skeleton has not been assigned an address by the user and has not yet been started.
      if(skeleton.addr == null && skeleton.serverRunning == false ){
        throw new IllegalStateException("skeleton has not been assigned an address by the user and has not yet been started");
      }

      checkExceptionsOfEachMethod(c);

      Helper.log("All methods are marked as throwing RMIException");

      MyInvocationHandler handler = new MyInvocationHandler<T>(c, skeleton.port, skeleton.addr );

      Helper.log("****handler created");
      T proxy;
      try{
        proxy = (T) Proxy.newProxyInstance(
                              c.getClassLoader(),
                              new Class[] { c },
                              handler);
      }catch(Exception e){
        throw new Error("An object implementing this interface cannot be dynamically created");
      }

      Helper.log("Proxy returned");
      return proxy;

    }

    /** Creates a stub, given a skeleton with an assigned address and a hostname
        which overrides the skeleton's hostname.

        <p>
        The stub is assigned the port of the skeleton and the given hostname.
        The skeleton must either have been started with a fixed port, or else
        it must have been started to receive a system-assigned port, for this
        method to succeed.

        <p>
        This method should be used when the stub is created together with the
        skeleton, but firewalls or private networks prevent the system from
        automatically assigning a valid externally-routable address to the
        skeleton. In this case, the creator of the stub has the option of
        obtaining an externally-routable address by other means, and specifying
        this hostname to this method.

        @param c A <code>Class</code> object representing the interface
                 implemented by the remote object.
        @param skeleton The skeleton whose port is to be used.
        @param hostname The hostname with which the stub will be created.
        @return The stub created.
        @throws IllegalStateException If the skeleton has not been assigned a
                                      port.
        @throws NullPointerException If any argument is <code>null</code>.
        @throws Error If <code>c</code> does not represent a remote interface
                      - an interface in which each method is marked as throwing
                      <code>RMIException</code>, or if an object implementing
                      this interface cannot be dynamically created.
     */
    public static <T> T create(Class<T> c, Skeleton<T> skeleton,
                               String hostname) throws UnknownHostException
    {
        Helper.log("public static <T> T create(Class<T> c, Skeleton<T> skeleton, String hostname) called");
        if(c == null || skeleton == null || hostname == null ){
          throw new NullPointerException("Some argument(s) is null");
        }

        if( !c.isInterface() ){
          throw new Error("Need interface");
        }

        checkExceptionsOfEachMethod(c);

        // don't we need to check if server has started running???
        if(skeleton.port == 0){
          throw new IllegalStateException("skeleton has not been assigned a port");
        }


        InetAddress localSkeletonAddr = InetAddress.getByName(hostname); //skeleton.addr.getLocalHost();
        skeleton.addr = localSkeletonAddr;

        InetSocketAddress skeletonAddr = new InetSocketAddress(hostname, skeleton.port);
        MyInvocationHandler handler = new MyInvocationHandler<T>(c, skeletonAddr.getPort(), skeletonAddr.getAddress());
        Helper.log("****handler created");
        T proxy;
        try{
          proxy = (T) Proxy.newProxyInstance(
                                c.getClassLoader(),
                                new Class[] { c },
                                handler);
        }catch(Exception e){
          throw new Error("An object implementing this interface cannot be dynamically created");
        }

        Helper.log("Proxy returned");
        return proxy;

    }
    /** Creates a stub, given the address of a remote server.

        <p>
        This method should be used primarily when bootstrapping RMI. In this
        case, the server is already running on a remote host but there is
        not necessarily a direct way to obtain an associated stub.

        @param c A <code>Class</code> object representing the interface
                 implemented by the remote object.
        @param address The network address of the remote skeleton.
        @return The stub created.
        @throws NullPointerException If any argument is <code>null</code>.
        @throws Error If <code>c</code> does not represent a remote interface
                      - an interface in which each method is marked as throwing
                      <code>RMIException</code>, or if an object implementing
                      this interface cannot be dynamically created.
     */
    public static <T> T create(Class<T> c, InetSocketAddress address)
    {
        //throw new UnsupportedOperationException("not implemented");
        Helper.log("public static <T> T create (Class<T> c, InetSocketAddress address)");
        if(c == null || address == null){
          throw new NullPointerException("Some argument(s) is null");
        }


        if( !c.isInterface() ){
          throw new Error("Need interface");
        }
        Helper.log("c is an interface");
        
        checkExceptionsOfEachMethod(c);

      // create invocation handler

      // don't we need to check if the address has valid port and address???
      MyInvocationHandler handler;
      try{
        handler = new MyInvocationHandler<T>(c, address.getPort(), address.getAddress() );
      }catch(Exception e){
        throw new Error("Handler creation failed");
      }

      Helper.log("****handler created");

      T proxy;

      try{
        proxy = (T) Proxy.newProxyInstance(
                              c.getClassLoader(),
                              new Class[] { c },
                              handler);
      }catch(Exception e){
        throw new Error("An object implementing this interface cannot be dynamically created");
      }

      Helper.log("Proxy returned");
      return proxy;

    }

    // Overriding methods
    /*
    // report the name of the remote interface implemented by the stub, and the 
    // remote address (including hostname and port) of the skeleton to which the stub connects.
    @Override
    public String toString() 
    {
      return  this.c.getName()
    }

    // Returns a hash code value for the object. 
    @Override
    public int hashCode() 
    {

    }

    // Indicates whether some other object is "equal to" this one.
    // Two stubs are considered equal if they implement the same 
    // remote interface and connect to the same skeleton
    @Override
    public boolean equals(Object obj) 
    {

    }
    */
    private static void checkExceptionsOfEachMethod(Class<?> c) {

        //check if each method is marked as throwing RMIException
        // how to check if an object implementing this interface can be dynamically created or not??
        Method [] allMethods = c.getMethods();
        // check excpetions thrown by every methods in the interface
        boolean badInterface = true;
        for (Method m : allMethods){
          Class[] exceptionTypes = m.getExceptionTypes();
          for (Class excep : exceptionTypes){
            String exceptionName = excep.getName();
            if(exceptionName.contains( "RMIException")) {
              badInterface = false;
            }
          } // end for (Class...
          if (badInterface) throw new Error("Not all methods are marked as throwing RMIException");
          badInterface = true;
        } // end for (Method...

    }


}
