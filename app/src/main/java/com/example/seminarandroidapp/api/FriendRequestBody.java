package com.example.seminarandroidapp.api;

import com.google.gson.annotations.SerializedName;

public class FriendRequestBody {
    @SerializedName("recipient_id")
    private final String recipientId;

    public FriendRequestBody(String recipientId) {
        this.recipientId = recipientId;
    }

    public String getRecipientId() {
        return recipientId;
    }
}