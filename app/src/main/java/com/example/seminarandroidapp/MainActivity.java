package com.example.seminarandroidapp;

import android.content.Intent;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.seminarandroidapp.api.ApiClient;
import com.example.seminarandroidapp.api.DirectConversation;
import com.example.seminarandroidapp.api.DirectConversationRequest;
import com.example.seminarandroidapp.api.EncryptedMessage;
import com.example.seminarandroidapp.api.FriendRequest;
import com.example.seminarandroidapp.api.KeyEnvelopeResponse;
import com.example.seminarandroidapp.api.User;
import com.example.seminarandroidapp.util.KeyUtils;
import com.example.seminarandroidapp.util.LocalKeyStore;
import com.example.seminarandroidapp.util.Prefs;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import java.security.PrivateKey;
import java.text.Collator;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final long REFRESH_INTERVAL_MILLIS = 15_000L;
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable periodicRefresh = new Runnable() {
        @Override
        public void run() {
            refreshMainContent();
            refreshFriendRequestCount();
            refreshHandler.postDelayed(this, REFRESH_INTERVAL_MILLIS);
        }
    };

    private RecyclerView recyclerViewConversations;
    private ConversationsAdapter adapter;
    private SearchView searchView;
    private TextView friendRequestBadge;
    private int incomingRequestCount;
    private String currentSearchQuery = "";

    private List<User> friends = new ArrayList<>();
    private List<DirectConversation> directConversations = new ArrayList<>();
    private boolean friendsLoaded;
    private boolean conversationsLoaded;

    private Call<List<FriendRequest>> friendRequestsCall;
    private Call<List<User>> friendsCall;
    private Call<List<DirectConversation>> conversationsCall;
    private Call<DirectConversation> createConversationCall;
    private final Map<String, Call<?>> previewCalls = new HashMap<>();
    private final Map<String, ConversationPreview> previewCache = new HashMap<>();
    private final ExecutorService previewExecutor = Executors.newFixedThreadPool(3);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!Prefs.isLoggedIn(this)) {
            navigateToLogin();
            return;
        }

        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        applyStatusBarInsetToToolbar(toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            String username = Prefs.getUsername(this);
            getSupportActionBar().setTitle(username != null ? username : "");
        }

        searchView = findViewById(R.id.searchView);
        recyclerViewConversations = findViewById(R.id.recyclerViewConversations);
        recyclerViewConversations.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewConversations.setFocusableInTouchMode(true);
        recyclerViewConversations.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN && searchView.hasFocus()) {
                recyclerViewConversations.requestFocus();
            }
            return false;
        });

        adapter = new ConversationsAdapter(new ArrayList<>(), this::onConversationSelected, conversation -> { });
        recyclerViewConversations.setAdapter(adapter);

        searchView.setQueryHint("Search friends and chats");
        searchView.setOnQueryTextFocusChangeListener((view, hasFocus) -> {
            adapter.setSearchFocused(hasFocus);
            if (hasFocus) {
                adapter.filter(currentSearchQuery);
                return;
            }

            currentSearchQuery = "";
            searchView.setQuery("", false);
            adapter.filter("");
            InputMethodManager inputMethodManager =
                    (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (inputMethodManager != null) {
                inputMethodManager.hideSoftInputFromWindow(searchView.getWindowToken(), 0);
            }
        });
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                currentSearchQuery = query;
                adapter.filter(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                currentSearchQuery = newText;
                adapter.filter(newText);
                return true;
            }
        });
    }

    private void applyStatusBarInsetToToolbar(Toolbar toolbar) {
        ViewGroup.LayoutParams layoutParams = toolbar.getLayoutParams();
        int baseHeight = layoutParams.height;
        int basePaddingLeft = toolbar.getPaddingLeft();
        int basePaddingTop = toolbar.getPaddingTop();
        int basePaddingRight = toolbar.getPaddingRight();
        int basePaddingBottom = toolbar.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(toolbar, (view, windowInsets) -> {
            Insets safeTopInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout()
            );
            int topInset = safeTopInsets.top;

            ViewGroup.LayoutParams updatedParams = view.getLayoutParams();
            updatedParams.height = baseHeight + topInset;
            view.setLayoutParams(updatedParams);
            view.setPadding(
                    basePaddingLeft,
                    basePaddingTop + topInset,
                    basePaddingRight,
                    basePaddingBottom
            );
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(toolbar);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshMainContent();
        refreshFriendRequestCount();
        refreshHandler.removeCallbacks(periodicRefresh);
        refreshHandler.postDelayed(periodicRefresh, REFRESH_INTERVAL_MILLIS);
    }

    @Override
    protected void onPause() {
        refreshHandler.removeCallbacks(periodicRefresh);
        for (Call<?> call : previewCalls.values()) {
            call.cancel();
        }
        previewCalls.clear();
        super.onPause();
    }

    private void refreshMainContent() {
        if (friendsCall != null || conversationsCall != null) {
            return;
        }
        friendsLoaded = false;
        conversationsLoaded = false;

        Call<List<User>> loadFriends = ApiClient.getApiService(this).getFriends();
        friendsCall = loadFriends;
        loadFriends.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<User>> call, @NonNull Response<List<User>> response) {
                if (friendsCall != call || call.isCanceled()) {
                    return;
                }
                friendsCall = null;
                if (response.isSuccessful() && response.body() != null) {
                    friends = response.body();
                }
                friendsLoaded = true;
                renderMainContent();
            }

            @Override
            public void onFailure(@NonNull Call<List<User>> call, @NonNull Throwable t) {
                if (friendsCall == call) {
                    friendsCall = null;
                }
                if (call.isCanceled()) {
                    return;
                }
                friendsLoaded = true;
                renderMainContent();
                Toast.makeText(MainActivity.this, "Could not load friends.", Toast.LENGTH_SHORT).show();
            }
        });

        Call<List<DirectConversation>> loadConversations =
                ApiClient.getApiService(this).getDirectConversations();
        conversationsCall = loadConversations;
        loadConversations.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<DirectConversation>> call,
                                   @NonNull Response<List<DirectConversation>> response) {
                if (conversationsCall != call || call.isCanceled()) {
                    return;
                }
                conversationsCall = null;
                if (response.isSuccessful() && response.body() != null) {
                    directConversations = response.body();
                }
                conversationsLoaded = true;
                renderMainContent();
            }

            @Override
            public void onFailure(@NonNull Call<List<DirectConversation>> call, @NonNull Throwable t) {
                if (conversationsCall == call) {
                    conversationsCall = null;
                }
                if (call.isCanceled()) {
                    return;
                }
                conversationsLoaded = true;
                renderMainContent();
                Toast.makeText(MainActivity.this, "Could not load conversations.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void renderMainContent() {
        if (!friendsLoaded || !conversationsLoaded || adapter == null) {
            return;
        }

        List<Conversation> rows = new ArrayList<>();
        List<Conversation> searchableRows = new ArrayList<>();
        List<Conversation> friendSuggestions = new ArrayList<>();
        Map<String, EncryptedMessage> latestMessagesByConversation = new HashMap<>();
        Set<String> usersWithConversations = new HashSet<>();
        String currentUserId = Prefs.getUserId(this);

        for (DirectConversation directConversation : directConversations) {
            if (directConversation == null || directConversation.getId() == null) {
                continue;
            }
            User friend = directConversation.getOtherParticipant(currentUserId);
            if (friend == null && directConversation.getRecipientId() != null) {
                friend = findFriend(directConversation.getRecipientId());
            }
            if (friend == null || friend.getId() == null || friend.getUsername() == null) {
                continue;
            }

            usersWithConversations.add(friend.getId());
            User latestFriend = findFriend(friend.getId());
            if (latestFriend != null) {
                friend = latestFriend;
            }
            EncryptedMessage latestMessage = directConversation.getLastMessage();
            ConversationPreview cachedPreview = previewCache.get(directConversation.getId());
            boolean hasCurrentPreview = latestMessage != null && cachedPreview != null
                    && latestMessage.getId() != null
                    && latestMessage.getId().equals(cachedPreview.messageId);
            String previewText = latestMessage == null ? "No messages yet"
                    : hasCurrentPreview ? cachedPreview.text : "Encrypted message";
            String previewTime = latestMessage == null ? ""
                    : hasCurrentPreview ? cachedPreview.time : formatPreviewTime(latestMessage.getCreatedAt());
            Conversation row = new Conversation(
                    directConversation.getId(),
                    friend.getId(),
                    friend.getUsername(),
                    previewText,
                    previewTime
            );
            row.setProfilePicture(friend.getProfilePicture());
            row.setUnreadCount(directConversation.getUnreadCount());
            latestMessagesByConversation.put(directConversation.getId(), latestMessage);
            rows.add(row);
            searchableRows.add(row);
        }

        for (User friend : friends) {
            if (friend == null || friend.getId() == null || friend.getUsername() == null
                    || usersWithConversations.contains(friend.getId())) {
                continue;
            }
            Conversation row = new Conversation(
                    null,
                    friend.getId(),
                    friend.getUsername(),
                    "Start a conversation",
                    ""
            );
            row.setProfilePicture(friend.getProfilePicture());
            searchableRows.add(row);
            friendSuggestions.add(row);
        }

        Collator collator = Collator.getInstance(Locale.getDefault());
        rows.sort((left, right) -> collator.compare(left.getContactName(), right.getContactName()));
        searchableRows.sort((left, right) -> collator.compare(left.getContactName(), right.getContactName()));
        friendSuggestions.sort((left, right) -> collator.compare(left.getContactName(), right.getContactName()));
        adapter.updateData(rows, searchableRows, friendSuggestions);
        adapter.filter(currentSearchQuery);
        for (Conversation row : rows) {
            loadConversationPreview(row.getConversationId(),
                    latestMessagesByConversation.get(row.getConversationId()));
        }
    }

    private void loadConversationPreview(String conversationId, EncryptedMessage latestMessage) {
        if (conversationId == null || latestMessage == null) {
            return;
        }
        ConversationPreview cached = previewCache.get(conversationId);
        if (cached != null && latestMessage.getId().equals(cached.messageId)) {
            return;
        }
        if (previewCalls.containsKey(conversationId)) {
            return;
        }

        byte[] conversationKey = null;
        PrivateKey privateKey = null;
        try {
            conversationKey = LocalKeyStore.loadConversationKey(this, Prefs.getUserId(this), conversationId);
            if (conversationKey == null) {
                privateKey = LocalKeyStore.loadPrivateKey(this, Prefs.getUserId(this));
            }
        } catch (Exception ignored) {
            // Display a safe encrypted preview if this device cannot access the local key.
        }

        if (conversationKey != null) {
            decryptLatestPreview(conversationId, latestMessage, conversationKey);
            return;
        }
        if (privateKey == null) {
            setConversationPreview(conversationId, latestMessage, "Encrypted message");
            return;
        }

        Call<KeyEnvelopeResponse> envelopeCall = ApiClient.getApiService(this)
                .getMyKeyEnvelope(conversationId);
        previewCalls.put(conversationId, envelopeCall);
        PrivateKey localPrivateKey = privateKey;
        envelopeCall.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<KeyEnvelopeResponse> call,
                                   @NonNull Response<KeyEnvelopeResponse> response) {
                if (previewCalls.get(conversationId) != call || call.isCanceled()) return;
                if (!response.isSuccessful() || response.body() == null) {
                    previewCalls.remove(conversationId);
                    setConversationPreview(conversationId, latestMessage, "Encrypted message");
                    return;
                }

                KeyEnvelopeResponse envelope = response.body();
                previewExecutor.execute(() -> {
                    byte[] key = null;
                    try {
                        key = KeyUtils.unwrapConversationKey(envelope.getEncryptedConversationKey(), localPrivateKey);
                        LocalKeyStore.saveConversationKey(getApplicationContext(),
                                Prefs.getUserId(MainActivity.this), conversationId, key);
                    } catch (Exception ignored) {
                        // Keep encrypted content hidden if the key cannot be restored.
                    }
                    byte[] resolvedKey = key;
                    runOnUiThread(() -> {
                        if (!isFinishing() && previewCalls.get(conversationId) == call) {
                            previewCalls.remove(conversationId);
                            if (resolvedKey != null) {
                                decryptLatestPreview(conversationId, latestMessage, resolvedKey);
                            } else {
                                setConversationPreview(conversationId, latestMessage, "Encrypted message");
                            }
                        }
                    });
                });
            }

            @Override
            public void onFailure(@NonNull Call<KeyEnvelopeResponse> call, @NonNull Throwable t) {
                if (previewCalls.get(conversationId) == call) {
                    previewCalls.remove(conversationId);
                }
            }
        });
    }

    private void decryptLatestPreview(String conversationId, EncryptedMessage latestMessage, byte[] key) {
        previewExecutor.execute(() -> {
            String text = "Encrypted message";
            try {
                text = KeyUtils.decryptMessage(latestMessage.getCiphertext(), latestMessage.getNonce(),
                        latestMessage.getAuthTag(), key);
            } catch (Exception ignored) {
                // Keep ciphertext out of the conversation list.
            }
            String previewText = text;
            runOnUiThread(() -> {
                if (!isFinishing()) {
                    setConversationPreview(conversationId, latestMessage, previewText);
                }
            });
        });
    }

    private void setConversationPreview(String conversationId, EncryptedMessage latestMessage, String text) {
        if (!isCurrentLatestMessage(conversationId, latestMessage.getId())) {
            return;
        }
        ConversationPreview loaded = new ConversationPreview(
                latestMessage.getId(), text, formatPreviewTime(latestMessage.getCreatedAt()));
        previewCache.put(conversationId, loaded);
        if (!isFinishing() && !isDestroyed() && adapter != null) {
            adapter.updateConversationPreview(conversationId, loaded.text, loaded.time);
        }
    }

    private boolean isCurrentLatestMessage(String conversationId, String messageId) {
        for (DirectConversation conversation : directConversations) {
            if (conversation != null && conversationId.equals(conversation.getId())) {
                EncryptedMessage latest = conversation.getLastMessage();
                return latest != null && messageId != null && messageId.equals(latest.getId());
            }
        }
        return false;
    }

    private String formatPreviewTime(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) return "";
        try {
            Date date = Date.from(Instant.parse(timestamp));
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(date);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static final class ConversationPreview {
        final String messageId;
        final String text;
        final String time;

        ConversationPreview(String messageId, String text, String time) {
            this.messageId = messageId;
            this.text = text;
            this.time = time;
        }
    }

    private User findFriend(String userId) {
        for (User friend : friends) {
            if (friend != null && userId.equals(friend.getId())) {
                return friend;
            }
        }
        return null;
    }

    private void onConversationSelected(Conversation conversation) {
        if (conversation.hasDirectConversation()) {
            openConversation(conversation, false);
        } else {
            createDirectConversation(conversation);
        }
    }

    private void openConversation(Conversation conversation, boolean initializeKey) {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_CONVERSATION_ID, conversation.getConversationId());
        intent.putExtra(ChatActivity.EXTRA_FRIEND_ID, conversation.getTargetUserId());
        intent.putExtra(ChatActivity.EXTRA_FRIEND_NAME, conversation.getContactName());
        intent.putExtra(ChatActivity.EXTRA_FRIEND_PICTURE, conversation.getProfilePicture());
        intent.putExtra(ChatActivity.EXTRA_INITIALIZE_KEY, initializeKey);
        startActivity(intent);
    }

    private void createDirectConversation(Conversation friendRow) {
        if (createConversationCall != null) {
            return;
        }

        Call<DirectConversation> call = ApiClient.getApiService(this)
                .createDirectConversation(new DirectConversationRequest(friendRow.getTargetUserId()));
        createConversationCall = call;
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<DirectConversation> call,
                                   @NonNull Response<DirectConversation> response) {
                if (createConversationCall != call || call.isCanceled()) {
                    return;
                }
                createConversationCall = null;
                if (response.isSuccessful() && response.body() != null && response.body().getId() != null) {
                    DirectConversation created = response.body();
                    created.setRecipient(new User(friendRow.getTargetUserId(), friendRow.getContactName(),
                            friendRow.getProfilePicture()));
                    directConversations.add(created);
                    renderMainContent();
                    Conversation createdRow = new Conversation(
                            created.getId(),
                            friendRow.getTargetUserId(),
                            friendRow.getContactName(),
                            "Tap to chat",
                            ""
                    );
                    createdRow.setProfilePicture(friendRow.getProfilePicture());
                    openConversation(createdRow, true);
                } else {
                    Toast.makeText(MainActivity.this, ApiClient.parseError(response), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<DirectConversation> call, @NonNull Throwable t) {
                if (createConversationCall == call) {
                    createConversationCall = null;
                }
                if (!call.isCanceled()) {
                    Toast.makeText(MainActivity.this, "Could not create conversation. Check your connection and try again.", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        MenuItem addFriendsItem = menu.findItem(R.id.action_add_friend);
        View actionView = addFriendsItem.getActionView();
        if (actionView != null) {
            friendRequestBadge = actionView.findViewById(R.id.friendRequestBadge);
            actionView.setOnClickListener(v -> openFriendRequests());
            ImageButton addFriendsButton = actionView.findViewById(R.id.addFriendButton);
            addFriendsButton.setOnClickListener(v -> openFriendRequests());
            updateFriendRequestBadge();
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        updateFriendRequestBadge();
        return super.onPrepareOptionsMenu(menu);
    }

    private void refreshFriendRequestCount() {
        if (friendRequestsCall != null) {
            return;
        }
        Call<List<FriendRequest>> call = ApiClient.getApiService(this).getFriendRequests();
        friendRequestsCall = call;
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<FriendRequest>> call,
                                   @NonNull Response<List<FriendRequest>> response) {
                if (friendRequestsCall != call || call.isCanceled()) {
                    return;
                }
                friendRequestsCall = null;
                if (response.isSuccessful() && response.body() != null) {
                    int count = 0;
                    for (FriendRequest request : response.body()) {
                        if (request != null && "pending".equalsIgnoreCase(request.getStatus())) {
                            count++;
                        }
                    }
                    incomingRequestCount = count;
                    updateFriendRequestBadge();
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<FriendRequest>> call, @NonNull Throwable t) {
                if (friendRequestsCall == call) {
                    friendRequestsCall = null;
                }
            }
        });
    }

    private void updateFriendRequestBadge() {
        if (friendRequestBadge == null) {
            return;
        }
        if (incomingRequestCount <= 0) {
            friendRequestBadge.setVisibility(View.GONE);
            return;
        }
        friendRequestBadge.setText(incomingRequestCount > 99 ? "99+" : String.valueOf(incomingRequestCount));
        friendRequestBadge.setVisibility(View.VISIBLE);
    }

    private void openFriendRequests() {
        startActivity(new Intent(this, SearchUsersActivity.class));
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (id == R.id.action_add_friend) {
            openFriendRequests();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        if (friendRequestsCall != null) {
            friendRequestsCall.cancel();
        }
        if (friendsCall != null) {
            friendsCall.cancel();
        }
        if (conversationsCall != null) {
            conversationsCall.cancel();
        }
        if (createConversationCall != null) {
            createConversationCall.cancel();
        }
        for (Call<?> call : previewCalls.values()) {
            call.cancel();
        }
        previewCalls.clear();
        previewExecutor.shutdownNow();
        super.onDestroy();
    }

    private void performLogout() {
        ApiClient.getApiService(this).logout().enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                completeLogout();
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                completeLogout();
            }
        });
    }

    private void completeLogout() {
        Prefs.clearAuth(this);
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
        navigateToLogin();
    }

    private void navigateToLogin() {
        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}