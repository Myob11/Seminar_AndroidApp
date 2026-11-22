package com.example.seminarandroidapp.api;

import com.google.gson.annotations.SerializedName;

public class User {
    private String id;
    private String username;
    @SerializedName("profile_picture")
    private String profilePicture;

    public User() {
    }

    public User(String username) {
        this.username = username;
    }

    public User(String id, String username) {
        this.id = id;
        this.username = username;
    }

    public User(String id, String username, String profilePicture) {
        this.id = id;
        this.username = username;
        this.profilePicture = profilePicture;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getProfilePicture() {
        return profilePicture;
    }

    public void setProfilePicture(String profilePicture) {
        this.profilePicture = profilePicture;
    }
}