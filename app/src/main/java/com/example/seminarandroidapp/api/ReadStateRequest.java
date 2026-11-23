package com.example.seminarandroidapp.api;

import com.google.gson.annotations.SerializedName;

public class ReadStateRequest {
    @SerializedName("through_message_id")
    private final String throughMessageId;

    public ReadStateRequest(String throughMessageId) {
        this.throughMessageId = throughMessageId;
    }
}
