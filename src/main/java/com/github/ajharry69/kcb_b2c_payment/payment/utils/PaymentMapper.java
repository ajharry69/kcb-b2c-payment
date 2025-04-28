package com.github.ajharry69.kcb_b2c_payment.payment.utils;

import com.github.ajharry69.kcb_b2c_payment.payment.dto.PaymentRequest;
import com.github.ajharry69.kcb_b2c_payment.payment.dto.PaymentResponse;
import com.github.ajharry69.kcb_b2c_payment.payment.model.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;


@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface PaymentMapper {
    @Mapping(target = "id", ignore = true) // Let JPA generate ID
    @Mapping(target = "status", expression = "java(com.github.ajharry69.kcb_b2c_payment.payment.model.PaymentStatus.PENDING)")
    @Mapping(target = "failureReason", ignore = true)
    @Mapping(target = "mnoReference", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Payment toEntity(PaymentRequest paymentRequest);

    @Mapping(source = "id", target = "paymentId")
    PaymentResponse toResponse(Payment payment);
}
