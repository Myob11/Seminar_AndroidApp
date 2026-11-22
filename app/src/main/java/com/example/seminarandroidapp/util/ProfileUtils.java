package com.example.seminarandroidapp.util;

import android.content.Context;
import android.widget.ImageView;
import com.bumptech.glide.Glide;
import com.example.seminarandroidapp.R;

public class ProfileUtils {

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
                    .placeholder(R.mipmap.ic_launcher_round)
                    .error(R.mipmap.ic_launcher_round)
                    .into(imageView);
        } else {
            imageView.setImageResource(R.mipmap.ic_launcher_round);
        }
    }
}