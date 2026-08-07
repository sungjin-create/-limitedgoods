package com.limitedgoods.limitedgoods;

import com.limitedgoods.limitedgoods.analytics.entity.DailyOrderFunnelProjection;
import com.limitedgoods.limitedgoods.analytics.entity.DailySalesProjection;
import com.limitedgoods.limitedgoods.analytics.repository.DailyOrderFunnelProjectionRepository;
import com.limitedgoods.limitedgoods.analytics.repository.DailySalesProjectionRepository;
import com.limitedgoods.limitedgoods.cart.entity.Cart;
import com.limitedgoods.limitedgoods.cart.entity.CartItem;
import com.limitedgoods.limitedgoods.cart.repository.CartItemRepository;
import com.limitedgoods.limitedgoods.cart.repository.CartRepository;
import com.limitedgoods.limitedgoods.cart.service.CartService;
import com.limitedgoods.limitedgoods.common.exception.BusinessException;
import com.limitedgoods.limitedgoods.common.exception.ErrorCode;
import com.limitedgoods.limitedgoods.event.outbox.entity.OutboxEvent;
import com.limitedgoods.limitedgoods.event.outbox.entity.OutboxEventType;
import com.limitedgoods.limitedgoods.event.outbox.repository.OutboxEventRepository;
import com.limitedgoods.limitedgoods.order.application.cancel.CancelOrderUseCase;
import com.limitedgoods.limitedgoods.order.application.payment.PaymentCommandService;
import com.limitedgoods.limitedgoods.order.application.payment.dto.PaymentStartAction;
import com.limitedgoods.limitedgoods.order.application.payment.dto.PaymentStartResult;
import com.limitedgoods.limitedgoods.order.entity.Order;
import com.limitedgoods.limitedgoods.order.entity.OrderItem;
import com.limitedgoods.limitedgoods.order.entity.OrderStatus;
import com.limitedgoods.limitedgoods.order.repository.OrderItemRepository;
import com.limitedgoods.limitedgoods.order.repository.OrderRepository;
import com.limitedgoods.limitedgoods.payment.dto.PaymentRequest;
import com.limitedgoods.limitedgoods.payment.dto.PaymentResult;
import com.limitedgoods.limitedgoods.payment.entity.PaymentAttempt;
import com.limitedgoods.limitedgoods.payment.entity.PaymentAttemptStatus;
import com.limitedgoods.limitedgoods.payment.entity.RefundAttempt;
import com.limitedgoods.limitedgoods.payment.entity.RefundAttemptStatus;
import com.limitedgoods.limitedgoods.payment.repository.PaymentAttemptRepository;
import com.limitedgoods.limitedgoods.payment.repository.RefundAttemptRepository;
import com.limitedgoods.limitedgoods.payment.service.FakePaymentService;
import com.limitedgoods.limitedgoods.payment.service.RefundReconciliationService;
import com.limitedgoods.limitedgoods.product.entity.Product;
import com.limitedgoods.limitedgoods.product.entity.ProductStatus;
import com.limitedgoods.limitedgoods.product.entity.ProductType;
import com.limitedgoods.limitedgoods.product.repository.ProductRepository;
import com.limitedgoods.limitedgoods.queue.dto.QueueAdmissionResult;
import com.limitedgoods.limitedgoods.queue.infrastructure.redis.QueueRedisKeys;
import com.limitedgoods.limitedgoods.queue.service.AdmissionTokenService;
import com.limitedgoods.limitedgoods.queue.service.LeaveResult;
import com.limitedgoods.limitedgoods.queue.service.QueueMaintenanceService;
import com.limitedgoods.limitedgoods.user.entity.User;
import com.limitedgoods.limitedgoods.user.entity.UserRole;
import com.limitedgoods.limitedgoods.user.entity.UserStatus;
import com.limitedgoods.limitedgoods.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = {
        "app.mail.enabled=false",
        "app.cors.allowed-origins=http://localhost:5173",
        "monitoring.prometheus.base-url=http://localhost:9090",
        "management.server.port=0",
        "outbox.publish.delay=3600000",
        "queue.cleanup.delay-ms=3600000",
        "payment.refund.reconcile-delay-ms=3600000",
        "payment.finalize.delay-ms=3600000"
})
@AutoConfigureMockMvc
class PostgresRedisIntegrationTest {

