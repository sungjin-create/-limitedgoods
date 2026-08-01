package com.limitedgoods.limitedgoods.order.application.create.dto;

public record OrderAdmissionClaim(
        String admissionToken,
        Long userId,
        Long productId,
        String claimId,
        long generation
) {
}
