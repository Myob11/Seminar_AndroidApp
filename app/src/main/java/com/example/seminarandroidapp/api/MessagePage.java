package com.example.seminarandroidapp.api;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class MessagePage {
    private List<EncryptedMessage> messages;
    @SerializedName("next_cursor")
    private String nextCursor;
    @SerializedName("has_more")
    private boolean hasMore;

    public List<EncryptedMessage> getMessages() { return messages; }
    public String getNextCursor() { return nextCursor; }
    public boolean hasMore() { return hasMore; }
}
