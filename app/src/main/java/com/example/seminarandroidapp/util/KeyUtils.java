package com.example.seminarandroidapp.util;

import android.util.Base64;

import com.example.seminarandroidapp.api.PrivateKeyBackupResponse;
import com.google.gson.annotations.SerializedName;

import org.bouncycastle.crypto.generators.SCrypt;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

public final class KeyUtils {
    private static final int SCRYPT_N = 16384;
    private static final int SCRYPT_R = 8;
    private static final int SCRYPT_P = 1;
    private static final int GCM_TAG_BYTES = 16;

    private KeyUtils() {}

    public static KeyPair generateRsaKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    public static String exportPublicKeyToPem(PublicKey publicKey) {
        if (publicKey == null) return null;
        String key = Base64.encodeToString(publicKey.getEncoded(), Base64.NO_WRAP);
        return "-----BEGIN PUBLIC KEY-----\n" + key + "\n-----END PUBLIC KEY-----";
    }

    public static PublicKey importPublicKeyFromPem(String pem) throws Exception {
        if (pem == null) throw new IllegalArgumentException("Missing RSA public key");
        String encoded = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.decode(encoded, Base64.DEFAULT);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    public static String wrapConversationKey(byte[] conversationKey, PublicKey publicKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        OAEPParameterSpec parameters = new OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
        cipher.init(Cipher.ENCRYPT_MODE, publicKey, parameters);
        return Base64.encodeToString(cipher.doFinal(conversationKey), Base64.NO_WRAP);
    }

    public static byte[] unwrapConversationKey(String encryptedKey, PrivateKey privateKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        OAEPParameterSpec parameters = new OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
        cipher.init(Cipher.DECRYPT_MODE, privateKey, parameters);
        return cipher.doFinal(Base64.decode(encryptedKey, Base64.DEFAULT));
    }

    public static byte[] generateConversationKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }

    public static EncryptedPayload encryptMessage(String plaintext, byte[] key) throws Exception {
        byte[] nonce = new byte[12];
        new SecureRandom().nextBytes(nonce);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        byte[] ciphertextAndTag = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        int ciphertextLength = ciphertextAndTag.length - GCM_TAG_BYTES;
        byte[] ciphertext = Arrays.copyOfRange(ciphertextAndTag, 0, ciphertextLength);
        byte[] tag = Arrays.copyOfRange(ciphertextAndTag, ciphertextLength, ciphertextAndTag.length);
        return new EncryptedPayload(
                Base64.encodeToString(ciphertext, Base64.NO_WRAP),
                Base64.encodeToString(nonce, Base64.NO_WRAP),
                Base64.encodeToString(tag, Base64.NO_WRAP));
    }

    public static String decryptMessage(String ciphertext, String nonce, String authTag, byte[] key) throws Exception {
        byte[] encrypted = Base64.decode(ciphertext, Base64.DEFAULT);
        byte[] tag = Base64.decode(authTag, Base64.DEFAULT);
        byte[] combined = new byte[encrypted.length + tag.length];
        System.arraycopy(encrypted, 0, combined, 0, encrypted.length);
        System.arraycopy(tag, 0, combined, encrypted.length, tag.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(128, Base64.decode(nonce, Base64.DEFAULT)));
        return new String(cipher.doFinal(combined), StandardCharsets.UTF_8);
    }

    public static BackupResult createPrivateKeyBackup(PrivateKey privateKey, String recoverySecret) throws Exception {
        byte[] salt = new byte[32];
        byte[] nonce = new byte[12];
        SecureRandom random = new SecureRandom();
        random.nextBytes(salt);
        random.nextBytes(nonce);

        byte[] keyBytes = SCrypt.generate(recoverySecret.getBytes(StandardCharsets.UTF_8),
                salt, SCRYPT_N, SCRYPT_R, SCRYPT_P, 32);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(128, nonce));
        byte[] encrypted = cipher.doFinal(privateKey.getEncoded());
        int ciphertextLength = encrypted.length - GCM_TAG_BYTES;

