package com.example.seminarandroidapp;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
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
import com.example.seminarandroidapp.api.FriendRequestBody;
import com.example.seminarandroidapp.api.User;
import com.example.seminarandroidapp.util.Prefs;
import com.google.android.material.tabs.TabLayout;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import okhttp3.ResponseBody;

public class SearchUsersActivity extends AppCompatActivity {

    private static final long SEARCH_DEBOUNCE_MS = 350L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;
    private Call<List<User>> activeSearch;
    private Call<List<User>> friendsCall;
    private Call<List<FriendRequest>> requestsCall;
    private Call<List<FriendRequest>> outgoingRequestsCall;
    private final Set<String> acceptedFriendIds = new HashSet<>();
    private final Set<String> incomingRequestUserIds = new HashSet<>();
    private boolean friendsLoaded;
    private UsersAdapter usersAdapter;
    private FriendRequestsAdapter requestsAdapter;
    private RecyclerView recyclerView;
    private SearchView searchView;
    private TextView statusView;
    private TabLayout tabs;
    private String currentQuery = "";
    private boolean showingRequests;
    private boolean requestsLoaded;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_users);

        Toolbar toolbar = findViewById(R.id.searchToolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Add friends");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        statusView = findViewById(R.id.searchStatus);
        searchView = findViewById(R.id.userSearchView);
        tabs = findViewById(R.id.friendTabs);
        recyclerView = findViewById(R.id.recyclerViewUsers);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        usersAdapter = new UsersAdapter(this::sendFriendRequest);
        requestsAdapter = new FriendRequestsAdapter(new FriendRequestsAdapter.Listener() {
            @Override
            public void onAccept(FriendRequest request) {
                decideFriendRequest(request, true);
            }

            @Override
            public void onDecline(FriendRequest request) {
                decideFriendRequest(request, false);
            }
        });
        recyclerView.setAdapter(usersAdapter);

        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                showSection(tab.getPosition() == 1);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                if (tab.getPosition() == 1) {
                    loadFriendRequests();
                }
            }
        });

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                queueSearch(query, 0L);
                searchView.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                queueSearch(newText, SEARCH_DEBOUNCE_MS);
                return true;
            }
        });

        loadFriendRequests();
        loadAcceptedFriends();
        loadOutgoingFriendRequests();
    }

    private void showSection(boolean requests) {
        showingRequests = requests;
        if (activeSearch != null) {
            activeSearch.cancel();
            activeSearch = null;
        }
        if (pendingSearch != null) {
            handler.removeCallbacks(pendingSearch);
        }

        if (requests) {
            searchView.setVisibility(View.GONE);
            recyclerView.setAdapter(requestsAdapter);
            if (requestsLoaded) {
                showRequestListState();
            } else {
                showStatus("Loading requests…");
                loadFriendRequests();
            }
        } else {
            searchView.setVisibility(View.VISIBLE);
            recyclerView.setAdapter(usersAdapter);
            queueSearch(searchView.getQuery().toString(), 0L);
        }
    }

    private void queueSearch(String rawQuery, long delayMs) {
        currentQuery = rawQuery == null ? "" : rawQuery.trim();
        if (pendingSearch != null) {
            handler.removeCallbacks(pendingSearch);
        }
        if (activeSearch != null) {
            activeSearch.cancel();
            activeSearch = null;
        }

        usersAdapter.updateUsers(null);
        if (currentQuery.isEmpty()) {
            showStatus("Search for people by username");
            return;
        }
        if (currentQuery.length() > 64) {
            showStatus("Search text must be 64 characters or fewer");
            return;
        }
        if (!friendsLoaded) {
            showStatus("Loading your friends…");
            loadAcceptedFriends();
            return;
        }

        showStatus("Searching…");
        String query = currentQuery;
        pendingSearch = () -> searchUsers(query);
        handler.postDelayed(pendingSearch, delayMs);
    }

    private void searchUsers(String query) {
        Call<List<User>> call = ApiClient.getApiService(this).searchUsers(query);
        activeSearch = call;
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<User>> call, @NonNull Response<List<User>> response) {
                if (call.isCanceled() || !query.equals(currentQuery) || showingRequests) {
                    return;
                }
                activeSearch = null;
                if (response.isSuccessful() && response.body() != null) {
                    List<User> users = new ArrayList<>();
                    for (User user : response.body()) {
                        if (user != null && user.getId() != null
                                && !acceptedFriendIds.contains(user.getId())) {
                            users.add(user);
                        }
                    }
                    usersAdapter.updateUsers(users);
                    usersAdapter.setRequestedUserIds(Prefs.getOutgoingFriendRequestIds(SearchUsersActivity.this));
                    usersAdapter.setIncomingRequestUserIds(incomingRequestUserIds);
                    if (users.isEmpty()) {
                        showStatus("No new people found");
                    } else {
                        hideStatus();
                    }
                } else {
                    usersAdapter.updateUsers(null);
                    showStatus(ApiClient.parseError(response));
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<User>> call, @NonNull Throwable t) {
                if (call.isCanceled() || !query.equals(currentQuery) || showingRequests) {
                    return;
                }
                activeSearch = null;
                usersAdapter.updateUsers(null);
                showStatus("Search failed. Check your connection and try again.");
            }
        });
    }

    private void loadOutgoingFriendRequests() {
        if (outgoingRequestsCall != null) {
            outgoingRequestsCall.cancel();
        }
        Call<List<FriendRequest>> call = ApiClient.getApiService(this).getOutgoingFriendRequests();
        outgoingRequestsCall = call;
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<FriendRequest>> call,
                                   @NonNull Response<List<FriendRequest>> response) {
                if (outgoingRequestsCall != call || call.isCanceled()) {
                    return;
                }
                outgoingRequestsCall = null;
                if (!response.isSuccessful() || response.body() == null) {
                    return;
                }

                Set<String> outgoingUserIds = new HashSet<>();
                for (FriendRequest request : response.body()) {
                    if (request != null && "pending".equalsIgnoreCase(request.getStatus())
                            && request.getRecipient() != null
                            && request.getRecipient().getId() != null) {
                        outgoingUserIds.add(request.getRecipient().getId());
                    }
                }
                Prefs.replaceOutgoingFriendRequestIds(SearchUsersActivity.this, outgoingUserIds);
                usersAdapter.setRequestedUserIds(outgoingUserIds);
            }

            @Override
            public void onFailure(@NonNull Call<List<FriendRequest>> call, @NonNull Throwable throwable) {
                if (outgoingRequestsCall == call) {
                    outgoingRequestsCall = null;
                }
            }
        });
    }

    private void loadAcceptedFriends() {
        if (friendsLoaded || friendsCall != null) {
            return;
        }

        Call<List<User>> call = ApiClient.getApiService(this).getFriends();
        friendsCall = call;
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<User>> call, @NonNull Response<List<User>> response) {
                if (friendsCall != call || call.isCanceled()) {
                    return;
                }
                friendsCall = null;
                if (!response.isSuccessful() || response.body() == null) {
                    showFriendsLoadError();
                    return;
                }

                acceptedFriendIds.clear();
                for (User friend : response.body()) {
                    if (friend != null && friend.getId() != null) {
                        acceptedFriendIds.add(friend.getId());
                        Prefs.removeOutgoingFriendRequest(SearchUsersActivity.this, friend.getId());
                    }
                }
                friendsLoaded = true;

                if (!showingRequests && !currentQuery.isEmpty()) {
                    queueSearch(currentQuery, 0L);
                } else if (!showingRequests) {
                    showStatus("Search for people by username");
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<User>> call, @NonNull Throwable t) {
                if (friendsCall == call) {
                    friendsCall = null;
                }
                if (!call.isCanceled()) {
                    showFriendsLoadError();
                }
            }
        });
    }

    private void showFriendsLoadError() {
        if (!showingRequests) {
            showStatus("Could not load your friends. Try searching again.");
        }
    }

    private void sendFriendRequest(User user) {
        if (user.getId() == null) {
            Toast.makeText(this, "This user cannot receive a request right now.", Toast.LENGTH_SHORT).show();
            return;
        }

        usersAdapter.setSending(user.getId(), true);
        ApiClient.getApiService(this)
                .sendFriendRequest(new FriendRequestBody(user.getId()))
                .enqueue(new Callback<>() {
                    @Override
                    public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                        if (response.isSuccessful()) {
                            Prefs.addOutgoingFriendRequest(SearchUsersActivity.this, user.getId());
                            usersAdapter.markRequested(user.getId());
                            Toast.makeText(SearchUsersActivity.this, "Friend request sent.", Toast.LENGTH_SHORT).show();
                        } else {
                            String error = ApiClient.parseError(response);
                            String normalizedError = error.toLowerCase(java.util.Locale.ROOT);
                            boolean alreadyPending = response.code() == 409
                                    && (normalizedError.contains("pending")
                                    || normalizedError.contains("already exists")
                                    || normalizedError.contains("friendship_exists"));
                            if (alreadyPending) {
                                Prefs.addOutgoingFriendRequest(SearchUsersActivity.this, user.getId());
                                usersAdapter.markRequested(user.getId());
                                Toast.makeText(SearchUsersActivity.this, "Friend request is already pending.", Toast.LENGTH_SHORT).show();
                            } else {
                                usersAdapter.setSending(user.getId(), false);
                                Toast.makeText(SearchUsersActivity.this, error, Toast.LENGTH_LONG).show();
                            }
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                        usersAdapter.setSending(user.getId(), false);
                        Toast.makeText(SearchUsersActivity.this, "Could not send request. Check your connection and try again.", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void loadFriendRequests() {
        if (requestsCall != null) {
            if (showingRequests) {
                showStatus("Loading requests…");
            }
            return;
        }

        if (showingRequests) {
            showStatus("Loading requests…");
        }
        Call<List<FriendRequest>> call = ApiClient.getApiService(this).getFriendRequests();
        requestsCall = call;
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<FriendRequest>> call,
                                   @NonNull Response<List<FriendRequest>> response) {
                if (requestsCall != call || call.isCanceled()) {
                    return;
                }
                requestsCall = null;
                if (response.isSuccessful() && response.body() != null) {
                    List<FriendRequest> pending = new ArrayList<>();
                    incomingRequestUserIds.clear();
                    for (FriendRequest request : response.body()) {
                        if (request != null && "pending".equalsIgnoreCase(request.getStatus())) {
                            pending.add(request);
                            if (request.getRequester() != null && request.getRequester().getId() != null) {
                                incomingRequestUserIds.add(request.getRequester().getId());
                            }
                        }
                    }
                    requestsAdapter.setRequests(pending);
                    usersAdapter.setIncomingRequestUserIds(incomingRequestUserIds);
                    requestsLoaded = true;
                    updateRequestsTabTitle();
                    if (showingRequests) {
                        showRequestListState();
                    }
                } else {
                    requestsLoaded = false;
                    if (showingRequests) {
                        showStatus(ApiClient.parseError(response));
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<FriendRequest>> call, @NonNull Throwable t) {
                if (requestsCall != call || call.isCanceled()) {
                    return;
                }
                requestsCall = null;
                requestsLoaded = false;
                if (showingRequests) {
                    showStatus("Could not load requests. Tap Requests to retry.");
                }
            }
        });
    }

    private void decideFriendRequest(FriendRequest request, boolean accept) {
        if (request.getId() == null) {
            Toast.makeText(this, "This request cannot be updated.", Toast.LENGTH_SHORT).show();
            return;
        }

        requestsAdapter.setBusy(request.getId(), true);
        Call<ResponseBody> call = accept
                ? ApiClient.getApiService(this).acceptFriendRequest(request.getId())
                : ApiClient.getApiService(this).declineFriendRequest(request.getId());
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    if (request.getRequester() != null && request.getRequester().getId() != null) {
                        String requesterId = request.getRequester().getId();
                        incomingRequestUserIds.remove(requesterId);
                        if (accept) {
                            acceptedFriendIds.add(requesterId);
                            friendsLoaded = true;
                        }
                        usersAdapter.setIncomingRequestUserIds(incomingRequestUserIds);
                    }
                    requestsAdapter.removeRequest(request.getId());
                    updateRequestsTabTitle();
                    showRequestListState();
                    String message = accept ? "Friend request accepted." : "Friend request declined.";
                    Toast.makeText(SearchUsersActivity.this, message, Toast.LENGTH_SHORT).show();
                } else {
                    requestsAdapter.setBusy(request.getId(), false);
                    Toast.makeText(SearchUsersActivity.this, ApiClient.parseError(response), Toast.LENGTH_LONG).show();
                    if (response.code() == 409 || response.code() == 404) {
                        requestsLoaded = false;
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                requestsAdapter.setBusy(request.getId(), false);
                Toast.makeText(SearchUsersActivity.this, "Could not update request. Check your connection and try again.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateRequestsTabTitle() {
        TabLayout.Tab requestTab = tabs.getTabAt(1);
        if (requestTab != null) {
            requestTab.setText("Requests (" + requestsAdapter.getRequestCount() + ")");
        }
    }

    private void showRequestListState() {
        if (requestsAdapter.getRequestCount() == 0) {
            showStatus("No incoming friend requests");
        } else {
            hideStatus();
        }
    }

    private void showStatus(String message) {
        statusView.setText(message);
        statusView.setVisibility(View.VISIBLE);
    }

    private void hideStatus() {
        statusView.setVisibility(View.GONE);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (activeSearch != null) {
            activeSearch.cancel();
        }
        if (requestsCall != null) {
            requestsCall.cancel();
        }
        if (friendsCall != null) {
            friendsCall.cancel();
        }
        if (outgoingRequestsCall != null) {
            outgoingRequestsCall.cancel();
        }
        super.onDestroy();
    }
}