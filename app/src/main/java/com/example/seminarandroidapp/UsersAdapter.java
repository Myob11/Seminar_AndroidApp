package com.example.seminarandroidapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.seminarandroidapp.api.User;
import com.example.seminarandroidapp.util.ProfileUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UsersAdapter extends RecyclerView.Adapter<UsersAdapter.ViewHolder> {

    private final List<User> users = new ArrayList<>();
    private final Set<String> sendingUserIds = new HashSet<>();
    private final Set<String> requestedUserIds = new HashSet<>();
    private final OnUserClickListener listener;

    public interface OnUserClickListener {
        void onAddFriendClick(User user);
    }

    public UsersAdapter(OnUserClickListener listener) {
        this.listener = listener;
    }

    public void updateUsers(List<User> newUsers) {
        users.clear();
        if (newUsers != null) {
            users.addAll(newUsers);
        }
        notifyDataSetChanged();
    }

    public void setSending(String userId, boolean sending) {
        if (sending) {
            sendingUserIds.add(userId);
        } else {
            sendingUserIds.remove(userId);
        }
        notifyUser(userId);
    }

    public void markRequested(String userId) {
        sendingUserIds.remove(userId);
        requestedUserIds.add(userId);
        notifyUser(userId);
    }

    private void notifyUser(String userId) {
        for (int i = 0; i < users.size(); i++) {
            if (userId != null && userId.equals(users.get(i).getId())) {
                notifyItemChanged(i);
                return;
            }
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        User user = users.get(position);
        String userId = user.getId();
        holder.userName.setText(user.getUsername());
        ProfileUtils.loadProfilePicture(holder.itemView.getContext(), user, holder.userAvatar);

        boolean requested = userId != null && requestedUserIds.contains(userId);
        boolean sending = userId != null && sendingUserIds.contains(userId);
        holder.btnAddFriend.setText(requested ? "Requested" : sending ? "Sending…" : "Add");
        holder.btnAddFriend.setEnabled(userId != null && !requested && !sending);
        holder.btnAddFriend.setOnClickListener(v -> listener.onAddFriendClick(user));
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView userAvatar;
        final TextView userName;
        final Button btnAddFriend;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            userAvatar = itemView.findViewById(R.id.userAvatar);
            userName = itemView.findViewById(R.id.userName);
            btnAddFriend = itemView.findViewById(R.id.btnAddFriend);
        }
    }
}