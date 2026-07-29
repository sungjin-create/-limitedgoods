package com.limitedgoods.limitedgoods.order.purchase.service;

import com.limitedgoods.limitedgoods.common.exception.BusinessException;
import com.limitedgoods.limitedgoods.common.exception.ErrorCode;
import com.limitedgoods.limitedgoods.order.entity.OrderItem;
import com.limitedgoods.limitedgoods.order.purchase.repository.UserProductPurchaseCounterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserPurchaseLimitService {

    private final UserProductPurchaseCounterRepository repository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(Long userId, List<OrderItem> items) {
        for (OrderItem item : items) {
            Integer limit = item.getPurchaseLimitAtOrder();

            if (limit == null) {
                continue;
            }

            int updated = repository.tryReserve(
                    userId,
                    item.getProduct().getId(),
                    item.getQuantity(),
                    limit
            );

            if (updated != 1) {
                throw new BusinessException(
                        ErrorCode.MAX_PURCHASE_QUANTITY_EXCEEDED
                );
            }
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void confirmPayment(Long userId, List<OrderItem> items) {
        for (OrderItem item : items) {
            if (item.getPurchaseLimitAtOrder() == null) {
                continue;
            }

            int updated = repository.confirmPayment(
                    userId,
                    item.getProduct().getId(),
                    item.getQuantity()
            );

            requireSingleUpdate(updated);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseReservation(Long userId, List<OrderItem> items) {
        for (OrderItem item : items) {
            if (item.getPurchaseLimitAtOrder() == null) {
                continue;
            }

            int updated = repository.releaseReservation(
                    userId,
                    item.getProduct().getId(),
                    item.getQuantity()
            );

            requireSingleUpdate(updated);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void releasePaidQuantity(Long userId, List<OrderItem> items) {
        for (OrderItem item : items) {
            if (item.getPurchaseLimitAtOrder() == null) {
                continue;
            }

            int updated = repository.releasePaidQuantity(
                    userId,
                    item.getProduct().getId(),
                    item.getQuantity()
            );

            requireSingleUpdate(updated);
        }
    }

    private void requireSingleUpdate(int updated) {
        if (updated != 1) {
            throw new IllegalStateException(
                    "사용자별 구매 수량 카운터가 주문 상태와 일치하지 않습니다."
            );
        }
    }
}