package com.limitedgoods.limitedgoods.notification.service;

import com.limitedgoods.limitedgoods.notification.dto.NotificationResponse;
import com.limitedgoods.limitedgoods.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationQueryService {

    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public List<NotificationResponse> findMyNotifications(Long userId) {
        return notificationRepository
                .findTop50ByUserIdOrderByIdDesc(userId)
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long countUnread(Long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }
}