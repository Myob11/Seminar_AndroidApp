package com.example.seminarandroidapp;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.example.seminarandroidapp.api.ApiClient;
import com.example.seminarandroidapp.api.AuthRequest;
import com.example.seminarandroidapp.api.AuthResponse;
import com.example.seminarandroidapp.api.HealthResponse;
import com.example.seminarandroidapp.util.Prefs;
import com.google.android.material.textfield.TextInputEditText;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private TextView tvServerStatus;
    private TextInputEditText etUsername, etPassword;
    private Button btnLogin, btnRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Prefs.isLoggedIn(this)) {
            navigateToMain();
            return;
        }

        setContentView(R.layout.activity_login);

        tvServerStatus = findViewById(R.id.tvServerStatus);
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);

        checkServerHealth();

        btnLogin.setOnClickListener(v -> attemptLogin());
        btnRegister.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });
    }

    private void checkServerHealth() {
        tvServerStatus.setText("Checking server status...");
        tvServerStatus.setTextColor(Color.GRAY);

        ApiClient.getApiService(this).checkHealth().enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<HealthResponse> call, @NonNull Response<HealthResponse> response) {
                if (response.isSuccessful() && response.body() != null && "ok".equalsIgnoreCase(response.body().getStatus())) {
                    tvServerStatus.setText("Server Active");
                    tvServerStatus.setTextColor(Color.parseColor("#4CAF50"));
                } else {
                    tvServerStatus.setText("Server Inactive");
                    tvServerStatus.setTextColor(Color.RED);
                    Toast.makeText(LoginActivity.this, "Server inactive", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<HealthResponse> call, @NonNull Throwable t) {
                tvServerStatus.setText("Server Inactive");
                tvServerStatus.setTextColor(Color.RED);
                Toast.makeText(LoginActivity.this, "Server inactive", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void attemptLogin() {
        String rawUsername = etUsername.getText() != null ? etUsername.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString() : "";

        String normalizedUsername = rawUsername.toLowerCase();

        if (TextUtils.isEmpty(normalizedUsername) || !normalizedUsername.matches("^[a-z0-9_]{3,64}$")) {
            etUsername.setError("Username must be 3-64 lowercase letters, digits, or underscores");
            etUsername.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password) || password.length() < 12 || password.length() > 200) {
            etPassword.setError("Password must be 12-200 characters long");
            etPassword.requestFocus();
            return;
        }

        btnLogin.setEnabled(false);
        AuthRequest request = new AuthRequest(normalizedUsername, password);

        ApiClient.getApiService(this).login(request).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<AuthResponse> call, @NonNull Response<AuthResponse> response) {
                btnLogin.setEnabled(true);
                if (response.isSuccessful() && response.body() != null) {
                    AuthResponse authData = response.body();
                    String token = authData.getToken();
                    String userId = authData.getUser() != null ? authData.getUser().getId() : "";
                    String username = authData.getUser() != null ? authData.getUser().getUsername() : normalizedUsername;

                    Prefs.saveAuth(LoginActivity.this, token, userId, username);
                    Toast.makeText(LoginActivity.this, "Login successful!", Toast.LENGTH_SHORT).show();
                    navigateToMain();
                } else {
                    String errorMsg = ApiClient.parseError(response);
                    Toast.makeText(LoginActivity.this, "Login failed: " + errorMsg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<AuthResponse> call, @NonNull Throwable t) {
                btnLogin.setEnabled(true);
                Toast.makeText(LoginActivity.this, "Network error: " + t.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void navigateToMain() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}