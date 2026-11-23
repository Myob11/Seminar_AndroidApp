package com.example.seminarandroidapp.api;

import com.google.gson.annotations.SerializedName;

public class KeyEnvelopeRequest {
    @SerializedName("key_version")
    private final int keyVersion;
    @SerializedName("encrypted_conversation_key")
    private final String encryptedConversationKey;

    public KeyEnvelopeRequest(int keyVersion, String encryptedConversationKey) {
        this.keyVersion = keyVersion;
        this.encryptedConversationKey = encryptedConversationKey;
    }
}
