package com.marketplace.backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts secrets kept in the database (AI API keys) with AES-256-GCM. The key is derived from
 * app.secret-key (falling back to the JWT secret), so a database dump alone does not reveal the keys.
 */
@Component
public class SecretCipher {

    private static final String PREFIX = "enc1:";
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(@Value("${app.secret-key:${app.jwt.secret}}") String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(("shoppi-secrets:" + secret).getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(digest, "AES");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public String encrypt(String plain) {
        if (plain == null) return null;
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] out = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + out.length).put(iv).put(out).array());
        } catch (Exception e) {
            throw new IllegalStateException("Không mã hóa được khóa bí mật", e);
        }
    }

    /** The plain text, or null when the value can't be decrypted (e.g. the server secret changed). */
    public String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) return null;
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, all, 0, 12));
            return new String(c.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /** "sk-…a1b2" */
    public static String mask(String secret) {
        if (secret == null || secret.isBlank()) return null;
        String tail = secret.length() > 8 ? secret.substring(secret.length() - 4) : "";
        String head = secret.length() > 12 ? secret.substring(0, 3) : "";
        return head + "…" + tail;
    }
}
