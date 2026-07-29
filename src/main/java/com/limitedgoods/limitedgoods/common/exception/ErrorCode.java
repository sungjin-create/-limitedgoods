package com.limitedgoods.limitedgoods.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@AllArgsConstructor
@Getter
public enum ErrorCode {

    USER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "USER_001",
            "사용자를 찾을 수 없습니다."
    ),
    DUPLICATE_EMAIL(
            HttpStatus.CONFLICT,
            "USER_002",
            "이미 사용 중인 이메일입니다."
    ),

    INVALID_INPUT(
            HttpStatus.BAD_REQUEST,
            "COMMON_001",
            "잘못된 요청입니다."
    ),

    INVALID_PRODUCT_ID(
            HttpStatus.BAD_REQUEST,
            "PRODUCT_001",
                    "Id에 해당하는 상품이 없습니다."
    ),
    INSUFFICIENT_STOCK(
            HttpStatus.BAD_REQUEST,
            "PRODUCT_002",
            "상품의 재고보다 요청한 수량이 더 많습니다."
    ),
    INVALID_PRODUCT_TIME(
            HttpStatus.BAD_REQUEST,
            "PRODUCT_003",
            "유효하지 않은 상품 등록기간입니다."
    ),
    HAS_NO_SALE_TIME(
            HttpStatus.BAD_REQUEST,
            "PRODUCT_004",
            "상품등록 기간은 필수입니다."
    ),
    INVALID_PRODUCT_STATUS_TRANSITION(
            HttpStatus.BAD_REQUEST,
            "PRODUCT_005",
            "상품 상태변경 정책을 위반합니다."
    ),
    SALE_START_MUST_BE_FUTURE(
            HttpStatus.BAD_REQUEST,
            "PRODUCT_006",
            "SCHEDULED 상품 판매 시작은 반드시 현재보다 나중이어야합니다."
    ),
    SALE_ALREADY_ENDED(
            HttpStatus.BAD_REQUEST,
            "PRODUCT_007",
            "상품 판매 종료시각이 현재보다 이전입니다."
    ),
    INVALID_PRODUCT_STATUS_REGISTER(
            HttpStatus.BAD_REQUEST,
            "PRODUCT_008",
            "상품 상태 등록 정책을 위반합니다."
    ),
    STOCK_ADJUSTMENT_NOT_ALLOWED_STATUS(
            HttpStatus.BAD_REQUEST,
            "PRODUCT_009",
            "재고를 변경할 수 있는 상품상태가 아닙니다."
    ),
    INVALID_PRODUCT_SALE_STATUS(
            HttpStatus.BAD_REQUEST,
            "PRODUCT_0010",
            "상품을 구입할 수 없는 상태입니다."
    ),

    ORDER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "ORDER_001",
            "주문을 찾을 수 없습니다."
    ),
    INVALID_ORDER_STATUS(
            HttpStatus.BAD_REQUEST,
            "ORDER_002",
            "현재 주문 상태에서는 요청을 처리할 수 없습니다."
    ),
    ORDER_ALREADY_CANCELED(
            HttpStatus.CONFLICT,
            "ORDER_003",
            "이미 취소된 주문입니다."
    ),
    ORDER_CANCEL_NOT_ALLOWED(
            HttpStatus.BAD_REQUEST,
            "ORDER_004",
            "현재 주문 상태에서는 취소할 수 없습니다."
    ),
    ORDER_STARTING_PAYMENT(
            HttpStatus.BAD_REQUEST,
            "ORDER_005",
            "현재 결제 진행중인 주문이 있습니다."
    ),
    TOO_MANY_ORDER_REQUESTS(
            HttpStatus.TOO_MANY_REQUESTS,
            "ORDER_006",
            "주문 요청이 너무 많습니다. 잠시 후 다시 시도해주세요."
    ),
    LIMITED_PRODUCT_SINGLE_ORDER_ONLY(
            HttpStatus.TOO_MANY_REQUESTS,
            "ORDER_007",
            "한정판 상품은 단일 주문만 가능합니다."
    ),
    MAX_PURCHASE_QUANTITY_EXCEEDED(
            HttpStatus.TOO_MANY_REQUESTS,
            "ORDER_008",
            "한번에 주문 가능한 갯수를 초과했습니다."
    ),
    IDEMPOTENCY_KEY_REUSED(
            HttpStatus.CONFLICT,
            "ORDER_009",
            "동일한 checkoutToken이 다른 주문 요청에 사용되었습니다."
    ),
    DUPLICATE_ORDER_PRODUCT(
            HttpStatus.BAD_REQUEST,
            "ORDER_010",
            "동일한 상품을 주문 항목에 중복으로 포함할 수 없습니다."
    ),

    PAYMENT_FAILED(
            HttpStatus.BAD_REQUEST,
            "PAYMENT_001",
            "결제에 실패했습니다."
    ),
    RESERVATION_EXPIRED(
            HttpStatus.BAD_REQUEST,
            "PAYMENT_002",
            "결제 유효기간이 지났습니다."
    ),
    DUPLICATE_PAYMENT_REQUEST(
            HttpStatus.CONFLICT,
            "PAYMENT_003",
            "이미 처리 중이거나 처리된 결제 요청입니다."
    ),
    PAYMENT_CANCEL_FAILED(
            HttpStatus.BAD_REQUEST,
            "PAYMENT_004",
            "결제 취소에 실패했습니다."
    ),
    PAYMENT_FINALIZATION_FAILED(
            HttpStatus.SERVICE_UNAVAILABLE,
            "PAYMENT_005",
            "결제 승인은 완료됐지만 주문 확정이 지연되고 있습니다."
    ),
    PAYMENT_PROCESSING(
            HttpStatus.ACCEPTED,
            "PAYMENT_006",
            "결제 결과를 확인하고 있습니다."
    ),
    PAYMENT_RESULT_UNKNOWN(
            HttpStatus.ACCEPTED,
            "PAYMENT_007",
            "결제 결과를 확인하고 있습니다."
    ),
    PAYMENT_AMOUNT_MISMATCH(
            HttpStatus.CONFLICT,
            "PAYMENT_008",
            "승인 금액이 주문 금액과 일치하지 않습니다."
    ),
    PAYMENT_IDEMPOTENCY_KEY_REUSED(
            HttpStatus.CONFLICT,
            "PAYMENT_009",
            "동일한 결제 키가 다른 요청에 사용되었습니다."
    ),
    PAYMENT_ALREADY_DECLINED(
            HttpStatus.CONFLICT,
            "PAYMENT_010",
            "이미 실패 처리된 결제 요청입니다."
    ),
    PAYMENT_ATTEMPT_NOT_FOUND(
            HttpStatus.CONFLICT,
            "PAYMENT_011",
            "결제 시도 정보를 찾을 수 없습니다."
    ),
    INVALID_PAYMENT_IDEMPOTENCY_KEY(
            HttpStatus.BAD_REQUEST,
            "PAYMENT_012",
            "유효하지 않은 결제 멱등 키입니다."
    ),
    PAYMENT_REFUND_PROCESSING(
            HttpStatus.ACCEPTED,
            "PAYMENT_013",
            "환불을 처리하고 있습니다."
    ),

    PAYMENT_REFUND_RESULT_UNKNOWN(
            HttpStatus.ACCEPTED,
            "PAYMENT_014",
            "환불 결과를 확인하고 있습니다."
    ),

    CART_NOT_FOUND(
            HttpStatus.BAD_REQUEST,
            "CART_001",
            "CART를 찾을 수 없습니다."
    ),
    CART_ITEM_NOT_FOUND(
            HttpStatus.BAD_REQUEST,
            "CART_ITEM_001",
            "ITEM을 찾을 수 없습니다."
    ),
    CART_ITEM_ALREADY_ADD(
            HttpStatus.BAD_REQUEST,
            "CART_ITEM_002",
            "ITEM이 이미 장바구니에 담겨있습니다."
    ),

    HAS_NO_CHECKOUT_TOKEN(
            HttpStatus.BAD_REQUEST,
            "CHECKOUT_TOKEN_001",
            "CHECKOUT_TOKEN이 없습니다."
    ),

    QUEUE_SOLD_OUT(
            HttpStatus.BAD_REQUEST,
            "QUEUE_001",
            "품절된 상품은 대기열에 진입할 수 없습니다."
    ),
    ADMISSION_TOKEN_REQUIRED(
            HttpStatus.FORBIDDEN,
            "QUEUE_002",
            "입장 토큰이 필요합니다."
    ),
    ADMISSION_TOKEN_INVALID(
            HttpStatus.FORBIDDEN,
            "QUEUE_003",
            "유효하지 않거나 만료된 입장 토큰입니다."
    ),

    EMAIL_DELIVERY_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "EMAIL_001",
            "이메일 발송 정보를 찾을 수 없습니다."
    ),

    EMAIL_DELIVERY_NOT_DEAD(
            HttpStatus.CONFLICT,
            "EMAIL_002",
            "DEAD 상태의 이메일만 다시 처리할 수 있습니다."
    ),

    UNAUTHORIZED(
            HttpStatus.UNAUTHORIZED,
            "AUTH_001",
            "인증이 필요합니다."
    ),
    FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "AUTH_002",
            "접근 권한이 없습니다."
    ),

    VALIDATION_ERROR(
            HttpStatus.BAD_REQUEST,
            "COMMON_002",
            "요청 값이 올바르지 않습니다."
    ),
    INVALID_JSON(
            HttpStatus.BAD_REQUEST,
            "COMMON_003",
            "요청 본문 형식이 올바르지 않습니다."
    ),
    MISSING_REQUIRED_VALUE(
            HttpStatus.BAD_REQUEST,
            "COMMON_004",
            "필수 요청 값이 누락되었습니다."
    ),
    TYPE_MISMATCH(
            HttpStatus.BAD_REQUEST,
            "COMMON_005",
            "요청 값의 형식이 올바르지 않습니다."
    ),
    RESOURCE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "COMMON_006",
            "요청한 경로를 찾을 수 없습니다."
    ),

    REFUND_ATTEMPT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "REFUND_001",
            "환불 시도를 찾을 수 없습니다."
    ),
    REFUND_RECONCILE_NOT_ALLOWED(
            HttpStatus.CONFLICT,
            "REFUND_002",
            "현재 상태에서는 환불 결과를 재조회할 수 없습니다."
    ),
    REFUND_RETRY_NOT_ALLOWED(
            HttpStatus.CONFLICT,
            "REFUND_003",
            "확정적으로 실패한 환불만 다시 요청할 수 있습니다."
    ),
    ;



    private final HttpStatus status;
    private final String code;
    private final String message;
}
