package com.example.seminarandroidapp.api;

import com.google.gson.annotations.SerializedName;

public class KeyEnvelopeResponse {
    @SerializedName("conversation_id")
    private String conversationId;
    @SerializedName("recipient_user_id")
    private String recipientUserId;
    @SerializedName("key_version")
    private int keyVersion;
    @SerializedName("encrypted_conversation_key")
    private String encryptedConversationKey;

    public int getKeyVersion() {
        return keyVersion;
    }

    public String getEncryptedConversationKey() {
        return encryptedConversationKey;
    }
}
