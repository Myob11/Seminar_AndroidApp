package com.example.seminarandroidapp;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.seminarandroidapp.util.ProfileUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ConversationsAdapter extends RecyclerView.Adapter<ConversationsAdapter.ViewHolder> {

    private List<Conversation> conversations;
    private List<Conversation> searchCandidates;
    private List<Conversation> friendSuggestions;
    private List<Conversation> filteredList;
    private boolean searchFocused;
    private OnConversationClickListener listener;
    private OnConversationLongClickListener longClickListener;

    public interface OnConversationClickListener {
        void onConversationClick(Conversation conversation);
    }

    public interface OnConversationLongClickListener {
        void onConversationLongClick(Conversation conversation);
    }

    public ConversationsAdapter(
            List<Conversation> conversations,
            OnConversationClickListener listener,
            OnConversationLongClickListener longClickListener
    ) {
        this.conversations = conversations;
        this.searchCandidates = new ArrayList<>(conversations);
        this.friendSuggestions = new ArrayList<>();
        this.filteredList = new ArrayList<>(conversations);
        this.listener = listener;
        this.longClickListener = longClickListener;
    }

    public void updateData(List<Conversation> newList, List<Conversation> searchableList,
                           List<Conversation> suggestions) {
        this.conversations = new ArrayList<>(newList);
        this.searchCandidates = new ArrayList<>(searchableList);
        this.friendSuggestions = new ArrayList<>(suggestions);
        notifyDataSetChanged();
    }

    public void updateConversationPreview(String conversationId, String preview, String time) {
        updatePreviewInList(conversations, conversationId, preview, time);
        updatePreviewInList(searchCandidates, conversationId, preview, time);
        updatePreviewInList(friendSuggestions, conversationId, preview, time);
        updatePreviewInList(filteredList, conversationId, preview, time);
        notifyDataSetChanged();
    }

    private void updatePreviewInList(List<Conversation> list, String conversationId,
                                     String preview, String time) {
        for (Conversation conversation : list) {
            if (conversationId.equals(conversation.getConversationId())) {
                conversation.setLastMessage(preview);
                conversation.setTime(time);
            }
        }
    }

    public void setSearchFocused(boolean focused) {
        searchFocused = focused;
    }

    public void filter(String query) {
        filteredList.clear();
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Conversation> source = normalizedQuery.isEmpty()
                ? (searchFocused ? friendSuggestions : conversations)
                : searchCandidates;
        if (normalizedQuery.isEmpty()) {
            filteredList.addAll(source);
        } else {
            for (Conversation conv : source) {
                String name = conv.getContactName();
                if (name != null && name.toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
                    filteredList.add(conv);
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_conversation, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Conversation conversation = filteredList.get(position);
        Context context = holder.itemView.getContext();
        
        holder.name.setText(conversation.getContactName());
        
        holder.lastMessage.setText(conversation.getLastMessage());

        holder.time.setText(conversation.getTime());
        
        boolean unread = conversation.getUnreadCount() > 0;
        holder.name.setTypeface(null, unread ? Typeface.BOLD : Typeface.NORMAL);
        holder.lastMessage.setTypeface(null, unread ? Typeface.BOLD : Typeface.NORMAL);
        if (unread) {
            holder.unreadCount.setText(conversation.getUnreadCount() > 99
                    ? "99+" : String.valueOf(conversation.getUnreadCount()));
            holder.unreadCount.setVisibility(View.VISIBLE);
        } else {
            holder.unreadCount.setVisibility(View.GONE);
        }
        
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.textColorSecondary, typedValue, true);
        holder.lastMessage.setTextColor(typedValue.data);
        
        ProfileUtils.loadProfilePicture(context,
                new com.example.seminarandroidapp.api.User(
                        conversation.getTargetUserId(), conversation.getContactName(), conversation.getProfilePicture()),
                holder.profileImage);
        
        holder.itemView.setOnClickListener(v -> {
            listener.onConversationClick(conversation);
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onConversationLongClick(conversation);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return filteredList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView profileImage;
        TextView name, lastMessage, time, unreadCount;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            profileImage = itemView.findViewById(R.id.imageViewProfile);
            name = itemView.findViewById(R.id.textViewName);
            lastMessage = itemView.findViewById(R.id.textViewLastMessage);
            time = itemView.findViewById(R.id.textViewTime);
            unreadCount = itemView.findViewById(R.id.textViewUnreadCount);
        }
    }
}