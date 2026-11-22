package com.example.seminarandroidapp.api;

import com.google.gson.annotations.SerializedName;

public class DirectConversationRequest {
    @SerializedName("recipient_id")
    private final String recipientId;

    public DirectConversationRequest(String recipientId) {
        this.recipientId = recipientId;
    }

    public String getRecipientId() {
        return recipientId;
    }
}