package com.marketplace.backend.security;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.*;

/**
 * Loads the Facebook / GitHub profile (Google uses OpenID Connect and needs nothing extra) and gives every
 * provider the same attribute names: sub, email, email_verified, name.
 *  - Facebook only returns an e-mail it has confirmed (and none for phone-only accounts).
 *  - GitHub hides the e-mail when the user made it private, so the verified primary one is read from /user/emails.
 */
@Component
public class SocialOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final RestClient rest = RestClient.create();

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User user = delegate.loadUser(request);
        String provider = request.getClientRegistration().getRegistrationId();
        Map<String, Object> a = new HashMap<>(user.getAttributes());
        a.put("sub", String.valueOf(a.get("id")));
        switch (provider) {
            case "github" -> {
                if (a.get("name") == null || String.valueOf(a.get("name")).isBlank()) a.put("name", a.get("login"));
                String email = githubEmail(request.getAccessToken().getTokenValue());
                a.put("email", email);
                a.put("email_verified", email != null);
            }
            case "facebook" -> a.put("email_verified", a.get("email") != null);
            default -> a.putIfAbsent("email_verified", false);
        }
        return new DefaultOAuth2User(user.getAuthorities(), a, "sub");
    }

    private String githubEmail(String token) {
        try {
            List<Map<String, Object>> emails = rest.get().uri("https://api.github.com/user/emails")
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() { });
            if (emails == null) return null;
            return emails.stream()
                    .filter(e -> Boolean.TRUE.equals(e.get("verified")))
                    .sorted(Comparator.comparing(e -> !Boolean.TRUE.equals(e.get("primary"))))
                    .map(e -> String.valueOf(e.get("email")))
                    .filter(e -> !e.endsWith("@users.noreply.github.com"))
                    .findFirst().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
