package com.example.seminarandroidapp.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.KeyFactory;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class LocalKeyStore {
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String PREFS_NAME = "secure_chat_key_material";
    private static final String KEY_PREFIX = "chat_local_key_";
    private static final int IV_LENGTH = 12;

    private LocalKeyStore() {}

    public static void savePrivateKey(Context context, String userId, PrivateKey privateKey) throws Exception {
        save(context, userId, "private_key", privateKey.getEncoded());
    }

    public static PrivateKey loadPrivateKey(Context context, String userId) throws Exception {
        byte[] encoded = load(context, userId, "private_key");
        if (encoded == null) {
            return null;
        }
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
    }

    public static void saveConversationKey(Context context, String userId, String conversationId,
                                           byte[] conversationKey) throws Exception {
        save(context, userId, "conversation_" + conversationId, conversationKey);
    }

    public static byte[] loadConversationKey(Context context, String userId, String conversationId)
            throws Exception {
        return load(context, userId, "conversation_" + conversationId);
    }

    private static void save(Context context, String userId, String name, byte[] plaintext) throws Exception {
        if (userId == null || userId.isEmpty() || plaintext == null) {
            throw new IllegalArgumentException("Missing key data");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(userId));
        cipher.updateAAD(aad(userId, name));
        byte[] encrypted = cipher.doFinal(plaintext);
        ByteBuffer blob = ByteBuffer.allocate(cipher.getIV().length + encrypted.length);
        blob.put(cipher.getIV());
        blob.put(encrypted);
        preferences(context).edit()
                .putString(storageKey(userId, name), Base64.encodeToString(blob.array(), Base64.NO_WRAP))
                .apply();
        Arrays.fill(encrypted, (byte) 0);
    }

    private static byte[] load(Context context, String userId, String name) throws Exception {
        if (userId == null || userId.isEmpty()) {
            return null;
        }
        String encoded = preferences(context).getString(storageKey(userId, name), null);
        if (encoded == null) {
            return null;
        }
        byte[] blob = Base64.decode(encoded, Base64.NO_WRAP);
        if (blob.length <= IV_LENGTH) {
            throw new IllegalStateException("Stored key data is invalid");
        }
        byte[] iv = Arrays.copyOfRange(blob, 0, IV_LENGTH);
        byte[] ciphertext = Arrays.copyOfRange(blob, IV_LENGTH, blob.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(userId), new GCMParameterSpec(128, iv));
        cipher.updateAAD(aad(userId, name));
        return cipher.doFinal(ciphertext);
    }

    private static SecretKey getOrCreateKey(String userId) throws Exception {
        String alias = KEY_PREFIX + userId;
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        if (keyStore.containsAlias(alias)) {
            return (SecretKey) keyStore.getKey(alias, null);
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(
                alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static String storageKey(String userId, String name) {
        return userId + "_" + name;
    }

    private static byte[] aad(String userId, String name) {
        return ("SecureChat|" + userId + "|" + name).getBytes(StandardCharsets.UTF_8);
    }
}
