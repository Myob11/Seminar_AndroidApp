package com.example.seminarandroidapp.api;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

public class PrivateKeyBackupResponse {
    @SerializedName("encrypted_rsa_private_key")
    private String encryptedRsaPrivateKey;
    @SerializedName("private_key_kdf")
    private String privateKeyKdf;
    @SerializedName("private_key_kdf_parameters")
    private Map<String, Object> privateKeyKdfParameters;
    @SerializedName("private_key_backup_salt")
    private String privateKeyBackupSalt;
    @SerializedName("private_key_backup_nonce")
    private String privateKeyBackupNonce;
    @SerializedName("private_key_backup_auth_tag")
    private String privateKeyBackupAuthTag;

    public String getEncryptedRsaPrivateKey() { return encryptedRsaPrivateKey; }
    public String getPrivateKeyKdf() { return privateKeyKdf; }
    public Map<String, Object> getPrivateKeyKdfParameters() { return privateKeyKdfParameters; }
    public String getPrivateKeyBackupSalt() { return privateKeyBackupSalt; }
    public String getPrivateKeyBackupNonce() { return privateKeyBackupNonce; }
    public String getPrivateKeyBackupAuthTag() { return privateKeyBackupAuthTag; }
}
