package com.example.seminarandroidapp;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.seminarandroidapp.api.ApiClient;
import com.example.seminarandroidapp.api.ApiService;
import com.example.seminarandroidapp.api.EncryptedMessage;
import com.example.seminarandroidapp.api.KeyEnvelopeRequest;
import com.example.seminarandroidapp.api.KeyEnvelopeResponse;
import com.example.seminarandroidapp.api.MessagePage;
import com.example.seminarandroidapp.api.PrivateKeyBackupResponse;
import com.example.seminarandroidapp.api.PublicKeyResponse;
import com.example.seminarandroidapp.api.SendMessageRequest;
import com.example.seminarandroidapp.util.KeyUtils;
import com.example.seminarandroidapp.util.LocalKeyStore;
import com.example.seminarandroidapp.util.Prefs;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Response;

public class ChatActivity extends AppCompatActivity {
    public static final String EXTRA_CONVERSATION_ID = "conversation_id";
    public static final String EXTRA_FRIEND_ID = "friend_id";
    public static final String EXTRA_FRIEND_NAME = "friend_name";
    public static final String EXTRA_FRIEND_PICTURE = "friend_picture";
    public static final String EXTRA_INITIALIZE_KEY = "initialize_key";

    private static final long POLL_INTERVAL_MS = 3_000L;
    private static final int MESSAGE_PAGE_SIZE = 50;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService cryptoExecutor = Executors.newSingleThreadExecutor();
    private final Map<String, EncryptedMessage> messagesById = new HashMap<>();
    private volatile Call<?> activePollCall;
    private final Runnable pollRunnable = new Runnable() {
        @Override public void run() {
            if (isResumed && !polling) {
                pollConversation();
            }
            if (isResumed) handler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    private String conversationId;
    private String friendId;
    private String currentUserId;
    private boolean initializeKey;
    private volatile boolean isResumed;
    private volatile boolean polling;
    private boolean sending;
    private boolean keySetupRunning;
    private int keyVersion = 1;
    private byte[] conversationKey;
    private PrivateKey privateKey;

    private TextView statusView;
    private EditText messageInput;
    private Button sendButton;
    private RecyclerView recyclerView;
    private ChatMessagesAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_chat);
        applyComposerInsets(findViewById(R.id.chatComposer));

        conversationId = getIntent().getStringExtra(EXTRA_CONVERSATION_ID);
        friendId = getIntent().getStringExtra(EXTRA_FRIEND_ID);
        currentUserId = Prefs.getUserId(this);
        initializeKey = getIntent().getBooleanExtra(EXTRA_INITIALIZE_KEY, false);

        Toolbar toolbar = findViewById(R.id.chatToolbar);
        applyStatusBarInset(toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getIntent().getStringExtra(EXTRA_FRIEND_NAME) != null
                    ? getIntent().getStringExtra(EXTRA_FRIEND_NAME) : getString(R.string.app_name));
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        statusView = findViewById(R.id.chatStatus);
        messageInput = findViewById(R.id.chatMessageInput);
        sendButton = findViewById(R.id.chatSendButton);
        recyclerView = findViewById(R.id.chatMessages);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChatMessagesAdapter(currentUserId);
        recyclerView.setAdapter(adapter);
        sendButton.setOnClickListener(v -> sendMessage());

        if (TextUtils.isEmpty(conversationId) || TextUtils.isEmpty(friendId) || TextUtils.isEmpty(currentUserId)) {
            Toast.makeText(this, R.string.chat_key_setup_failed, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        try {
            privateKey = LocalKeyStore.loadPrivateKey(this, currentUserId);
        } catch (Exception ignored) {
            privateKey = null;
        }
        if (privateKey == null) {
            requestRecoverySecret();
        } else {
            startKeySetupOrPolling();
        }
    }

    private void applyStatusBarInset(Toolbar toolbar) {
        int baseHeight = toolbar.getLayoutParams().height;
        int left = toolbar.getPaddingLeft();
        int top = toolbar.getPaddingTop();
        int right = toolbar.getPaddingRight();
        int bottom = toolbar.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(toolbar, (view, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.statusBars()
                    | WindowInsetsCompat.Type.displayCutout());
            view.getLayoutParams().height = baseHeight + safe.top;
            view.setPadding(left, top + safe.top, right, bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(toolbar);
    }

    private void applyComposerInsets(View composer) {
        int baseLeft = composer.getPaddingLeft();
        int baseTop = composer.getPaddingTop();
        int baseRight = composer.getPaddingRight();
        int baseBottom = composer.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(composer, (view, insets) -> {
            Insets navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            int bottomInset = Math.max(navigationBars.bottom, ime.bottom);
            view.setPadding(baseLeft, baseTop, baseRight, baseBottom + bottomInset);
            return insets;
        });
        ViewCompat.requestApplyInsets(composer);
    }

    private void startKeySetupOrPolling() {
        if (initializeKey) {
            ensureCreatorKey();
        } else {
            loadMyConversationKey();
        }
    }

    private void requestRecoverySecret() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(R.string.chat_recovery_hint);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        input.setPadding(padding, padding / 2, padding, padding / 2);
        new AlertDialog.Builder(this)
                .setTitle(R.string.chat_key_restore_title)
                .setMessage(R.string.chat_recovery_required)
                .setView(input)
                .setCancelable(false)
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> finish())
                .setPositiveButton(R.string.chat_restore_key, (dialog, which) -> {
                    String secret = input.getText() == null ? "" : input.getText().toString();
                    if (TextUtils.isEmpty(secret)) {
                        Toast.makeText(this, R.string.chat_key_restore_failed, Toast.LENGTH_LONG).show();
                        finish();
                    } else {
                        restorePrivateKey(secret);
                    }
                })
                .show();
    }

    private void restorePrivateKey(String secret) {
        setStatus(R.string.chat_connecting);
        cryptoExecutor.execute(() -> {
            try {
                Response<PrivateKeyBackupResponse> response =
                        ApiClient.getApiService(getApplicationContext()).getPrivateKeyBackup().execute();
                if (!response.isSuccessful()) throw new IllegalStateException(ApiClient.parseError(response));
                PrivateKey restored = KeyUtils.restorePrivateKeyBackup(response.body(), secret);
                LocalKeyStore.savePrivateKey(getApplicationContext(), currentUserId, restored);
                runOnUiThread(() -> {
                    privateKey = restored;
                    startKeySetupOrPolling();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (!isFinishing()) {
                        Toast.makeText(this, R.string.chat_key_restore_failed, Toast.LENGTH_LONG).show();
                        finish();
                    }
                });
            }
        });
    }

    private void ensureCreatorKey() {
        if (keySetupRunning || isFinishing()) return;
        keySetupRunning = true;
        setStatus(R.string.chat_connecting);
        cryptoExecutor.execute(() -> {
            try {
                ApiService api = ApiClient.getApiService(getApplicationContext());
                Response<KeyEnvelopeResponse> ownResponse = api.getMyKeyEnvelope(conversationId).execute();
                if (ownResponse.isSuccessful() && ownResponse.body() != null) {
                    KeyEnvelopeResponse envelope = ownResponse.body();
                    byte[] existingKey = KeyUtils.unwrapConversationKey(
                            envelope.getEncryptedConversationKey(), privateKey);
                    LocalKeyStore.saveConversationKey(getApplicationContext(), currentUserId, conversationId, existingKey);
                    keyVersion = envelope.getKeyVersion();
                    conversationKey = existingKey;
                    ensureFriendEnvelope(api, existingKey, keyVersion);
                    runOnUiThread(() -> {
                        keySetupRunning = false;
                        setStatus(0);
                        beginPolling();
                    });
                    return;
                }
                if (ownResponse.code() != 404) {
                    throw new IllegalStateException(ApiClient.parseError(ownResponse));
                }

                Response<PublicKeyResponse> ownPublicResponse = api.getUserPublicKey(currentUserId).execute();
                Response<PublicKeyResponse> friendPublicResponse = api.getUserPublicKey(friendId).execute();
                if (!ownPublicResponse.isSuccessful() || ownPublicResponse.body() == null
                        || !friendPublicResponse.isSuccessful() || friendPublicResponse.body() == null) {
                    throw new IllegalStateException("Could not load participant public keys.");
                }
                PublicKey ownPublic = KeyUtils.importPublicKeyFromPem(ownPublicResponse.body().getRsaPublicKey());
                PublicKey friendPublic = KeyUtils.importPublicKeyFromPem(friendPublicResponse.body().getRsaPublicKey());
                byte[] generatedKey = KeyUtils.generateConversationKey();
                int version = 1;
                String ownWrapped = KeyUtils.wrapConversationKey(generatedKey, ownPublic);
                Response<KeyEnvelopeResponse> putOwn = api.putKeyEnvelope(conversationId, currentUserId,
                        new KeyEnvelopeRequest(version, ownWrapped)).execute();
                if (!putOwn.isSuccessful()) {
                    // Another device may have initialized the same canonical conversation first.
                    if (putOwn.code() == 409) {
                        Response<KeyEnvelopeResponse> raced = api.getMyKeyEnvelope(conversationId).execute();
                        if (raced.isSuccessful() && raced.body() != null) {
                            generatedKey = KeyUtils.unwrapConversationKey(
                                    raced.body().getEncryptedConversationKey(), privateKey);
                            version = raced.body().getKeyVersion();
                        } else {
                            throw new IllegalStateException(ApiClient.parseError(putOwn));
                        }
                    } else {
                        throw new IllegalStateException(ApiClient.parseError(putOwn));
                    }
                }
                LocalKeyStore.saveConversationKey(getApplicationContext(), currentUserId, conversationId, generatedKey);
                String friendWrapped = KeyUtils.wrapConversationKey(generatedKey, friendPublic);
                Response<KeyEnvelopeResponse> putFriend = api.putKeyEnvelope(conversationId, friendId,
                        new KeyEnvelopeRequest(version, friendWrapped)).execute();
                if (!putFriend.isSuccessful() && putFriend.code() != 409) {
                    throw new IllegalStateException(ApiClient.parseError(putFriend));
                }
                conversationKey = generatedKey;
                keyVersion = version;
                runOnUiThread(() -> {
                    keySetupRunning = false;
                    setStatus(0);
                    beginPolling();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    keySetupRunning = false;
                    if (!isFinishing()) {
                        setStatus(R.string.chat_key_setup_failed);
                        Toast.makeText(this, R.string.chat_key_setup_failed, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void ensureFriendEnvelope(ApiService api, byte[] key, int version) throws Exception {
        Response<PublicKeyResponse> friendPublicResponse = api.getUserPublicKey(friendId).execute();
        if (!friendPublicResponse.isSuccessful() || friendPublicResponse.body() == null) {
            throw new IllegalStateException("Could not load the other participant's public key.");
        }
        PublicKey friendPublic = KeyUtils.importPublicKeyFromPem(friendPublicResponse.body().getRsaPublicKey());
        String wrappedKey = KeyUtils.wrapConversationKey(key, friendPublic);
        Response<KeyEnvelopeResponse> response = api.putKeyEnvelope(conversationId, friendId,
                new KeyEnvelopeRequest(version, wrappedKey)).execute();
        if (!response.isSuccessful() && response.code() != 409) {
            throw new IllegalStateException(ApiClient.parseError(response));
        }
    }

    private void loadMyConversationKey() {
        if (keySetupRunning || isFinishing()) return;
        keySetupRunning = true;
        setStatus(R.string.chat_connecting);
        cryptoExecutor.execute(() -> {
            try {
                byte[] savedKey = LocalKeyStore.loadConversationKey(
                        getApplicationContext(), currentUserId, conversationId);
                if (savedKey != null) {
                    conversationKey = savedKey;
                    runOnUiThread(() -> {
                        keySetupRunning = false;
                        setStatus(0);
                        beginPolling();
                    });
                    return;
                }
                Response<KeyEnvelopeResponse> response =
                        ApiClient.getApiService(getApplicationContext()).getMyKeyEnvelope(conversationId).execute();
                if (response.isSuccessful() && response.body() != null) {
                    byte[] unwrapped = KeyUtils.unwrapConversationKey(
                            response.body().getEncryptedConversationKey(), privateKey);
                    LocalKeyStore.saveConversationKey(getApplicationContext(), currentUserId, conversationId, unwrapped);
                    conversationKey = unwrapped;
                    keyVersion = response.body().getKeyVersion();
                    runOnUiThread(() -> {
                        keySetupRunning = false;
                        setStatus(0);
                        beginPolling();
                    });
                } else if (response.code() == 404) {
                    runOnUiThread(() -> {
                        keySetupRunning = false;
                        setStatus(R.string.chat_waiting_for_key);
                        beginPolling();
                    });
                } else {
                    throw new IllegalStateException(ApiClient.parseError(response));
                }
            } catch (Exception error) {
                runOnUiThread(() -> {
                    keySetupRunning = false;
                    setStatus(R.string.chat_key_setup_failed);
                    beginPolling();
                });
            }
        });
    }

    private void beginPolling() {
        if (isResumed) {
            handler.removeCallbacks(pollRunnable);
            pollRunnable.run();
        }
    }

    private void pollConversation() {
        if (polling || !isResumed) return;
        polling = true;
        cryptoExecutor.execute(() -> {
            try {
                if (conversationKey == null) {
                    if (!initializeKey) {
                        byte[] stored = LocalKeyStore.loadConversationKey(
                                getApplicationContext(), currentUserId, conversationId);
                        if (stored != null) {
                            conversationKey = stored;
                            Call<KeyEnvelopeResponse> envelopeCall = ApiClient.getApiService(getApplicationContext())
                                    .getMyKeyEnvelope(conversationId);
                            activePollCall = envelopeCall;
                            Response<KeyEnvelopeResponse> envelope = envelopeCall.execute();
                            if (envelope.isSuccessful() && envelope.body() != null) {
                                keyVersion = envelope.body().getKeyVersion();
                            }
                        } else {
                            Call<KeyEnvelopeResponse> envelopeCall = ApiClient.getApiService(getApplicationContext())
                                    .getMyKeyEnvelope(conversationId);
                            activePollCall = envelopeCall;
                            Response<KeyEnvelopeResponse> envelope = envelopeCall.execute();
                            if (envelope.isSuccessful() && envelope.body() != null) {
                                byte[] unwrapped = KeyUtils.unwrapConversationKey(
                                        envelope.body().getEncryptedConversationKey(), privateKey);
                                keyVersion = envelope.body().getKeyVersion();
                                LocalKeyStore.saveConversationKey(
                                        getApplicationContext(), currentUserId, conversationId, unwrapped);
                                conversationKey = unwrapped;
                            }
                        }
                    }
                }

                if (conversationKey != null) {
                    Call<MessagePage> messagesCall = ApiClient.getApiService(getApplicationContext())
                            .getMessages(conversationId, MESSAGE_PAGE_SIZE, null);
                    activePollCall = messagesCall;
                    Response<MessagePage> response = messagesCall.execute();
                    if (response.isSuccessful() && response.body() != null && response.body().getMessages() != null) {
                        for (EncryptedMessage message : response.body().getMessages()) {
                            if (message == null || message.getId() == null || messagesById.containsKey(message.getId())) {
                                continue;
                            }
                            try {
                                message.setDecryptedText(KeyUtils.decryptMessage(
                                        message.getCiphertext(), message.getNonce(), message.getAuthTag(), conversationKey));
                            } catch (Exception decryptError) {
                                message.setDecryptedText(null);
                            }
                            messagesById.put(message.getId(), message);
                        }
                        List<EncryptedMessage> sorted = new ArrayList<>(messagesById.values());
                        sorted.sort(Comparator.comparing(
                                EncryptedMessage::getCreatedAt,
                                Comparator.nullsFirst(String::compareTo)));
                        runOnUiThread(() -> {
                            if (!isFinishing()) {
                                boolean nearBottom = !recyclerView.canScrollVertically(1);
                                adapter.setMessages(sorted);
                                if (nearBottom && !sorted.isEmpty()) {
                                    recyclerView.scrollToPosition(sorted.size() - 1);
                                }
                                setStatus(0);
                            }
                        });
                    } else if (response.code() == 401) {
                        runOnUiThread(() -> setStatus(R.string.chat_connecting));
                    }
                } else if (!initializeKey) {
                    // Recipient's key envelope can arrive after this chat is opened.
                    Call<KeyEnvelopeResponse> envelopeCall = ApiClient.getApiService(getApplicationContext())
                            .getMyKeyEnvelope(conversationId);
                    activePollCall = envelopeCall;
                    Response<KeyEnvelopeResponse> response = envelopeCall.execute();
                    if (response.isSuccessful() && response.body() != null) {
                        byte[] unwrapped = KeyUtils.unwrapConversationKey(
                                response.body().getEncryptedConversationKey(), privateKey);
                        LocalKeyStore.saveConversationKey(getApplicationContext(), currentUserId, conversationId, unwrapped);
                        conversationKey = unwrapped;
                        keyVersion = response.body().getKeyVersion();
                        runOnUiThread(() -> setStatus(0));
                    } else if (response.code() == 404) {
                        runOnUiThread(() -> setStatus(R.string.chat_waiting_for_key));
                    }
                }
            } catch (Exception ignored) {
                // Keep the chat usable during transient network interruptions; retry on the next poll.
            } finally {
                activePollCall = null;
                polling = false;
            }
        });
    }

    private void sendMessage() {
        String text = messageInput.getText() == null ? "" : messageInput.getText().toString().trim();
        if (TextUtils.isEmpty(text)) {
            Toast.makeText(this, R.string.chat_message_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        if (conversationKey == null) {
            Toast.makeText(this, R.string.chat_waiting_for_key, Toast.LENGTH_SHORT).show();
            return;
        }
        if (sending) return;

        sending = true;
        sendButton.setEnabled(false);
        messageInput.setText("");
        cryptoExecutor.execute(() -> {
            try {
                KeyUtils.EncryptedPayload payload = KeyUtils.encryptMessage(text, conversationKey);
                Response<EncryptedMessage> response = ApiClient.getApiService(getApplicationContext())
                        .sendMessage(conversationId, new SendMessageRequest(
                                keyVersion, payload.ciphertext, payload.nonce, payload.authTag)).execute();
                if (!response.isSuccessful()) throw new IllegalStateException(ApiClient.parseError(response));
                runOnUiThread(() -> pollConversation());
            } catch (Exception error) {
                runOnUiThread(() -> {
                    messageInput.setText(text);
                    Toast.makeText(this, R.string.chat_send_failed, Toast.LENGTH_SHORT).show();
                });
            } finally {
                runOnUiThread(() -> {
                    sending = false;
                    sendButton.setEnabled(true);
                });
            }
        });
    }

    private void setStatus(int resourceId) {
        if (statusView == null) return;
        if (resourceId == 0) {
            statusView.setVisibility(View.GONE);
        } else {
            statusView.setText(resourceId);
            statusView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isResumed = true;
        handler.removeCallbacks(pollRunnable);
        if (conversationKey != null || !keySetupRunning) {
            beginPolling();
        } else {
            handler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
        }
    }

    @Override
    protected void onPause() {
        isResumed = false;
        handler.removeCallbacks(pollRunnable);
        Call<?> pollCall = activePollCall;
        if (pollCall != null) pollCall.cancel();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        cryptoExecutor.shutdownNow();
        super.onDestroy();
    }
}