    private static final String JWT_SECRET_BASE64 =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379)
                    .withCommand("redis-server", "--notify-keyspace-events", "KEA");

    @DynamicPropertySource
    static void registerContainerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("jwt.secret-base64", () -> JWT_SECRET_BASE64);
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired UserRepository userRepository;
    @Autowired ProductRepository productRepository;
    @Autowired CartRepository cartRepository;
    @Autowired CartItemRepository cartItemRepository;
    @Autowired CartService cartService;
    @Autowired OrderRepository orderRepository;
    @Autowired OrderItemRepository orderItemRepository;
    @Autowired PaymentAttemptRepository paymentAttemptRepository;
    @Autowired RefundAttemptRepository refundAttemptRepository;
    @Autowired PaymentCommandService paymentCommandService;
    @Autowired CancelOrderUseCase cancelOrderUseCase;
    @Autowired RefundReconciliationService refundReconciliationService;
    @Autowired FakePaymentService fakePaymentService;
    @Autowired OutboxEventRepository outboxEventRepository;
    @Autowired DailySalesProjectionRepository dailySalesRepository;
    @Autowired DailyOrderFunnelProjectionRepository dailyFunnelRepository;
    @Autowired RedisTemplate<String, String> redisTemplate;
    @Autowired AdmissionTokenService admissionTokenService;
    @Autowired QueueMaintenanceService queueMaintenanceService;

    @BeforeEach
    void cleanState() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    users,
                    product,
                    outbox_event,
                    daily_sales_projection,
                    daily_order_funnel_projection
                RESTART IDENTITY CASCADE
                """);
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    @Test
    void flywayAppliesEveryMigrationToRealPostgres() {
        List<String> versions = jdbcTemplate.queryForList(
                """
                SELECT version
                  FROM flyway_schema_history
                 WHERE success = true
                 ORDER BY installed_rank
                """,
                String.class
        );

        assertThat(versions).contains("1", "2", "3", "4", "5", "6", "7");
        assertThat(columnExists("daily_order_funnel_projection", "refunded_order_count")).isTrue();
        assertThat(columnExists("daily_order_funnel_projection", "payment_failure_attempt_count")).isTrue();
        assertThat(columnExists("daily_order_funnel_projection", "canceled_order_count")).isFalse();
    }

    @Test
    void unauthenticatedCartAndOrderRequestsReturnTheSame401Contract() throws Exception {
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_001"));

        mockMvc.perform(get("/api/user/order"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_001"));
    }

    @Test
    void anotherUserCannotUpdateOrDeleteTheOwnersCartItem() {
        User owner = saveUser("owner@example.com");
        User attacker = saveUser("attacker@example.com");
        Product product = saveProduct();
        LocalDateTime now = LocalDateTime.now();

        Cart cart = cartRepository.saveAndFlush(Cart.builder()
                .user(owner)
                .createdAt(now)
                .updatedAt(now)
                .build());
        cartRepository.saveAndFlush(Cart.builder()
                .user(attacker)
                .createdAt(now)
                .updatedAt(now)
                .build());
        CartItem item = cartItemRepository.saveAndFlush(CartItem.builder()
                .cart(cart)
                .product(product)
                .quantity(1)
                .price(product.getPrice())
                .totalPrice(product.getPrice())
                .createdAt(now)
                .updatedAt(now)
                .build());

        assertThatThrownBy(() -> cartService.updateCartItem(attacker.getId(), item.getId(), 2))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND));
        assertThatThrownBy(() -> cartService.deleteCartItem(item.getId(), attacker.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND));

        assertThat(cartItemRepository.findById(item.getId())).isPresent();
        assertThat(cartItemRepository.findById(item.getId()).orElseThrow().getQuantity()).isEqualTo(1);
    }

    @Test
    void twoOutboxWorkersClaimDisjointRowsWithSkipLocked() throws Exception {
        for (int index = 0; index < 6; index++) {
            outboxEventRepository.save(OutboxEvent.create(
                    OutboxEventType.ORDER_CREATED,
                    "Order",
                    (long) index + 1,
                    "{}"
            ));
        }
        outboxEventRepository.flush();

        CountDownLatch firstWorkerLocked = new CountDownLatch(1);
        CountDownLatch secondWorkerFinished = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<List<Long>> first = executor.submit(() -> transactionTemplate.execute(status -> {
                List<OutboxEvent> events = claimRows(3);
                firstWorkerLocked.countDown();
                await(secondWorkerFinished);
                return events.stream().map(OutboxEvent::getId).toList();
            }));

            Future<List<Long>> second = executor.submit(() -> {
                assertThat(firstWorkerLocked.await(10, TimeUnit.SECONDS)).isTrue();
                try {
                    return transactionTemplate.execute(status ->
                            claimRows(3).stream().map(OutboxEvent::getId).toList());
                } finally {
                    secondWorkerFinished.countDown();
                }
            });

            List<Long> firstIds = first.get(15, TimeUnit.SECONDS);
            List<Long> secondIds = second.get(15, TimeUnit.SECONDS);

            assertThat(firstIds).hasSize(3);
            assertThat(secondIds).hasSize(3);
            assertThat(firstIds).doesNotContainAnyElementsOf(secondIds);
            Set<Long> allClaimedIds = new HashSet<>(firstIds);
            allClaimedIds.addAll(secondIds);
            assertThat(allClaimedIds).hasSize(6);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void paymentTimeoutIsReconciledWithTheSameIdempotencyKeyAndAttempt() {
        User user = saveUser("payment@example.com");
        Order order = saveOrder(user, "checkout-payment");
        String idempotencyKey = "payment-key-0001";
        String fingerprint = "a".repeat(64);

        PaymentStartResult first = paymentCommandService.preparePayment(
                user.getId(),
                order.getId(),
                idempotencyKey,
                fingerprint
        );
        paymentCommandService.recordUnknown(first.paymentAttemptId(), "PG timeout");

        PaymentStartResult retry = paymentCommandService.preparePayment(
                user.getId(),
                order.getId(),
                idempotencyKey,
                fingerprint
        );

        assertThat(first.action()).isEqualTo(PaymentStartAction.REQUEST_PG);
        assertThat(retry.action()).isEqualTo(PaymentStartAction.RECONCILE_PG);
        assertThat(retry.paymentAttemptId()).isEqualTo(first.paymentAttemptId());
        assertThat(retry.idempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(paymentAttemptRepository.count()).isEqualTo(1);
        assertThat(paymentAttemptRepository.findById(first.paymentAttemptId()).orElseThrow().getStatus())
                .isEqualTo(PaymentAttemptStatus.UNKNOWN);
    }

    @Test
    void unknownRefundIsRecoveredByReconciliationWithoutCreatingAnotherAttempt() {
        User user = saveUser("refund@example.com");
        Product product = saveProduct();
        Order order = saveOrder(user, "checkout-refund");
        String paymentKey = "payment-key-refund";
        String fingerprint = "b".repeat(64);

        PaymentStartResult paymentStart = paymentCommandService.preparePayment(
                user.getId(), order.getId(), paymentKey, fingerprint);
        PaymentResult paymentResult = fakePaymentService.pay(
                order.getId(), order.getTotalPrice(), paymentKey, new PaymentRequest());
        paymentCommandService.recordApproval(
                user.getId(), order.getId(), paymentStart.paymentAttemptId(), paymentResult);

        transactionTemplate.executeWithoutResult(status -> {
            Order approvedOrder = orderRepository.findById(order.getId()).orElseThrow();
            approvedOrder.markPaid();
            orderItemRepository.save(OrderItem.builder()
                    .order(approvedOrder)
                    .product(product)
                    .quantity(1)
                    .price(product.getPrice())
                    .lineTotalPrice(product.getPrice())
                    .purchaseLimitAtOrder(product.getMaxPurchaseQuantity())
                    .build());
        });

        fakePaymentService.setRefundScenario(
                paymentResult.transactionId(),
                FakePaymentService.FakeRefundScenario.PROCESSING
        );

        assertThatThrownBy(() -> cancelOrderUseCase.execute(user.getId(), order.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.PAYMENT_REFUND_RESULT_UNKNOWN));

        RefundAttempt unknownAttempt = refundAttemptRepository
                .findTopByOrder_IdOrderByIdDesc(order.getId())
                .orElseThrow();
        assertThat(unknownAttempt.getStatus()).isEqualTo(RefundAttemptStatus.UNKNOWN);

        fakePaymentService.approvePendingRefund(
                paymentResult.transactionId(),
                unknownAttempt.getIdempotencyKey()
        );
        refundReconciliationService.reconcile(unknownAttempt.getId());

        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.REFUNDED);
        assertThat(refundAttemptRepository.findById(unknownAttempt.getId()).orElseThrow().getStatus())
                .isEqualTo(RefundAttemptStatus.APPROVED);
        assertThat(refundAttemptRepository.count()).isEqualTo(1);
    }

    @Test
    void nativeProjectionUpsertsAccumulateRefundsAndFailureAttempts() {
        LocalDate date = LocalDate.of(2026, 7, 29);

        transactionTemplate.executeWithoutResult(status -> {
            dailySalesRepository.addRefund(date, 15_000, 2);
            dailySalesRepository.addRefund(date, 5_000, 1);
            dailyFunnelRepository.increment(date, 1, 0, 2, 0, 1);
            dailyFunnelRepository.increment(date, 0, 0, 1, 0, 0);
        });

        DailySalesProjection sales = dailySalesRepository
                .findAllBySalesDateBetweenOrderBySalesDateAsc(date, date)
                .get(0);
        DailyOrderFunnelProjection funnel = dailyFunnelRepository
                .findAllByOrderDateBetweenOrderByOrderDateAsc(date, date)
                .get(0);

        assertThat(sales.getRefundedOrderCount()).isEqualTo(2);
        assertThat(sales.getRefundAmount()).isEqualTo(20_000);
        assertThat(sales.getRefundedQuantity()).isEqualTo(3);
        assertThat(funnel.getRefundedOrderCount()).isEqualTo(1);
        assertThat(funnel.getPaymentFailureAttemptCount()).isEqualTo(3);
    }

    @Test
    void queueLuaSupportsLeaveHeartbeatAndStaleUserCorrection() {
        Long productId = 501L;
        Long leavingUserId = 11L;
        Long staleUserId = 12L;

        QueueAdmissionResult leaving = admissionTokenService
                .enterQueueAndIssueToken(leavingUserId, productId, 0);
        assertThat(leaving.admitted()).isFalse();
        queueMaintenanceService.registerActiveProduct(productId);
        assertThat(queueMaintenanceService.heartbeat(leavingUserId, productId)).isTrue();
        assertThat(queueMaintenanceService.leave(leavingUserId, productId))
                .isEqualTo(LeaveResult.REMOVED);

        QueueAdmissionResult stale = admissionTokenService
                .enterQueueAndIssueToken(staleUserId, productId, 0);
        assertThat(stale.admitted()).isFalse();
        queueMaintenanceService.registerActiveProduct(productId);
        redisTemplate.opsForZSet().add(
                QueueRedisKeys.activity(productId),
                staleUserId.toString(),
                System.currentTimeMillis() - Duration.ofMinutes(1).toMillis()
        );

        assertThat(queueMaintenanceService.removeStaleUsers(productId)).isEqualTo(1);
        assertThat(redisTemplate.opsForZSet().size(QueueRedisKeys.waiting(productId))).isZero();
        assertThat(queueMaintenanceService.findActiveProductIds())
                .doesNotContain(productId.toString());
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                  FROM information_schema.columns
                 WHERE table_schema = 'public'
                   AND table_name = ?
                   AND column_name = ?
                """,
                Integer.class,
                tableName,
                columnName
        );
        return count != null && count == 1;
    }

    private List<OutboxEvent> claimRows(int batchSize) {
        LocalDateTime now = LocalDateTime.now().plusSeconds(1);
        List<OutboxEvent> events = outboxEventRepository.lockClaimableEvents(
                now,
                now.minusMinutes(10),
                5,
                batchSize
        );
        events.forEach(event -> event.markProcessing(now));
        return events;
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for the other outbox worker");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while coordinating outbox workers", exception);
        }
    }

    private User saveUser(String email) {
        return userRepository.saveAndFlush(User.builder()
                .email(email)
                .password("encoded-password")
                .name(email)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .tokenVersion(0)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private Product saveProduct() {
        Product product = new Product();
        product.setName("integration product");
        product.setDescription("PostgreSQL integration test product");
        product.setPrice(10_000);
        product.setInitialStock(10);
        product.setStock(10);
        product.setSoldCount(0);
        product.setMaxPurchaseQuantity(2);
        product.setType(ProductType.NORMAL);
        product.setStatus(ProductStatus.ACTIVE);
        product.setUpdatedAt(LocalDateTime.now());
        return productRepository.saveAndFlush(product);
    }

    private Order saveOrder(User user, String checkoutToken) {
        return orderRepository.saveAndFlush(Order.create(
                user,
                10_000,
                LocalDateTime.now().plusMinutes(10),
                checkoutToken,
                "c".repeat(64)
        ));
    }
}
