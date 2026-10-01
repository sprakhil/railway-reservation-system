package com.railway.chatbotservice.api;
public class ChatException extends RuntimeException {
    public final int status;
    public ChatException(int status,String message){super(message);this.status=status;}
}
