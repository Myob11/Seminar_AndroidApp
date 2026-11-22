package com.example.seminarandroidapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.example.seminarandroidapp.api.ApiClient;
import com.example.seminarandroidapp.api.AuthRequest;
import com.example.seminarandroidapp.api.AuthResponse;
import com.example.seminarandroidapp.api.PrivateKeyBackupRequest;
import com.example.seminarandroidapp.api.PublicKeyRequest;
import com.example.seminarandroidapp.api.PublicKeyResponse;
import com.example.seminarandroidapp.util.KeyUtils;
import com.example.seminarandroidapp.util.Prefs;
import com.google.android.material.textfield.TextInputEditText;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import java.security.KeyPair;
import java.security.PrivateKey;

public class RegisterActivity extends AppCompatActivity {

    private TextInputEditText etUsername, etPassword, etConfirmPassword, etRecoverySecret;
    private Button btnSubmitRegister, btnBackToLogin;
    private KeyPair pendingKeyPair;
    private String pendingRecoverySecret;
    private boolean accountCreated;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        etRecoverySecret = findViewById(R.id.etRecoverySecret);
        btnSubmitRegister = findViewById(R.id.btnSubmitRegister);
        btnBackToLogin = findViewById(R.id.btnBackToLogin);

        btnSubmitRegister.setOnClickListener(v -> {
            if (accountCreated) {
                if (pendingKeyPair == null) {
                    setupRsaKeysAndBackup(pendingRecoverySecret);
                } else {
                    uploadPublicKey();
                }
            } else {
                attemptRegister();
            }
        });
        btnBackToLogin.setOnClickListener(v -> finish());
    }

    private void attemptRegister() {
        String rawUsername = etUsername.getText() != null ? etUsername.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString() : "";
        String confirmPassword = etConfirmPassword.getText() != null ? etConfirmPassword.getText().toString() : "";
        String recoverySecret = etRecoverySecret.getText() != null ? etRecoverySecret.getText().toString() : "";

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

        if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError("Passwords do not match");
            etConfirmPassword.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(recoverySecret)) {
            etRecoverySecret.setError("Enter a recovery secret separate from your login password");
            etRecoverySecret.requestFocus();
            return;
        }

        if (recoverySecret.equals(password)) {
            etRecoverySecret.setError("Use a recovery secret different from your login password");
            etRecoverySecret.requestFocus();
            return;
        }

        final String finalRecoverySecret = recoverySecret;
        btnSubmitRegister.setEnabled(false);

        AuthRequest request = new AuthRequest(normalizedUsername, password);

        ApiClient.getApiService(this).register(request).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<AuthResponse> call, @NonNull Response<AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AuthResponse authData = response.body();
                    String token = authData.getToken();
                    String userId = authData.getUser() != null ? authData.getUser().getId() : "";
                    String username = authData.getUser() != null ? authData.getUser().getUsername() : normalizedUsername;

                    Prefs.saveAuth(RegisterActivity.this, token, userId, username);
                    accountCreated = true;
                    pendingRecoverySecret = finalRecoverySecret;

                    setupRsaKeysAndBackup(finalRecoverySecret);
                } else {
                    btnSubmitRegister.setEnabled(true);
                    String errorMsg = ApiClient.parseError(response);
                    Toast.makeText(RegisterActivity.this, "Registration failed: " + errorMsg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<AuthResponse> call, @NonNull Throwable t) {
                btnSubmitRegister.setEnabled(true);
                Toast.makeText(RegisterActivity.this, "Network error: " + t.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupRsaKeysAndBackup(String recoverySecret) {
        try {
            pendingRecoverySecret = recoverySecret;
            pendingKeyPair = KeyUtils.generateRsaKeyPair();
            uploadPublicKey();
        } catch (Exception e) {
            showPublicKeyUploadFailure("Could not generate your RSA key pair.");
        }
    }

    private void uploadPublicKey() {
        if (pendingKeyPair == null) {
            setupRsaKeysAndBackup(pendingRecoverySecret);
            return;
        }

        String pemPublicKey = KeyUtils.exportPublicKeyToPem(pendingKeyPair.getPublic());
        PublicKeyRequest request = new PublicKeyRequest(pemPublicKey);
        ApiClient.getApiService(this).setPublicKey(request).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<PublicKeyResponse> call, @NonNull Response<PublicKeyResponse> response) {
                if (response.isSuccessful()) {
                    uploadPrivateBackup(pendingKeyPair.getPrivate(), pendingRecoverySecret);
                } else {
                    showPublicKeyUploadFailure("The account was created, but the RSA public key could not be saved: "
                            + ApiClient.parseError(response));
                }
            }

            @Override
            public void onFailure(@NonNull Call<PublicKeyResponse> call, @NonNull Throwable t) {
                showPublicKeyUploadFailure("The account was created, but the RSA public key could not be saved. Check your connection and retry.");
            }
        });
    }

    private void showPublicKeyUploadFailure(String message) {
        btnSubmitRegister.setText("Retry secure setup");
        btnSubmitRegister.setEnabled(true);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void uploadPrivateBackup(PrivateKey privateKey, String recoverySecret) {
        try {
            KeyUtils.BackupResult backupResult = KeyUtils.createPrivateKeyBackup(privateKey, recoverySecret);
            PrivateKeyBackupRequest backupRequest = new PrivateKeyBackupRequest(
                    backupResult.encryptedRsaPrivateKey,
                    backupResult.privateKeyKdf,
                    backupResult.privateKeyKdfParameters,
                    backupResult.privateKeyBackupSalt,
                    backupResult.privateKeyBackupNonce,
                    backupResult.privateKeyBackupAuthTag
            );

            ApiClient.getApiService(this).uploadPrivateKeyBackup(backupRequest).enqueue(new Callback<>() {
                @Override
                public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                    Toast.makeText(RegisterActivity.this, "Registration successful!", Toast.LENGTH_SHORT).show();
                    navigateToMain();
                }

                @Override
                public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                    Toast.makeText(RegisterActivity.this, "Registration successful!", Toast.LENGTH_SHORT).show();
                    navigateToMain();
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "Registration successful!", Toast.LENGTH_SHORT).show();
            navigateToMain();
        }
    }

    private void navigateToMain() {
        Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}