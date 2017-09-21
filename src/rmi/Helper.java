package rmi;

public class Helper {
   public static boolean loggingOn = false;

   public static void log(String msg){
        if(loggingOn){
            System.out.println(msg);
        }
   }

   public static void myAssert(boolean correct, String why){
        if(!correct){
            System.out.println("Error: " + why);
            System.out.println("Program terminating...");
            System.exit(0);
        }
   }

}