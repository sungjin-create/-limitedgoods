package com.limitedgoods.limitedgoods.queue.listener;

import com.limitedgoods.limitedgoods.queue.infrastructure.redis.AdmissionTrackKey;
import com.limitedgoods.limitedgoods.queue.infrastructure.redis.QueueRedisKeys;
import com.limitedgoods.limitedgoods.queue.service.QueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdmissionTokenExpiredListener implements MessageListener {

    private final QueueService queueService;

    @Override
    public void onMessage(
            Message message,
            byte[] pattern
    ) {
        String expiredKey = new String(message.getBody(), StandardCharsets.UTF_8);

        Optional<AdmissionTrackKey> parsedKey = QueueRedisKeys.parseAdmissionTrackKey(expiredKey);

        if (parsedKey.isEmpty()) {
            return;
        }

        AdmissionTrackKey trackKey = parsedKey.get();

        try {
            queueService.removeFromQueue(trackKey.userId(), trackKey.productId());

            log.info(
                    "입장 토큰 만료로 대기열 제거 "
                            + "userId={}, productId={}",
                    trackKey.userId(),
                    trackKey.productId()
            );
        } catch (Exception e) {
            log.error(
                    "입장 토큰 만료 처리 실패 "
                            + "key={}, userId={}, productId={}",
                    expiredKey,
                    trackKey.userId(),
                    trackKey.productId(),
                    e
            );
        }
    }
}