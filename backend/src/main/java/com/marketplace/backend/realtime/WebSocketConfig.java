package com.marketplace.backend.realtime;

import com.marketplace.backend.security.JwtService;
import com.marketplace.backend.security.UserPrincipal;
import com.marketplace.backend.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

/**
 * Realtime channel: STOMP over a plain WebSocket at /ws (the frontend uses @stomp/stompjs).
 * The client authenticates in the STOMP CONNECT frame with the same JWT as the REST API
 * ("Authorization: Bearer ..."), checked like JwtAuthFilter does (signature, account enabled, session active).
 * A client may only subscribe to its own queue: /user/queue/**.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final SessionService sessionService;

    // same origins as the REST API's CORS (CORS_ALLOWED_ORIGINS)
    @Value("${app.cors.allowed-origins:http://localhost:*}")
    private String allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns(java.util.Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).filter(o -> !o.isEmpty()).toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor acc = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (acc == null || acc.getCommand() == null) return message;
                if (acc.getCommand() == StompCommand.CONNECT) {
                    acc.setUser(authenticate(acc.getFirstNativeHeader("Authorization")));
                } else if (acc.getCommand() == StompCommand.SUBSCRIBE) {
                    String dest = acc.getDestination();
                    if (acc.getUser() == null || dest == null || !dest.startsWith("/user/queue/")) {
                        throw new MessageDeliveryException("Không được phép đăng ký kênh này");
                    }
                } else if (acc.getCommand() == StompCommand.SEND) {
                    // the server only pushes; clients talk to it through the REST API
                    throw new MessageDeliveryException("Kênh chỉ nhận");
                }
                return message;
            }
        });
    }

    private UsernamePasswordAuthenticationToken authenticate(String header) {
        if (header == null || !header.startsWith("Bearer ")) throw new MessageDeliveryException("Thiếu token");
        String token = header.substring(7);
        try {
            String email = jwtService.extractEmail(token);
            UserPrincipal user = (UserPrincipal) userDetailsService.loadUserByUsername(email);
            String sid = jwtService.extractSessionId(token);
            if (!user.isEnabled() || !jwtService.isTokenValid(token, email) || !sessionService.isActive(sid)) {
                throw new MessageDeliveryException("Token không hợp lệ");
            }
            // the principal's name is the user id: RealtimePublisher sends to /user/{id}/queue/events
            return new UsernamePasswordAuthenticationToken(String.valueOf(user.getId()), null, List.of());
        } catch (MessageDeliveryException e) {
            throw e;
        } catch (Exception e) {
            throw new MessageDeliveryException("Token không hợp lệ");
        }
    }
}
