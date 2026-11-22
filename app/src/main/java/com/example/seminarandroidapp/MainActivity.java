package com.example.seminarandroidapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.seminarandroidapp.api.ApiClient;
import com.example.seminarandroidapp.api.DirectConversation;
import com.example.seminarandroidapp.api.DirectConversationRequest;
import com.example.seminarandroidapp.api.FriendRequest;
import com.example.seminarandroidapp.api.User;
import com.example.seminarandroidapp.util.Prefs;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!Prefs.isLoggedIn(this)) {
            navigateToLogin();
            return;
        }

        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            String username = Prefs.getUsername(this);
            getSupportActionBar().setTitle(username != null ? "Chats (" + username + ")" : "Seminar App");
        }

        searchView = findViewById(R.id.searchView);
        recyclerViewConversations = findViewById(R.id.recyclerViewConversations);
        recyclerViewConversations.setLayoutManager(new LinearLayoutManager(this));

        adapter = new ConversationsAdapter(new ArrayList<>(), this::onConversationSelected, conversation -> { });
        recyclerViewConversations.setAdapter(adapter);

        searchView.setQueryHint("Search friends and chats");
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

    @Override
    protected void onResume() {
        super.onResume();
        refreshMainContent();
        refreshFriendRequestCount();
    }

    private void refreshMainContent() {
        if (friendsCall != null) {
            friendsCall.cancel();
        }
        if (conversationsCall != null) {
            conversationsCall.cancel();
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
            rows.add(new Conversation(
                    directConversation.getId(),
                    friend.getId(),
                    friend.getUsername(),
                    "Tap to chat",
                    ""
            ));
        }

        for (User friend : friends) {
            if (friend == null || friend.getId() == null || friend.getUsername() == null
                    || usersWithConversations.contains(friend.getId())) {
                continue;
            }
            rows.add(new Conversation(
                    null,
                    friend.getId(),
                    friend.getUsername(),
                    "Start a conversation",
                    ""
            ));
        }

        Collator collator = Collator.getInstance(Locale.getDefault());
        rows.sort((left, right) -> collator.compare(left.getContactName(), right.getContactName()));
        adapter.updateData(rows);
        adapter.filter(currentSearchQuery);
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
            Toast.makeText(this, "Chat with " + conversation.getContactName(), Toast.LENGTH_SHORT).show();
        } else {
            createDirectConversation(conversation);
        }
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
                    created.setRecipient(new User(friendRow.getTargetUserId(), friendRow.getContactName()));
                    directConversations.add(created);
                    renderMainContent();
                    Toast.makeText(MainActivity.this, "Conversation created.", Toast.LENGTH_SHORT).show();
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
            friendRequestsCall.cancel();
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