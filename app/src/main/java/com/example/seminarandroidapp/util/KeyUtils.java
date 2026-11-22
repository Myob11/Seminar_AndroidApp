package com.example.seminarandroidapp.util;

import android.util.Base64;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class KeyUtils {

    public static KeyPair generateRsaKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        return kpg.generateKeyPair();
    }

    public static String exportPublicKeyToPem(PublicKey publicKey) {
        if (publicKey == null) return null;
        String base64Key = Base64.encodeToString(publicKey.getEncoded(), Base64.NO_WRAP);
        return "-----BEGIN PUBLIC KEY-----\n" + base64Key + "\n-----END PUBLIC KEY-----";
    }

    public static BackupResult createPrivateKeyBackup(PrivateKey privateKey, String recoverySecret) throws Exception {
        byte[] salt = new byte[32];
        byte[] nonce = new byte[12];
        SecureRandom random = new SecureRandom();
        random.nextBytes(salt);
        random.nextBytes(nonce);

        PBEKeySpec spec = new PBEKeySpec(recoverySecret.toCharArray(), salt, 16384, 256);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        SecretKey secretKey = new SecretKeySpec(keyBytes, "AES");

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, nonce);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);

        byte[] privateKeyDer = privateKey.getEncoded();
        byte[] cipherTextWithTag = cipher.doFinal(privateKeyDer);

        int tagLength = 16;
        int cipherTextLen = cipherTextWithTag.length - tagLength;
        byte[] cipherTextOnly = new byte[cipherTextLen];
        byte[] authTag = new byte[tagLength];

        System.arraycopy(cipherTextWithTag, 0, cipherTextOnly, 0, cipherTextLen);
        System.arraycopy(cipherTextWithTag, cipherTextLen, authTag, 0, tagLength);

        Map<String, Object> kdfParams = new HashMap<>();
        kdfParams.put("N", 16384);
        kdfParams.put("r", 8);
        kdfParams.put("p", 1);

        BackupResult result = new BackupResult();
        result.encryptedRsaPrivateKey = Base64.encodeToString(cipherTextOnly, Base64.NO_WRAP);
        result.privateKeyKdf = "scrypt";
        result.privateKeyKdfParameters = kdfParams;
        result.privateKeyBackupSalt = Base64.encodeToString(salt, Base64.NO_WRAP);
        result.privateKeyBackupNonce = Base64.encodeToString(nonce, Base64.NO_WRAP);
        result.privateKeyBackupAuthTag = Base64.encodeToString(authTag, Base64.NO_WRAP);

        return result;
    }

    public static class BackupResult {
        public String encryptedRsaPrivateKey;
        public String privateKeyKdf;
        public Map<String, Object> privateKeyKdfParameters;
        public String privateKeyBackupSalt;
        public String privateKeyBackupNonce;
        public String privateKeyBackupAuthTag;
    }
}