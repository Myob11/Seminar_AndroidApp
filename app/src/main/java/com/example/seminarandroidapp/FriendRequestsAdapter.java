package com.example.seminarandroidapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.seminarandroidapp.api.FriendRequest;
import com.example.seminarandroidapp.util.ProfileUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FriendRequestsAdapter extends RecyclerView.Adapter<FriendRequestsAdapter.ViewHolder> {

    public interface Listener {
        void onAccept(FriendRequest request);
        void onDecline(FriendRequest request);
    }

    private final List<FriendRequest> requests = new ArrayList<>();
    private final Set<String> busyRequestIds = new HashSet<>();
    private final Listener listener;

    public FriendRequestsAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setRequests(List<FriendRequest> newRequests) {
        requests.clear();
        if (newRequests != null) {
            requests.addAll(newRequests);
        }
        notifyDataSetChanged();
    }

    public void setBusy(String requestId, boolean busy) {
        if (busy) {
            busyRequestIds.add(requestId);
        } else {
            busyRequestIds.remove(requestId);
        }
        notifyRequest(requestId);
    }

    public void removeRequest(String requestId) {
        busyRequestIds.remove(requestId);
        for (int i = 0; i < requests.size(); i++) {
            if (requestId != null && requestId.equals(requests.get(i).getId())) {
                requests.remove(i);
                notifyItemRemoved(i);
                return;
            }
        }
    }

    public int getRequestCount() {
        return requests.size();
    }

    private void notifyRequest(String requestId) {
        for (int i = 0; i < requests.size(); i++) {
            if (requestId != null && requestId.equals(requests.get(i).getId())) {
                notifyItemChanged(i);
                return;
            }
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_friend_request, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FriendRequest request = requests.get(position);
        String requesterName = request.getRequester() != null ? request.getRequester().getUsername() : "Unknown user";
        holder.requesterName.setText(requesterName);
        ProfileUtils.loadProfilePicture(holder.itemView.getContext(), requesterName, holder.requesterAvatar);

        boolean busy = request.getId() != null && busyRequestIds.contains(request.getId());
        holder.acceptButton.setEnabled(!busy);
        holder.declineButton.setEnabled(!busy);
        holder.acceptButton.setText(busy ? "Please wait…" : "Accept");
        holder.declineButton.setText(busy ? "Please wait…" : "Decline");
        holder.acceptButton.setOnClickListener(v -> listener.onAccept(request));
        holder.declineButton.setOnClickListener(v -> listener.onDecline(request));
    }

    @Override
    public int getItemCount() {
        return requests.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView requesterAvatar;
        final TextView requesterName;
        final Button acceptButton;
        final Button declineButton;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            requesterAvatar = itemView.findViewById(R.id.requesterAvatar);
            requesterName = itemView.findViewById(R.id.requesterName);
            acceptButton = itemView.findViewById(R.id.acceptRequestButton);
            declineButton = itemView.findViewById(R.id.declineRequestButton);
        }
    }
}