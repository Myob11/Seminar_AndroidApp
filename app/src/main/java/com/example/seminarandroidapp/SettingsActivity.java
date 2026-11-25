package com.example.seminarandroidapp;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;

import com.example.seminarandroidapp.api.ApiClient;
import com.example.seminarandroidapp.api.ApiService;
import com.example.seminarandroidapp.api.User;
import com.example.seminarandroidapp.util.Prefs;
import com.example.seminarandroidapp.util.ProfileUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SettingsActivity extends AppCompatActivity {
    private static final int MAX_PROFILE_PICTURE_BYTES = 5 * 1024 * 1024;

    private ImageView avatarView;
    private TextView usernameView;
    private User profile;
    private Call<User> profileCall;
    private Call<User> uploadCall;
    private Call<ResponseBody> removeCall;
    private Call<ResponseBody> logoutCall;

    private final ActivityResultLauncher<String> imagePicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onImagePicked);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.settingsToolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.settings);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        avatarView = findViewById(R.id.imageViewAvatar);
        usernameView = findViewById(R.id.textViewUsernameDisplay);
        String username = Prefs.getUsername(this);
        usernameView.setText(username == null ? "" : username);
        ProfileUtils.loadProfilePicture(this, username, avatarView);

        avatarView.setOnClickListener(view -> showProfilePictureOptions());
        avatarView.setOnLongClickListener(view -> {
            showProfilePictureOptions();
            return true;
        });

        SwitchCompat themeSwitch = findViewById(R.id.switchTheme);
        themeSwitch.setChecked(Prefs.isDarkMode(this));
        themeSwitch.setOnCheckedChangeListener((button, enabled) -> {
            Prefs.saveDarkMode(this, enabled);
            AppCompatDelegate.setDefaultNightMode(
                    enabled ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
            );
        });

        Button logoutButton = findViewById(R.id.buttonLogout);
        logoutButton.setOnClickListener(view -> logout());

        loadProfile();
    }

    private void loadProfile() {
        if (profileCall != null) {
            profileCall.cancel();
        }
        Call<User> call = ApiClient.getApiService(this).getMe();
        profileCall = call;
        call.enqueue(new Callback<User>() {
            @Override
            public void onResponse(@NonNull Call<User> call, @NonNull Response<User> response) {
                if (profileCall != call || call.isCanceled()) {
                    return;
                }
                profileCall = null;
                if (response.isSuccessful() && response.body() != null) {
                    profile = response.body();
                    if (profile.getUsername() != null) {
                        usernameView.setText(profile.getUsername());
                    }
                    ProfileUtils.loadProfilePicture(SettingsActivity.this, profile, avatarView);
                } else {
                    Toast.makeText(SettingsActivity.this, R.string.could_not_load_profile, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<User> call, @NonNull Throwable throwable) {
                if (profileCall == call) {
                    profileCall = null;
                }
                if (!call.isCanceled()) {
                    Toast.makeText(SettingsActivity.this, R.string.could_not_load_profile, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void showProfilePictureOptions() {
        String[] options = profile != null && profile.getProfilePicture() != null
                && !profile.getProfilePicture().isEmpty()
                ? new String[]{getString(R.string.change_profile_picture), getString(R.string.remove_profile_picture)}
                : new String[]{getString(R.string.change_profile_picture)};
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.profile_picture)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        imagePicker.launch("image/*");
                    } else {
                        removeProfilePicture();
                    }
                })
                .show();
    }

    private void onImagePicked(Uri uri) {
        if (uri == null) {
            return;
        }

        String mimeType = getContentResolver().getType(uri);
        if (mimeType == null || !(mimeType.equalsIgnoreCase("image/jpeg")
                || mimeType.equalsIgnoreCase("image/png")
                || mimeType.equalsIgnoreCase("image/webp"))) {
            Toast.makeText(this, R.string.unsupported_image_type, Toast.LENGTH_SHORT).show();
            return;
        }

        byte[] imageBytes;
        try (InputStream input = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) {
                throw new IllegalStateException("Unable to open selected image");
            }
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_PROFILE_PICTURE_BYTES) {
                    Toast.makeText(this, R.string.profile_picture_too_large, Toast.LENGTH_SHORT).show();
                    return;
                }
                output.write(buffer, 0, count);
            }
            imageBytes = output.toByteArray();
        } catch (Exception exception) {
            Toast.makeText(this, R.string.profile_picture_upload_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        String fileName = getFileName(uri);
        RequestBody requestBody = RequestBody.create(MediaType.parse(mimeType), imageBytes);
        MultipartBody.Part part = MultipartBody.Part.createFormData("profile_picture", fileName, requestBody);
        if (uploadCall != null) {
            uploadCall.cancel();
        }
        Call<User> call = ApiClient.getApiService(this).uploadProfilePicture(part);
        uploadCall = call;
        call.enqueue(new Callback<User>() {
            @Override
            public void onResponse(@NonNull Call<User> call, @NonNull Response<User> response) {
                if (uploadCall != call || call.isCanceled()) {
                    return;
                }
                uploadCall = null;
                if (response.isSuccessful() && response.body() != null) {
                    profile = response.body();
                    ProfileUtils.loadProfilePicture(SettingsActivity.this, profile, avatarView);
                    Toast.makeText(SettingsActivity.this, R.string.profile_picture_updated, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(SettingsActivity.this, ApiClient.parseError(response), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<User> call, @NonNull Throwable throwable) {
                if (uploadCall == call) {
                    uploadCall = null;
                }
                if (!call.isCanceled()) {
                    Toast.makeText(SettingsActivity.this, R.string.profile_picture_upload_failed, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private String getFileName(Uri uri) {
        String fileName = "profile-picture";
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String name = cursor.getString(index);
                    if (name != null && !name.trim().isEmpty()) {
                        fileName = name;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return fileName;
    }

    private void removeProfilePicture() {
        if (removeCall != null) {
            return;
        }
        Call<ResponseBody> call = ApiClient.getApiService(this).removeProfilePicture();
        removeCall = call;
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (removeCall != call || call.isCanceled()) {
                    return;
                }
                removeCall = null;
                if (response.isSuccessful()) {
                    if (profile != null) {
                        profile.setProfilePicture(null);
                    }
                    ProfileUtils.loadProfilePicture(SettingsActivity.this,
                            Prefs.getUsername(SettingsActivity.this), avatarView);
                    Toast.makeText(SettingsActivity.this, R.string.profile_picture_removed, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(SettingsActivity.this, ApiClient.parseError(response), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable throwable) {
                if (removeCall == call) {
                    removeCall = null;
                }
                if (!call.isCanceled()) {
                    Toast.makeText(SettingsActivity.this, R.string.network_error, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void logout() {
        if (logoutCall != null) {
            return;
        }
        logoutCall = ApiClient.getApiService(this).logout();
        logoutCall.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (logoutCall == call) {
                    logoutCall = null;
                }
                finishLogout();
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable throwable) {
                if (logoutCall == call) {
                    logoutCall = null;
                }
                if (!call.isCanceled()) {
                    finishLogout();
                }
            }
        });
    }

    private void finishLogout() {
        Prefs.clearAuth(this);
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        cancel(profileCall);
        cancel(uploadCall);
        cancel(removeCall);
        cancel(logoutCall);
        super.onDestroy();
    }

    private void cancel(Call<?> call) {
        if (call != null) {
            call.cancel();
        }
    }
}