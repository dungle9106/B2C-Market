package com.marketplace.backend.push;

import com.marketplace.backend.entity.PlatformSetting;
import com.marketplace.backend.entity.PushSubscription;
import com.marketplace.backend.repository.PlatformSettingRepository;
import com.marketplace.backend.security.SecretCipher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;

/**
 * Sends Web Push messages (RFC 8030) to browsers, written against the JDK only:
 *  - the payload is encrypted for the browser with "aes128gcm" (RFC 8291 + RFC 8188);
 *  - the request is signed with the server's VAPID key (RFC 8292), an ES256 JWT.
 * The VAPID key pair is generated on first use and kept in platform_settings (private key encrypted), so no
 * configuration or outside account is needed.
 */
@Component
@RequiredArgsConstructor
public class WebPushSender {

    private static final String PUBLIC_KEY = "push.vapid.public";
    private static final String PRIVATE_KEY = "push.vapid.private";
    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64URL_DEC = Base64.getUrlDecoder();

    private final PlatformSettingRepository settings;
    private final SecretCipher cipher;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private volatile KeyPair vapid;

    /** Result of one send: "gone" means the browser unsubscribed and the subscription should be deleted. */
    public enum Result { OK, GONE, FAILED }

    /** The VAPID public key (uncompressed point, base64url) the browser needs to subscribe. */
    public String publicKey() {
        return B64URL.encodeToString(uncompressed((ECPublicKey) vapid().getPublic()));
    }

    public Result send(PushSubscription s, String json, String subject) {
        try {
            byte[] body = encrypt(json.getBytes(StandardCharsets.UTF_8), B64URL_DEC.decode(s.getP256dh()), B64URL_DEC.decode(s.getAuth()));
            URI uri = URI.create(s.getEndpoint());
            String audience = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
            HttpRequest req = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(15))
                    .header("TTL", "86400")
                    .header("Urgency", "normal")
                    .header("Content-Type", "application/octet-stream")
                    .header("Content-Encoding", "aes128gcm")
                    .header("Authorization", "vapid t=" + jwt(audience, subject) + ", k=" + publicKey())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            int status = http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status == 404 || status == 410) return Result.GONE;
            return status >= 200 && status < 300 ? Result.OK : Result.FAILED;
        } catch (IllegalArgumentException e) {
            return Result.GONE; // malformed keys or endpoint: the subscription is useless
        } catch (Exception e) {
            return Result.FAILED;
        }
    }

    // ==================== RFC 8291 encryption ====================

    static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret) throws GeneralSecurityException {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return encrypt(plaintext, uaPublic, authSecret, gen.generateKeyPair(), salt); // a fresh key for every message
    }

    static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret, KeyPair as, byte[] salt) throws GeneralSecurityException {
        ECParameterSpec params = p256();
        byte[] asPublic = uncompressed((ECPublicKey) as.getPublic());

        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(as.getPrivate());
        ka.doPhase(toPublicKey(uaPublic, params), true);
        byte[] ecdhSecret = ka.generateSecret();

        byte[] prkKey = hmac(authSecret, ecdhSecret);
        byte[] keyInfo = concat("WebPush: info".getBytes(StandardCharsets.US_ASCII), new byte[]{0}, uaPublic, asPublic, new byte[]{1});
        byte[] ikm = hmac(prkKey, keyInfo);

        byte[] prk = hmac(salt, ikm);
        byte[] cek = Arrays.copyOf(hmac(prk, concat("Content-Encoding: aes128gcm".getBytes(StandardCharsets.US_ASCII), new byte[]{0, 1})), 16);
        byte[] nonce = Arrays.copyOf(hmac(prk, concat("Content-Encoding: nonce".getBytes(StandardCharsets.US_ASCII), new byte[]{0, 1})), 12);

        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] ciphertext = aes.doFinal(concat(plaintext, new byte[]{2})); // 0x02: last (and only) record

        // header: salt | record size | key id length | key id (our public key) | ciphertext
        return concat(salt, ByteBuffer.allocate(4).putInt(4096).array(), new byte[]{(byte) asPublic.length}, asPublic, ciphertext);
    }

    // ==================== VAPID ====================

    private String jwt(String audience, String subject) throws GeneralSecurityException {
        String header = B64URL.encodeToString("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        long exp = System.currentTimeMillis() / 1000 + 12 * 3600;
        String claims = B64URL.encodeToString(("{\"aud\":\"" + audience + "\",\"exp\":" + exp + ",\"sub\":\"" + subject + "\"}")
                .getBytes(StandardCharsets.UTF_8));
        Signature sig = Signature.getInstance("SHA256withECDSAinP1363Format"); // raw r|s, as JWS wants
        sig.initSign(vapid().getPrivate());
        sig.update((header + "." + claims).getBytes(StandardCharsets.US_ASCII));
        return header + "." + claims + "." + B64URL.encodeToString(sig.sign());
    }

    private KeyPair vapid() {
        KeyPair k = vapid;
        if (k != null) return k;
        synchronized (this) {
            if (vapid != null) return vapid;
            try {
                String pub = settings.findById(PUBLIC_KEY).map(PlatformSetting::getValue).orElse(null);
                String priv = settings.findById(PRIVATE_KEY).map(PlatformSetting::getValue).orElse(null);
                KeyFactory kf = KeyFactory.getInstance("EC");
                if (pub != null && priv != null) {
                    vapid = new KeyPair(toPublicKey(B64URL_DEC.decode(pub), p256()),
                            kf.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(cipher.decrypt(priv)))));
                } else {
                    KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
                    gen.initialize(new ECGenParameterSpec("secp256r1"));
                    KeyPair created = gen.generateKeyPair();
                    LocalDateTime now = LocalDateTime.now();
                    settings.save(new PlatformSetting(PUBLIC_KEY, B64URL.encodeToString(uncompressed((ECPublicKey) created.getPublic())), now, null));
                    settings.save(new PlatformSetting(PRIVATE_KEY,
                            cipher.encrypt(Base64.getEncoder().encodeToString(((ECPrivateKey) created.getPrivate()).getEncoded())), now, null));
                    vapid = created;
                }
                return vapid;
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("Không tạo được khóa VAPID", e);
            }
        }
    }

    // ==================== helpers ====================

    static ECParameterSpec p256() throws GeneralSecurityException {
        AlgorithmParameters p = AlgorithmParameters.getInstance("EC");
        p.init(new ECGenParameterSpec("secp256r1"));
        return p.getParameterSpec(ECParameterSpec.class);
    }

    static PublicKey toPublicKey(byte[] point, ECParameterSpec params) throws GeneralSecurityException {
        if (point.length != 65 || point[0] != 4) throw new IllegalArgumentException("bad EC point");
        ECPoint w = new ECPoint(new BigInteger(1, Arrays.copyOfRange(point, 1, 33)), new BigInteger(1, Arrays.copyOfRange(point, 33, 65)));
        return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(w, params));
    }

    static byte[] uncompressed(ECPublicKey key) {
        return concat(new byte[]{4}, fixed32(key.getW().getAffineX()), fixed32(key.getW().getAffineY()));
    }

    private static byte[] fixed32(BigInteger v) {
        byte[] b = v.toByteArray();
        if (b.length == 32) return b;
        byte[] out = new byte[32];
        if (b.length > 32) System.arraycopy(b, b.length - 32, out, 0, 32); // drop the sign byte
        else System.arraycopy(b, 0, out, 32 - b.length, b.length);
        return out;
    }

    private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) out.writeBytes(p);
        return out.toByteArray();
    }
}
