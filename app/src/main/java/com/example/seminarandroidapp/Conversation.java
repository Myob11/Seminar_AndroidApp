package com.example.seminarandroidapp;

public class Conversation {
    private String targetUserId;
    private String contactName;
    private String lastMessage;
    private String time;

    public Conversation() {
    }

    public Conversation(String targetUserId, String contactName, String lastMessage, String time) {
        this.targetUserId = targetUserId;
        this.contactName = contactName;
        this.lastMessage = lastMessage;
        this.time = time;
    }

    public String getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(String targetUserId) {
        this.targetUserId = targetUserId;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }
}