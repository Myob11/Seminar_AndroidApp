package com.example.seminarandroidapp.util;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

public class Prefs {
    private static final String PREF_NAME = "app_prefs";
    private static final String KEY_TOKEN = "jwt_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_DARK_MODE = "dark_mode";
    private static final String KEY_OUTGOING_REQUESTS_PREFIX = "outgoing_requests_";
    private static final String KEY_SHARED_SECRET_PREFIX = "secret_";
    private static SharedPreferences sharedPreferences;

    public static void init(Context context) {
        if (sharedPreferences == null && context != null) {
            sharedPreferences = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        }
    }

    public static void saveAuth(Context context, String token, String userId, String username) {
        init(context);
        if (sharedPreferences != null) {
            sharedPreferences.edit()
                    .putString(KEY_TOKEN, token)
                    .putString(KEY_USER_ID, userId)
                    .putString(KEY_USERNAME, username)
                    .apply();
        }
    }

    public static String getToken(Context context) {
        init(context);
        return sharedPreferences != null ? sharedPreferences.getString(KEY_TOKEN, null) : null;
    }

    public static String getUserId(Context context) {
        init(context);
        return sharedPreferences != null ? sharedPreferences.getString(KEY_USER_ID, null) : null;
    }

    public static String getUsername(Context context) {
        init(context);
        return sharedPreferences != null ? sharedPreferences.getString(KEY_USERNAME, null) : null;
    }

    public static boolean isDarkMode(Context context) {
        init(context);
        return sharedPreferences != null && sharedPreferences.getBoolean(KEY_DARK_MODE, false);
    }

    public static void saveDarkMode(Context context, boolean enabled) {
        init(context);
        if (sharedPreferences != null) {
            sharedPreferences.edit().putBoolean(KEY_DARK_MODE, enabled).apply();
        }
    }

    public static Set<String> getOutgoingFriendRequestIds(Context context) {
        init(context);
        String userId = getUserId(context);
        if (sharedPreferences == null || userId == null) {
            return new HashSet<>();
        }
        return new HashSet<>(sharedPreferences.getStringSet(
                KEY_OUTGOING_REQUESTS_PREFIX + userId, new HashSet<>()));
    }

    public static void replaceOutgoingFriendRequestIds(Context context, Set<String> requestIds) {
        init(context);
        String userId = getUserId(context);
        if (sharedPreferences != null && userId != null) {
            sharedPreferences.edit()
                    .putStringSet(KEY_OUTGOING_REQUESTS_PREFIX + userId,
                            requestIds == null ? new HashSet<>() : new HashSet<>(requestIds))
                    .apply();
        }
    }

    public static void addOutgoingFriendRequest(Context context, String targetUserId) {
        if (targetUserId == null) {
            return;
        }
        Set<String> requestIds = getOutgoingFriendRequestIds(context);
        requestIds.add(targetUserId);
        String userId = getUserId(context);
        if (sharedPreferences != null && userId != null) {
            sharedPreferences.edit()
                    .putStringSet(KEY_OUTGOING_REQUESTS_PREFIX + userId, requestIds)
                    .apply();
        }
    }

    public static void removeOutgoingFriendRequest(Context context, String targetUserId) {
        if (targetUserId == null) {
            return;
        }
        Set<String> requestIds = getOutgoingFriendRequestIds(context);
        if (requestIds.remove(targetUserId)) {
            String userId = getUserId(context);
            if (sharedPreferences != null && userId != null) {
                sharedPreferences.edit()
                        .putStringSet(KEY_OUTGOING_REQUESTS_PREFIX + userId, requestIds)
                        .apply();
            }
        }
    }

    public static boolean isLoggedIn(Context context) {
        String token = getToken(context);
        return token != null && !token.isEmpty();
    }

    public static void clearAuth(Context context) {
        init(context);
        if (sharedPreferences != null) {
            sharedPreferences.edit()
                    .remove(KEY_TOKEN)
                    .remove(KEY_USER_ID)
                    .remove(KEY_USERNAME)
                    .apply();
        }
    }

    public static String getSharedSecret(String targetUserId) {
        if (sharedPreferences == null) {
            return null;
        }
        return sharedPreferences.getString(KEY_SHARED_SECRET_PREFIX + targetUserId, null);
    }

    public static void saveSharedSecret(Context context, String targetUserId, String secretB64) {
        init(context);
        if (sharedPreferences != null) {
            sharedPreferences.edit().putString(KEY_SHARED_SECRET_PREFIX + targetUserId, secretB64).apply();
        }
    }
}