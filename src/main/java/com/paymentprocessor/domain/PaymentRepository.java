package com.paymentprocessor.domain;

import com.paymentprocessor.domain.entities.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByExternalId(String externalId);
    boolean existsByExternalId(String externalId);
}
