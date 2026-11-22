package com.example.seminarandroidapp.util;

import android.util.Base64;
import java.nio.charset.StandardCharsets;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class SignalManager {

    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";

    public static String encrypt(String plainText, byte[] secretKey) throws Exception {
        byte[] keyBytes = formatKey(secretKey);
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        byte[] iv = new byte[16];
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(iv));
        byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(encrypted, Base64.NO_WRAP);
    }

    public static String decrypt(String encryptedMessage, byte[] secretKey) throws Exception {
        byte[] keyBytes = formatKey(secretKey);
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        byte[] iv = new byte[16];
        cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(iv));
        byte[] decoded = Base64.decode(encryptedMessage, Base64.NO_WRAP);
        byte[] decrypted = cipher.doFinal(decoded);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    private static byte[] formatKey(byte[] secretKey) {
        byte[] key = new byte[16];
        if (secretKey != null) {
            System.arraycopy(secretKey, 0, key, 0, Math.min(secretKey.length, 16));
        }
        return key;
    }
}