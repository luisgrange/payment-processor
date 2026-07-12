package com.paymentprocessor.api;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import com.paymentprocessor.api.dto.PaymentRequest;
import com.paymentprocessor.domain.PaymentRepository;
import com.paymentprocessor.domain.enums.PaymentStatus;
import com.paymentprocessor.domain.entities.Payment;
import com.paymentprocessor.messaging.PaymentProducer;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


import java.math.BigDecimal;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class PaymentControllerTest {
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentProducer paymentProducer;
    @InjectMocks
    private PaymentController paymentController;

    @Test
    void deveCriarPagamentoQuandoExternalIdNaoExiste() {
        var request = new PaymentRequest("cust-001", BigDecimal.valueOf(150), "ext-001");

        when(paymentRepository.findByExternalId("ext-001")).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = paymentController.create(request);

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        verify(paymentProducer, times(1)).publishPaymentCreated(any(Payment.class));
    }

    @Test
    void deveRejeitarQuandoExternalIdJaExiste() {
        var request = new PaymentRequest("cust-001", BigDecimal.valueOf(150), "ext-001");
        var existente = new Payment("cust-001", BigDecimal.valueOf(150), "ext-001", PaymentStatus.PENDING);

        when(paymentRepository.findByExternalId("ext-001")).thenReturn(Optional.of(existente));

        assertThrows(PaymentAlreadyExistsException.class, () -> paymentController.create(request));

        verify(paymentProducer, never()).publishPaymentCreated(any());
    }
}
