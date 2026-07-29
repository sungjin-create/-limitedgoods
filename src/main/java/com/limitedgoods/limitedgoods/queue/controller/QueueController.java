package com.limitedgoods.limitedgoods.queue.controller;

import com.limitedgoods.limitedgoods.common.response.ApiResponse;
import com.limitedgoods.limitedgoods.queue.dto.QueueEnterRequest;
import com.limitedgoods.limitedgoods.queue.dto.QueueHeartbeatRequest;
import com.limitedgoods.limitedgoods.queue.dto.QueueStatusResponse;
import com.limitedgoods.limitedgoods.queue.service.QueueService;
import com.limitedgoods.limitedgoods.security.user.CustomUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user/queue")
@RequiredArgsConstructor
@Validated
public class QueueController {

    private final QueueService queueService;

    /**
     * 대기열 진입
     * 즉시 입장 가능하면 admitted=true + admissionToken 반환
     * 대기 중이면 admitted=false + position 반환
     */
    @PostMapping("/enter")
    public ResponseEntity<ApiResponse<QueueStatusResponse>> enter(
            @Valid @RequestBody QueueEnterRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        QueueStatusResponse response = queueService.enterQueue(
                userDetails.getUserId(),
                request.productId()
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 대기 상태 폴링 (2~3초 간격으로 호출)
     * admitted=true가 될 때까지 클라이언트가 반복 호출
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<QueueStatusResponse>> getStatus(
            @RequestParam
            @Positive(message = "productId는 1 이상의 값이어야 합니다.")
            Long productId,

            @AuthenticationPrincipal
            CustomUserDetails userDetails
    ) {
        QueueStatusResponse response = queueService.getStatus(
                userDetails.getUserId(),
                productId
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<ApiResponse<Void>> heartbeat(
            @Valid @RequestBody QueueHeartbeatRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        queueService.heartbeat(userDetails.getUserId(), request.productId());

        return ResponseEntity.ok(ApiResponse.success());
    }

    @DeleteMapping("/leave")
    public ResponseEntity<ApiResponse<Void>> leave(
            @RequestParam
            @Positive(message = "productId는 1 이상의 값이어야 합니다.")
            Long productId,

            @AuthenticationPrincipal
            CustomUserDetails userDetails
    ) {
        queueService.leaveQueue(
                userDetails.getUserId(),
                productId
        );

        return ResponseEntity.ok(ApiResponse.success());
    }
}