package com.example.seminarandroidapp.api;

import com.google.gson.annotations.SerializedName;

public class EncryptedMessage {
    private String id;
    @SerializedName("conversation_id")
    private String conversationId;
    @SerializedName("sender_id")
    private String senderId;
    @SerializedName("key_version")
    private int keyVersion;
    private String ciphertext;
    private String nonce;
    @SerializedName("auth_tag")
    private String authTag;
    @SerializedName("created_at")
    private String createdAt;
    private transient String decryptedText;

    public String getId() { return id; }
    public String getConversationId() { return conversationId; }
    public String getSenderId() { return senderId; }
    public int getKeyVersion() { return keyVersion; }
    public String getCiphertext() { return ciphertext; }
    public String getNonce() { return nonce; }
    public String getAuthTag() { return authTag; }
    public String getCreatedAt() { return createdAt; }
    public String getDecryptedText() { return decryptedText; }
    public void setDecryptedText(String decryptedText) { this.decryptedText = decryptedText; }
}
