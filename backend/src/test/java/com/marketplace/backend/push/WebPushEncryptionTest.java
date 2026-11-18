package com.marketplace.backend.push;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.spec.ECPrivateKeySpec;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The aes128gcm Web Push encryption must match the worked example of RFC 8291, Appendix A, byte for byte. */
class WebPushEncryptionTest {

    private static byte[] b64(String s) {
        return Base64.getUrlDecoder().decode(s);
    }

    @Test
    void matchesRfc8291Example() throws Exception {
        var params = WebPushSender.p256();
        var asPublic = WebPushSender.toPublicKey(b64("BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8"), params);
        var asPrivate = KeyFactory.getInstance("EC").generatePrivate(
                new ECPrivateKeySpec(new BigInteger(1, b64("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw")), params));

        byte[] body = WebPushSender.encrypt(
                "When I grow up, I want to be a watermelon".getBytes(StandardCharsets.UTF_8),
                b64("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4"),
                b64("BTBZMqHH6r4Tts7J_aSIgg"),
                new KeyPair(asPublic, asPrivate),
                b64("DGv6ra1nlYgDCS1FRnbzlw"));

        assertEquals("DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN",
                Base64.getUrlEncoder().withoutPadding().encodeToString(body));
    }
}
