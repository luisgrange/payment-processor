package com.paymentprocessor.api;

import com.paymentprocessor.api.dto.PaymentRequest;
import com.paymentprocessor.api.dto.PaymentResponse;
import com.paymentprocessor.domain.PaymentRepository;
import com.paymentprocessor.domain.entities.Payment;
import com.paymentprocessor.domain.enums.PaymentStatus;
import com.paymentprocessor.messaging.PaymentProducer;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/payments")
@AllArgsConstructor
public class PaymentController {

    private final PaymentRepository paymentRepository;
    private final PaymentProducer paymentProducer;


    @PostMapping
    public ResponseEntity<PaymentResponse> create(@RequestBody PaymentRequest request){
        paymentRepository.findByExternalId(request.externalId())
                .ifPresent(p -> {
                    throw new PaymentAlreadyExistsException(request.externalId());
                });

        Payment payment = new Payment(
                request.customerId(),
                request.amount(),
                request.externalId(),
                PaymentStatus.PENDING
        );

        paymentRepository.save(payment);
        paymentProducer.publishPaymentCreated(payment);

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(PaymentResponse.from(payment));
    }

    @GetMapping("/{externalId}")
    public ResponseEntity<PaymentResponse> getByExternalId(@PathVariable String externalId) {
        return paymentRepository.findByExternalId(externalId)
                .map(PaymentResponse::from)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new PaymentNotFoundException(externalId));
    }
}
