package com.limitedgoods.limitedgoods.common.messaging.outbox.admin;

import com.limitedgoods.limitedgoods.common.messaging.outbox.dto.OutboxDeadEventResponse;
import com.limitedgoods.limitedgoods.common.messaging.outbox.dto.OutboxEvent;
import com.limitedgoods.limitedgoods.common.messaging.outbox.repository.OutboxJdbcRepository;
import com.limitedgoods.limitedgoods.common.response.ApiResponse;
import com.limitedgoods.limitedgoods.security.user.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/outbox")
@RequiredArgsConstructor
public class OutboxAdminController {

    private final OutboxAdminService outboxAdminService;

    @GetMapping("/dead")
    public ResponseEntity<ApiResponse<List<OutboxDeadEventResponse>>> findOutboxEventStatusInDead(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ){
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return ResponseEntity.ok(ApiResponse.success(
                outboxAdminService.findOutboxDeadEvents(safePage, safeSize)));
    }

    @PostMapping("/{eventId}/requeue")
    public ResponseEntity<ApiResponse<Void>> requeueEvent(
            @PathVariable("eventId")UUID eventId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ){
        outboxAdminService.requeue(userDetails.getUserId(), eventId);
        return ResponseEntity.ok(ApiResponse.success());
    }

}
