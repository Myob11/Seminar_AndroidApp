package com.example.seminarandroidapp.api;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;
import java.util.List;

public interface ApiService {

    @GET("api/health")
    Call<HealthResponse> checkHealth();

    @POST("api/auth/register")
    Call<AuthResponse> register(@Body AuthRequest request);

    @POST("api/auth/login")
    Call<AuthResponse> login(@Body AuthRequest request);

    @POST("api/auth/logout")
    Call<ResponseBody> logout();

    @GET("api/auth/me")
    Call<User> getMe();

    @GET("api/users/search")
    Call<List<User>> searchUsers(@Query("q") String query);

    @GET("api/friends")
    Call<List<User>> getFriends();

    @GET("api/conversations/direct")
    Call<List<DirectConversation>> getDirectConversations();

    @POST("api/conversations/direct")
    Call<DirectConversation> createDirectConversation(@Body DirectConversationRequest request);

    @POST("api/friends/requests")
    Call<ResponseBody> sendFriendRequest(@Body FriendRequestBody request);

    @GET("api/friends/requests")
    Call<List<FriendRequest>> getFriendRequests();

    @POST("api/friends/requests/{id}/accept")
    Call<ResponseBody> acceptFriendRequest(@Path("id") String requestId);

    @POST("api/friends/requests/{id}/decline")
    Call<ResponseBody> declineFriendRequest(@Path("id") String requestId);

    @PUT("api/users/me/public-key")
    Call<PublicKeyResponse> setPublicKey(@Body PublicKeyRequest request);

    @PUT("api/users/me/private-key-backup")
    Call<ResponseBody> uploadPrivateKeyBackup(@Body PrivateKeyBackupRequest request);

    @GET("api/users/me/private-key-backup")
    Call<ResponseBody> getPrivateKeyBackup();
}