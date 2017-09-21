package rmi;

import java.lang.Object;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationHandler;
import java.io.*;
import java.net.*;
import java.lang.reflect.Proxy;

public class MyInvocationHandler<T> implements java.lang.reflect.InvocationHandler, Serializable {

  int port;
  InetAddress skeletonAddr;
  Class<T> remoteInterface;
  Throwable excep;
  boolean remoteMethodExcep;

  public MyInvocationHandler(Class<T> remoteInterface, int port, InetAddress skeletonAddr) {
    this.port = port;
    this.skeletonAddr = skeletonAddr;
    this.remoteInterface = remoteInterface;
    this.excep = null;
    this.remoteMethodExcep = false;
  }

  // The proxy parameter passed to the invoke() method is the dynamic proxy object
  // implementing the interface. Most often you don't need this object.

  // The Method object passed into the invoke() method represents the method 
  // called on the interface the dynamic proxy implements.

  // The Object[] args array contains the parameter values passed to the
  // proxy when the method in the interface implemented was called.
  public Object invoke(Object proxy, Method method, Object[] args)
          throws Throwable {

    // if it's a call to the override methods
    String methodCalled = method.getName();

    if (methodCalled.equals("toString") && (args == null || args.length == 0)) {
      try {
        remoteInterface.getMethod(method.getName(), method.getParameterTypes());
      } catch (NoSuchMethodException e) {
        Helper.log("toString() method called");
        return makeString();
      }

    } else if (methodCalled.equals("hashCode") && (args == null || args.length == 0)) {
      try {
        remoteInterface.getMethod(method.getName(), method.getParameterTypes());
      } catch (NoSuchMethodException e) {
        // Returns a hash code value for the object.
        Helper.log("hashCode() method called");
        return new Integer(generateHashCode());
      }

    } else if (methodCalled.equals("equals") && (args != null && args.length == 1)) {
      try {
        remoteInterface.getMethod(method.getName(), method.getParameterTypes());
      } catch (NoSuchMethodException e) {
        Helper.log("equals() method called");
        return isEqual(proxy, args[0]); //??? How to compare
      }


    }
    // RPC call

      Helper.log(methodCalled + "() method called");
      Socket stubSocket = new Socket(this.skeletonAddr, this.port);
      ObjectOutputStream out = new ObjectOutputStream(stubSocket.getOutputStream());
      // get method information
      Class[] parameterTypes = method.getParameterTypes();

      //create an object which is to be serialized
      transferContainer container = new transferContainer(methodCalled, args, parameterTypes);

      out.flush();
      try { // send object to Skeleton
        out.writeObject(container);
      } catch (Exception e) {
        Helper.log("Proxy object trying to writeObject() failed");
      }

      Object returnObject = new Object(); //dummy return object

      try { // receive object to Skeleton

        ObjectInputStream ois = new ObjectInputStream(stubSocket.getInputStream());
        transferContainer e;
        e = (transferContainer) ois.readObject();

        Object[] arguments = new Object[e.funcArgs.size()];
        e.funcArgs.toArray(arguments);

        //there should be only one object in the args array representing the returned value
        returnObject = arguments[0];
        if (e.funcArgs.size() == 2) { //check exception
          Helper.log("Skeleton returned exception!!!");
          this.excep = (Throwable) returnObject;
          //throw excep;
          this.remoteMethodExcep = true;
        }

        //checkReturnedObj(returnObject);
        out.close();
        ois.close();
        stubSocket.close();

      } catch (Exception ex) {
        throw new RMIException("Proxy object trying to readObject() failed");
      }

      if (this.remoteMethodExcep){
        this.remoteMethodExcep = false;
        throw this.excep;
      }

      return returnObject;

  }


  // report the name of the remote interface implemented by the stub, and the 
  // remote address (including hostname and port) of the skeleton to which the stub connects.
  private Object makeString()
  {
      String name = remoteInterface.getName() + " ";
      name += skeletonAddr.getHostName();
      name += ":";
      name += Integer.toString(port);
      return (Object)name;
  }


  // Indicates whether some other object is "equal to" this one.
  // Two stubs are considered equal if they implement the same 
  // remote interface and connect to the same skeleton
  
  private boolean isEqual(Object thisProxy, Object anotherStub)
  { 

    if(anotherStub == null){
      return false;
    }

    if( !Proxy.isProxyClass( anotherStub.getClass()) ){
      return false;
    }

    InvocationHandler thatHandler = Proxy.getInvocationHandler(anotherStub);
    InvocationHandler thisHandler = Proxy.getInvocationHandler(thisProxy);
    boolean res = thisHandler.equals(thatHandler);
    return res;
  }
  
  @Override
  public boolean equals(Object anotherHandler){
    // check if the same port is used
    if (anotherHandler == null) return false;
    if( !(this.getClass()).isAssignableFrom(anotherHandler.getClass() ) ){
      return false;
    }

    final MyInvocationHandler compareHandler = (MyInvocationHandler) anotherHandler;
    if(compareHandler.port != this.port){
      Helper.log("port mismatch");
      return false;
    }

    // check if remote interface name is the same
    if(! compareHandler.remoteInterface.getName().equals( this.remoteInterface.getName() ) ){
      Helper.log("interface mismatch");
      return false;
    }

    // if skeleton address is the same
    if( !(compareHandler.skeletonAddr).equals( this.skeletonAddr) ){
      Helper.log("address mismatch");
      return false;
    }

    return true;
  }


  private int generateHashCode()
  {
    //return this.port + this.remoteInterface.hashCode() + this.skeletonAddr.hashCode();
    return this.port + (this.skeletonAddr).hashCode();
  }


}