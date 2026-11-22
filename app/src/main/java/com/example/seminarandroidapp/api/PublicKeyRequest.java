package com.example.seminarandroidapp.api;

import com.google.gson.annotations.SerializedName;

public class PublicKeyRequest {
    @SerializedName("rsa_public_key")
    private String rsaPublicKey;

    public PublicKeyRequest() {
    }

    public PublicKeyRequest(String rsaPublicKey) {
        this.rsaPublicKey = rsaPublicKey;
    }

    public String getRsaPublicKey() {
        return rsaPublicKey;
    }

    public void setRsaPublicKey(String rsaPublicKey) {
        this.rsaPublicKey = rsaPublicKey;
    }
}