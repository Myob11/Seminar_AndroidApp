package com.example.seminarandroidapp;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.seminarandroidapp.api.EncryptedMessage;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatMessagesAdapter extends RecyclerView.Adapter<ChatMessagesAdapter.MessageHolder> {
    private final List<EncryptedMessage> messages = new ArrayList<>();
    private final String currentUserId;

    public ChatMessagesAdapter(String currentUserId) {
        this.currentUserId = currentUserId;
    }

    public List<EncryptedMessage> getMessages() {
        return new ArrayList<>(messages);
    }

    public void setMessages(List<EncryptedMessage> updated) {
        messages.clear();
        if (updated != null) {
            messages.addAll(updated);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MessageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message, parent, false);
        return new MessageHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageHolder holder, int position) {
        EncryptedMessage message = messages.get(position);
        boolean sent = currentUserId != null && currentUserId.equals(message.getSenderId());
        holder.row.setGravity(sent ? Gravity.END : Gravity.START);
        holder.bubble.setBackgroundResource(sent ? R.drawable.bg_message_sent : R.drawable.bg_message_received);
        holder.content.setText(message.getDecryptedText() == null ? "Unable to decrypt message" : message.getDecryptedText());
        holder.content.setTextColor(sent ? 0xFFFFFFFF : resolvePrimaryTextColor(holder.itemView.getContext()));
        holder.time.setText(formatTime(message.getCreatedAt()));
        holder.time.setTextColor(sent ? 0xFFDCEBFF : 0xFF888888);
    }

    private int resolvePrimaryTextColor(Context context) {
        android.util.TypedValue value = new android.util.TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.textColorPrimary, value, true);
        return value.data;
    }

    private String formatTime(String timestamp) {
        if (timestamp == null || timestamp.length() < 16) return "";
        try {
            String normalized = timestamp.replace("T", " ").replace("Z", "");
            Date date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).parse(normalized);
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(date);
        } catch (ParseException ignored) {
            return timestamp.substring(Math.max(0, timestamp.indexOf('T') + 1),
                    Math.min(timestamp.length(), timestamp.indexOf('T') + 6));
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class MessageHolder extends RecyclerView.ViewHolder {
        final LinearLayout row;
        final LinearLayout bubble;
        final TextView content;
        final TextView time;

        MessageHolder(@NonNull View itemView) {
            super(itemView);
            row = itemView.findViewById(R.id.messageRow);
            bubble = itemView.findViewById(R.id.messageBubble);
            content = itemView.findViewById(R.id.messageText);
            time = itemView.findViewById(R.id.messageTime);
        }
    }
}
