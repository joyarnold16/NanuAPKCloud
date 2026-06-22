package com.nanu.aitradingbot;

import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;

public class SecurePrefs {
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "nanu_secure_prefs_v1";
    private static final String ENC_PREFIX = "secure.";
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;
    private static final int PIN_SALT_BYTES = 16;
    private static final int PIN_ITERATIONS = 120000;

    private final SharedPreferences sp;

    public SecurePrefs(SharedPreferences sp) {
        this.sp = sp;
    }

    public String getSecret(String key, String fallback) {
        String encrypted = sp.getString(ENC_PREFIX + key, "");
        if (encrypted != null && !encrypted.isEmpty()) {
            try {
                return decrypt(encrypted);
            } catch (Exception ignored) {
                return fallback;
            }
        }
        String legacy = sp.getString(key, fallback);
        if (legacy != null && !legacy.isEmpty()) {
            try { putSecret(key, legacy); } catch (RuntimeException ignored) {}
        }
        return legacy == null ? fallback : legacy;
    }

    public void putSecret(String key, String value) {
        String safe = value == null ? "" : value;
        SharedPreferences.Editor editor = sp.edit().remove(key);
        try {
            if (safe.isEmpty()) editor.remove(ENC_PREFIX + key);
            else editor.putString(ENC_PREFIX + key, encrypt(safe));
        } catch (Exception e) {
            throw new RuntimeException("Keystore: " + e.getClass().getSimpleName()
                    + (e.getMessage() != null ? ": " + e.getMessage() : ""), e);
        }
        editor.apply();
    }

    public static String createPinHash(String pin) {
        try {
            byte[] salt = new byte[PIN_SALT_BYTES];
            new SecureRandom().nextBytes(salt);
            byte[] hash = pbkdf(pin, salt, PIN_ITERATIONS);
            return "v1$" + PIN_ITERATIONS + "$" + b64(salt) + "$" + b64(hash);
        } catch (Exception ignored) {
            return "";
        }
    }

    public static boolean verifyPin(String pin, String stored) {
        try {
            if (stored == null || stored.trim().isEmpty()) return pin == null || pin.trim().isEmpty();
            String[] parts = stored.split("\\$");
            if (parts.length != 4 || !"v1".equals(parts[0])) return false;
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.decode(parts[2], Base64.NO_WRAP);
            byte[] expected = Base64.decode(parts[3], Base64.NO_WRAP);
            byte[] actual = pbkdf(pin, salt, iterations);
            if (actual.length != expected.length) return false;
            int diff = 0;
            for (int i = 0; i < actual.length; i++) diff |= actual[i] ^ expected[i];
            return diff == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static byte[] pbkdf(String pin, byte[] salt, int iterations) throws Exception {
        PBEKeySpec spec = new PBEKeySpec((pin == null ? "" : pin).toCharArray(), salt, iterations, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception ignored) {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded();
        }
    }

    private String encrypt(String plain) throws Exception {
        byte[] iv = new byte[GCM_IV_BYTES];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        return b64(iv) + ":" + b64(encrypted);
    }

    private String decrypt(String packed) throws Exception {
        String[] parts = packed.split(":", 2);
        if (parts.length != 2) throw new IllegalArgumentException("Bad encrypted value");
        byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
        byte[] encrypted = Base64.decode(parts[1], Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true);
        if (Build.VERSION.SDK_INT >= 28) builder.setUnlockedDeviceRequired(false);
        generator.init(builder.build());
        return generator.generateKey();
    }

    private static String b64(byte[] data) {
        return Base64.encodeToString(data, Base64.NO_WRAP);
    }
}
