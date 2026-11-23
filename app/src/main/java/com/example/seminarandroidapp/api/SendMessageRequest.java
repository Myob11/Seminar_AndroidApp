package com.example.seminarandroidapp.api;

import com.google.gson.annotations.SerializedName;

public class SendMessageRequest {
    @SerializedName("key_version")
    private final int keyVersion;
    private final String ciphertext;
    private final String nonce;
    @SerializedName("auth_tag")
    private final String authTag;

    public SendMessageRequest(int keyVersion, String ciphertext, String nonce, String authTag) {
        this.keyVersion = keyVersion;
        this.ciphertext = ciphertext;
        this.nonce = nonce;
        this.authTag = authTag;
    }
}
