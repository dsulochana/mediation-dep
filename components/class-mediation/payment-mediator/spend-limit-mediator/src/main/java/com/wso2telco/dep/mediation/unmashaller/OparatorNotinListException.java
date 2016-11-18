package com.wso2telco.dep.mediation.unmashaller;

/**
 * Created by aushani on 9/22/16.
 */
public class OparatorNotinListException extends Exception {

    ErrorHolder error;

    public OparatorNotinListException(ErrorHolder errorHolder){
        super("Oparator not in the list");
        this.error =errorHolder;
    }


   enum  ErrorHolder{
       OPRATOR_NOT_DEFINED("OParator not in the list"),
       INVALID_CONSUMER_KEY("Invalid consumerKey"),
       INVALID_OPRATOR_ID("Invalid/null Oparator id"),
       NO_SP_DEFINED("service provider list not defined"),
       APPS_NOT_DEFIED("Application list not defined")
       ;

       private String str;
       ErrorHolder(String str){
           this.str =str;
       }

       public String getDesc(){
           return this.str;
       }
   }
}