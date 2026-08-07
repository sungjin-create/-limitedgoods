package com.limitedgoods.limitedgoods.notification.controller;

import com.limitedgoods.limitedgoods.common.response.ApiResponse;
import com.limitedgoods.limitedgoods.notification.dto.NotificationResponse;
import com.limitedgoods.limitedgoods.notification.service.NotificationCommandService;
import com.limitedgoods.limitedgoods.notification.service.NotificationQueryService;
import com.limitedgoods.limitedgoods.security.user.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationQueryService queryService;
    private final NotificationCommandService commandService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> findAll(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                queryService.findMyNotifications(userDetails.getUserId())
                )
        );
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Long>> countUnread(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                        queryService.countUnread(userDetails.getUserId())
                )
        );
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable Long notificationId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        commandService.markAsRead(userDetails.getUserId(), notificationId);

        return ResponseEntity.ok(ApiResponse.success());
    }
}