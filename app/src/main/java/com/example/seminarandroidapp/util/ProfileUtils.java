package com.example.seminarandroidapp.util;

import android.content.Context;
import android.widget.ImageView;
import com.bumptech.glide.Glide;
import com.example.seminarandroidapp.R;
import com.example.seminarandroidapp.api.User;

public class ProfileUtils {

    public static void loadProfilePicture(Context context, User user, ImageView imageView) {
        if (context == null || imageView == null) {
            return;
        }
        String profilePicture = user != null ? user.getProfilePicture() : null;
        if (profilePicture == null || profilePicture.isEmpty()) {
            loadProfilePicture(context, user != null ? user.getUsername() : null, imageView);
            return;
        }

        String imageUrl = profilePicture.startsWith("http")
                ? profilePicture
                : "https://backend.kbnet.si" + (profilePicture.startsWith("/") ? profilePicture : "/" + profilePicture);
        Glide.with(context)
                .load(imageUrl)
                .circleCrop()
                .placeholder(R.mipmap.ic_launcher_round)
                .error(R.mipmap.ic_launcher_round)
                .into(imageView);
    }

    public static void loadProfilePicture(Context context, String username, ImageView imageView) {
        if (context == null || imageView == null) {
            return;
        }
        String avatarUrl = (username != null && !username.isEmpty())
                ? "https://ui-avatars.com/api/?name=" + username + "&background=random"
                : null;

        if (avatarUrl != null) {
            Glide.with(context)
                    .load(avatarUrl)
                    .circleCrop()
                    .placeholder(R.mipmap.ic_launcher_round)
                    .error(R.mipmap.ic_launcher_round)
                    .into(imageView);
        } else {
            imageView.setImageResource(R.mipmap.ic_launcher_round);
        }
    }
}