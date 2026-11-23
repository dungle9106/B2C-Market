package com.marketplace.backend.repository;

import com.marketplace.backend.entity.AuthProvider;
import com.marketplace.backend.entity.OAuthAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OAuthAccountRepository extends JpaRepository<OAuthAccount, Long> {

    Optional<OAuthAccount> findByProviderAndSubject(AuthProvider provider, String subject);

    Optional<OAuthAccount> findByUserIdAndProvider(Long userId, AuthProvider provider);

    List<OAuthAccount> findByUserIdOrderByIdAsc(Long userId);
}
