package com.limitedgoods.limitedgoods.queue.service;

import com.limitedgoods.limitedgoods.product.entity.ProductType;
import com.limitedgoods.limitedgoods.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class QueueProductStateWarmup {

    private final ProductRepository productRepository;
    private final QueueProductStateCacheService queueProductStateCacheService;

    /*
     * 대기열은 LIMITED 상품만 적용
     * 어플리케이션이 시작될때, Redis 대기열을 위해 등록
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void warmup() {
        productRepository.findAllByType(ProductType.LIMITED)
                .forEach(queueProductStateCacheService::sync);
    }
}