        Map<String, Object> params = new HashMap<>();
        params.put("N", SCRYPT_N);
        params.put("r", SCRYPT_R);
        params.put("p", SCRYPT_P);

        BackupResult result = new BackupResult();
        result.encryptedRsaPrivateKey = Base64.encodeToString(
                Arrays.copyOfRange(encrypted, 0, ciphertextLength), Base64.NO_WRAP);
        result.privateKeyKdf = "scrypt";
        result.privateKeyKdfParameters = params;
        result.privateKeyBackupSalt = Base64.encodeToString(salt, Base64.NO_WRAP);
        result.privateKeyBackupNonce = Base64.encodeToString(nonce, Base64.NO_WRAP);
        result.privateKeyBackupAuthTag = Base64.encodeToString(
                Arrays.copyOfRange(encrypted, ciphertextLength, encrypted.length), Base64.NO_WRAP);
        Arrays.fill(keyBytes, (byte) 0);
        return result;
    }

    public static PrivateKey restorePrivateKeyBackup(PrivateKeyBackupResponse backup, String recoverySecret)
            throws Exception {
        if (backup == null || backup.getEncryptedRsaPrivateKey() == null
                || backup.getPrivateKeyBackupSalt() == null
                || backup.getPrivateKeyBackupNonce() == null
                || backup.getPrivateKeyBackupAuthTag() == null) {
            throw new IllegalStateException("No encrypted private-key backup is available.");
        }

        byte[] salt = Base64.decode(backup.getPrivateKeyBackupSalt(), Base64.DEFAULT);
        byte[] nonce = Base64.decode(backup.getPrivateKeyBackupNonce(), Base64.DEFAULT);
        byte[] ciphertext = Base64.decode(backup.getEncryptedRsaPrivateKey(), Base64.DEFAULT);
        byte[] tag = Base64.decode(backup.getPrivateKeyBackupAuthTag(), Base64.DEFAULT);
        byte[] encrypted = new byte[ciphertext.length + tag.length];
        System.arraycopy(ciphertext, 0, encrypted, 0, ciphertext.length);
        System.arraycopy(tag, 0, encrypted, ciphertext.length, tag.length);

        byte[] privateKeyDer;
        String kdf = backup.getPrivateKeyKdf();
        if ("scrypt".equalsIgnoreCase(kdf)) {
            Map<String, Object> params = backup.getPrivateKeyKdfParameters();
            int n = readInt(params, "N", SCRYPT_N);
            int r = readInt(params, "r", SCRYPT_R);
            int p = readInt(params, "p", SCRYPT_P);
            try {
                byte[] derived = SCrypt.generate(recoverySecret.getBytes(StandardCharsets.UTF_8), salt, n, r, p, 32);
                privateKeyDer = decryptBackup(encrypted, nonce, derived);
                Arrays.fill(derived, (byte) 0);
            } catch (AEADBadTagException badTag) {
                // Earlier app builds labeled a PBKDF2-derived backup as scrypt.
                byte[] legacyDerived = deriveLegacyPbkdf2(recoverySecret, salt, n);
                privateKeyDer = decryptBackup(encrypted, nonce, legacyDerived);
                Arrays.fill(legacyDerived, (byte) 0);
            }
        } else {
            throw new IllegalArgumentException("Unsupported private-key backup KDF: " + kdf);
        }
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(privateKeyDer));
    }

    private static byte[] decryptBackup(byte[] encrypted, byte[] nonce, byte[] key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        return cipher.doFinal(encrypted);
    }

    private static byte[] deriveLegacyPbkdf2(String secret, byte[] salt, int iterations) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(secret.toCharArray(), salt, iterations, 256);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private static int readInt(Map<String, Object> values, String key, int fallback) {
        if (values == null || values.get(key) == null) return fallback;
        Object value = values.get(key);
        return value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(value.toString());
    }

    public static class EncryptedPayload {
        public final String ciphertext;
        public final String nonce;
        public final String authTag;

        public EncryptedPayload(String ciphertext, String nonce, String authTag) {
            this.ciphertext = ciphertext;
            this.nonce = nonce;
            this.authTag = authTag;
        }
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