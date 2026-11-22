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
import com.example.seminarandroidapp.api.FriendRequest;
import com.example.seminarandroidapp.util.Prefs;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerViewConversations;
    private ConversationsAdapter adapter;
    private List<Conversation> conversationList;
    private TextView friendRequestBadge;
    private int incomingRequestCount;
    private Call<List<FriendRequest>> friendRequestsCall;

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

        SearchView searchView = findViewById(R.id.searchView);
        recyclerViewConversations = findViewById(R.id.recyclerViewConversations);
        recyclerViewConversations.setLayoutManager(new LinearLayoutManager(this));

        conversationList = new ArrayList<>();
        adapter = new ConversationsAdapter(conversationList, conversation -> {
            Toast.makeText(MainActivity.this, "Chat with " + conversation.getContactName(), Toast.LENGTH_SHORT).show();
        }, conversation -> {
        });

        recyclerViewConversations.setAdapter(adapter);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                adapter.filter(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                adapter.filter(newText);
                return false;
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

    @Override
    protected void onResume() {
        super.onResume();
        refreshFriendRequestCount();
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
            performLogout();
            return true;
        } else if (id == R.id.action_add_friend) {
            startActivity(new Intent(this, SearchUsersActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        if (friendRequestsCall != null) {
            friendRequestsCall.cancel();
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