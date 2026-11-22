package com.example.seminarandroidapp.api;

import com.google.gson.annotations.SerializedName;

public class PublicKeyResponse {
    @SerializedName("rsa_public_key")
    private String rsaPublicKey;

    public PublicKeyResponse() {
    }

    public PublicKeyResponse(String rsaPublicKey) {
        this.rsaPublicKey = rsaPublicKey;
    }

    public String getRsaPublicKey() {
        return rsaPublicKey;
    }

    public void setRsaPublicKey(String rsaPublicKey) {
        this.rsaPublicKey = rsaPublicKey;
    }
}