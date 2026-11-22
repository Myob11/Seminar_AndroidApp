package com.example.seminarandroidapp.api;

import com.google.gson.annotations.SerializedName;

public class FriendRequest {
    private String id;
    private User requester;
    private String status;
    @SerializedName("created_at")
    private String createdAt;

    public String getId() {
        return id;
    }

    public User getRequester() {
        return requester;
    }

    public String getStatus() {
        return status;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}