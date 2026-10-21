package com.marketplace.backend.repository;

import com.marketplace.backend.entity.PolicyVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PolicyVersionRepository extends JpaRepository<PolicyVersion, Long> {

    Optional<PolicyVersion> findFirstByPolicyKeyOrderByVersionDesc(String policyKey);

    Optional<PolicyVersion> findByPolicyKeyAndVersion(String policyKey, Integer version);

    List<PolicyVersion> findByPolicyKeyOrderByVersionDesc(String policyKey);
}
