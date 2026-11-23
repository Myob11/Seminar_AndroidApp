package com.example.seminarandroidapp.api;

import com.google.gson.annotations.SerializedName;

public class FriendRequest {
    private String id;
    private User requester;
    private User recipient;
    private String status;
    @SerializedName("created_at")
    private String createdAt;

    public String getId() {
        return id;
    }

    public User getRequester() {
        return requester;
    }

    public User getRecipient() {
        return recipient;
    }

    public String getStatus() {
        return status;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}