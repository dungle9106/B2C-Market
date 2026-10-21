package com.marketplace.backend.security;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * Time-based one-time passwords (RFC 6238, the codes of Google Authenticator / Microsoft Authenticator):
 * HMAC-SHA1 of a 30-second counter, 6 digits. One step of clock drift is accepted either way.
 */
@Component
public class TotpService {

    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private final SecureRandom random = new SecureRandom();

    /** A new random secret (160 bits), Base32 as the authenticator apps expect. */
    public String newSecret() {
        byte[] b = new byte[20];
        random.nextBytes(b);
        return base32(b);
    }

    /** otpauth:// link (shown as a QR code) for the authenticator app. */
    public String uri(String secret, String account) {
        String issuer = "Shoppi";
        return "otpauth://totp/" + enc(issuer + ":" + account) + "?secret=" + secret + "&issuer=" + enc(issuer) + "&algorithm=SHA1&digits=6&period=30";
    }

    public boolean verify(String secret, String code) {
        if (secret == null || code == null) return false;
        String c = code.replaceAll("\\s", "");
        if (!c.matches("\\d{6}")) return false;
        long step = System.currentTimeMillis() / 1000 / 30;
        for (long s = step - 1; s <= step + 1; s++) {
            if (code(secret, s).equals(c)) return true;
        }
        return false;
    }

    String code(String secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(unbase32(secret), "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int off = h[h.length - 1] & 0x0f;
            int bin = ((h[off] & 0x7f) << 24) | ((h[off + 1] & 0xff) << 16) | ((h[off + 2] & 0xff) << 8) | (h[off + 3] & 0xff);
            return String.format("%06d", bin % 1_000_000);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String base32(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                sb.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) sb.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        return sb.toString();
    }

    private static byte[] unbase32(String s) {
        String in = s.replace("=", "").toUpperCase();
        ByteBuffer out = ByteBuffer.allocate(in.length() * 5 / 8);
        int buffer = 0, bits = 0;
        for (char ch : in.toCharArray()) {
            int v = BASE32.indexOf(ch);
            if (v < 0) throw new IllegalArgumentException("not base32");
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) (buffer >> (bits - 8)));
                bits -= 8;
            }
        }
        return out.array();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
