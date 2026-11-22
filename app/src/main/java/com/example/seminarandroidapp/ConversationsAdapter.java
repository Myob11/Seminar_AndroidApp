package com.example.seminarandroidapp;

import android.content.Context;
import android.graphics.Typeface;
import android.util.Base64;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.seminarandroidapp.util.Prefs;
import com.example.seminarandroidapp.util.ProfileUtils;
import com.example.seminarandroidapp.util.SignalManager;
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
        
        String lastMsg = conversation.getLastMessage();
        String targetUserId = conversation.getTargetUserId();
        String secretB64 = (targetUserId != null) ? Prefs.getSharedSecret(targetUserId) : null;

        if (conversation.hasDirectConversation() && secretB64 != null && lastMsg != null && !lastMsg.isEmpty() &&
            !lastMsg.equals("No messages yet") && !lastMsg.equals("Tap to chat")) {
            try {
                byte[] secret = Base64.decode(secretB64, Base64.NO_WRAP);
                lastMsg = SignalManager.decrypt(lastMsg, secret);
            } catch (Exception e) {
                lastMsg = "[Encrypted Message]";
            }
        }
        holder.lastMessage.setText(lastMsg);

        holder.time.setText(conversation.getTime());
        
        holder.name.setTypeface(null, Typeface.NORMAL);
        holder.lastMessage.setTypeface(null, Typeface.NORMAL);
        
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
        TextView name, lastMessage, time;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            profileImage = itemView.findViewById(R.id.imageViewProfile);
            name = itemView.findViewById(R.id.textViewName);
            lastMessage = itemView.findViewById(R.id.textViewLastMessage);
            time = itemView.findViewById(R.id.textViewTime);
        }
    }
}