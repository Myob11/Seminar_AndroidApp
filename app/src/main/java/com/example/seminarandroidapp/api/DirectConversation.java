package com.example.seminarandroidapp.api;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class DirectConversation {
    private String id;
    private String type;
    @SerializedName("current_key_version")
    private int currentKeyVersion;
    @SerializedName(value = "recipient", alternate = {"friend", "other_user"})
    private User recipient;
    @SerializedName("recipient_id")
    private String recipientId;
    private List<User> participants;
    @SerializedName("last_message")
    private EncryptedMessage lastMessage;
    @SerializedName("unread_count")
    private int unreadCount;

    public DirectConversation() {
    }

    public DirectConversation(String id, String type, int currentKeyVersion, User recipient) {
        this.id = id;
        this.type = type;
        this.currentKeyVersion = currentKeyVersion;
        this.recipient = recipient;
        this.recipientId = recipient != null ? recipient.getId() : null;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public int getCurrentKeyVersion() {
        return currentKeyVersion;
    }

    public User getRecipient() {
        return recipient;
    }

    public void setRecipient(User recipient) {
        this.recipient = recipient;
        if (recipient != null) {
            this.recipientId = recipient.getId();
        }
    }

    public String getRecipientId() {
        return recipientId;
    }

    public List<User> getParticipants() {
        return participants;
    }

    public EncryptedMessage getLastMessage() {
        return lastMessage;
    }

    public int getUnreadCount() {
        return unreadCount;
    }

    public User getOtherParticipant(String currentUserId) {
        if (recipient != null) {
            return recipient;
        }
        if (participants != null) {
            for (User participant : participants) {
                if (participant != null && participant.getId() != null
                        && !participant.getId().equals(currentUserId)) {
                    return participant;
                }
            }
        }
        return null;
    }
}