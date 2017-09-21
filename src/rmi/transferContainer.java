package rmi;

import java.lang.Object;
import java.io.*;
import java.util.*;

public class transferContainer implements java.io.Serializable {

    public String funcName;

    ArrayList<Object> funcArgs;
    ArrayList<Class> parameterTypes;

    public transferContainer(String name, Object [] args, Class [] paraTypes ){
        this.funcName = name;
        
        if(args == null){
            this.funcArgs = null;
            this.parameterTypes = null;
        }else{

            this.funcArgs = new ArrayList<Object>();
            this.parameterTypes = new ArrayList<Class>();

            for(Object arg : args){
                this.funcArgs.add(arg);
            }    

            for(Class paraType : paraTypes){
                this.parameterTypes.add(paraType);
            }    
        }
    }
}

