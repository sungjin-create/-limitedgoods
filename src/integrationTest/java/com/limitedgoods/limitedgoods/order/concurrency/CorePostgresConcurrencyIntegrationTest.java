package com.limitedgoods.limitedgoods.order.concurrency;

import com.limitedgoods.limitedgoods.common.exception.BusinessException;
import com.limitedgoods.limitedgoods.common.exception.ErrorCode;
import com.limitedgoods.limitedgoods.order.application.create.OrderCreateTransactionService;
import com.limitedgoods.limitedgoods.order.application.create.OrderStockReservationService;
import com.limitedgoods.limitedgoods.order.application.create.idempotency.OrderRequestFingerprintGenerator;
import com.limitedgoods.limitedgoods.order.application.history.OrderStatusHistoryService;
import com.limitedgoods.limitedgoods.order.application.mapper.OrderResponseMapper;
import com.limitedgoods.limitedgoods.order.dto.request.OrderItemRequest;
import com.limitedgoods.limitedgoods.order.dto.response.OrderResponse;
import com.limitedgoods.limitedgoods.order.entity.Order;
import com.limitedgoods.limitedgoods.order.entity.OrderItem;
import com.limitedgoods.limitedgoods.order.entity.OrderStatus;
import com.limitedgoods.limitedgoods.order.repository.OrderItemRepository;
import com.limitedgoods.limitedgoods.order.repository.OrderRepository;
import com.limitedgoods.limitedgoods.order.purchase.service.UserPurchaseLimitService;
import com.limitedgoods.limitedgoods.order.purchase.repository.UserProductPurchaseCounterRepository;
import com.limitedgoods.limitedgoods.product.entity.Product;
import com.limitedgoods.limitedgoods.product.entity.ProductStatus;
import com.limitedgoods.limitedgoods.product.entity.ProductType;
import com.limitedgoods.limitedgoods.product.repository.ProductRepository;
import com.limitedgoods.limitedgoods.product.service.ProductSoldOutCacheService;
import com.limitedgoods.limitedgoods.queue.service.QueueAvailabilityRedisService;
import com.limitedgoods.limitedgoods.user.entity.User;
import com.limitedgoods.limitedgoods.user.entity.UserRole;
import com.limitedgoods.limitedgoods.user.entity.UserStatus;
import com.limitedgoods.limitedgoods.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest(properties = "spring.jpa.properties.hibernate.format_sql=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        OrderCreateTransactionService.class,
        OrderStockReservationService.class,
        OrderResponseMapper.class,
        OrderRequestFingerprintGenerator.class,
        UserPurchaseLimitService.class,
        UserProductPurchaseCounterRepository.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CorePostgresConcurrencyIntegrationTest {

    private static final long RESERVATION_SECONDS = 300L;

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @DynamicPropertySource
    static void registerPostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired OrderCreateTransactionService transactionService;
    @Autowired OrderRequestFingerprintGenerator fingerprintGenerator;
    @Autowired OrderRepository orderRepository;
    @Autowired OrderItemRepository orderItemRepository;
    @Autowired ProductRepository productRepository;
    @Autowired UserRepository userRepository;
    @Autowired UserPurchaseLimitService userPurchaseLimitService;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean ProductSoldOutCacheService soldOutCacheService;
    @MockitoBean OrderStatusHistoryService orderStatusHistoryService;
    @MockitoBean QueueAvailabilityRedisService queueAvailabilityRedisService;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM user_product_purchase_counter");
        orderItemRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("재고보다 많은 동시 주문에서도 성공 수는 재고를 넘지 않고 음수 재고가 생기지 않는다")
    void concurrentOrders_succeedOnlyUpToAvailableStock() throws Exception {
        Product product = saveProduct(10, 10_000);
        List<User> users = saveUsers(30, "stock");
        List<OrderItemRequest> items = items(item(product, 1));
        String fingerprint = fingerprintGenerator.generate(items);

        List<Attempt> attempts = runConcurrently(30, index -> create(
                users.get(index).getId(),
                items,
                "stock-" + index,
                fingerprint
        ));

        assertThat(successes(attempts)).hasSize(10);
        assertThat(failures(attempts, ErrorCode.INSUFFICIENT_STOCK)).hasSize(20);
        assertThat(unexpectedFailures(attempts)).isEmpty();
        assertThat(productRepository.findStockById(product.getId())).contains(0);
        assertThat(orderRepository.count()).isEqualTo(10);
        assertThat(orderItemRepository.count()).isEqualTo(10);
    }

    @Test
    @DisplayName("같은 사용자의 동일 checkoutToken 동시 재요청은 주문과 재고를 한 번만 생성한다")
    void sameCheckoutToken_isIdempotentUnderConcurrency() throws Exception {
        User user = saveUser("idempotent@test.com");
        Product product = saveProduct(20, 12_000);
        List<OrderItemRequest> items = items(item(product, 1));
        String fingerprint = fingerprintGenerator.generate(items);

        List<Attempt> attempts = runConcurrently(20, index -> create(
                user.getId(), items, "same-checkout", fingerprint
        ));

        assertThat(successes(attempts)).hasSize(20);
        assertThat(unexpectedFailures(attempts)).isEmpty();
        assertThat(successes(attempts))
                .extracting(attempt -> attempt.response().id())
                .containsOnly(successes(attempts).get(0).response().id());
        assertThat(orderRepository.count()).isOne();
        assertThat(orderItemRepository.count()).isOne();
        assertThat(productRepository.findStockById(product.getId())).contains(19);
    }

    @Test
    @DisplayName("같은 checkoutToken을 다른 주문 내용으로 동시에 재사용하면 최초 요청만 반영된다")
    void sameCheckoutTokenWithDifferentPayload_rejectsConflictingRequests() throws Exception {
        User user = saveUser("conflict@test.com");
        Product product = saveProduct(20, 7_000);
        List<OrderItemRequest> oneItem = items(item(product, 1));
        List<OrderItemRequest> twoItems = items(item(product, 2));

        List<Attempt> attempts = runConcurrently(20, index -> {
            List<OrderItemRequest> requestItems = index % 2 == 0 ? oneItem : twoItems;
            return create(
                    user.getId(),
                    requestItems,
                    "conflicting-checkout",
                    fingerprintGenerator.generate(requestItems)
            );
        });

        assertThat(successes(attempts)).hasSize(10);
        assertThat(failures(attempts, ErrorCode.IDEMPOTENCY_KEY_REUSED)).hasSize(10);
        assertThat(unexpectedFailures(attempts)).isEmpty();
        assertThat(orderRepository.count()).isOne();
        assertThat(orderItemRepository.count()).isOne();

        Order saved = orderRepository.findAll().get(0);
        int savedQuantity = orderItemRepository.findByOrderId(saved.getId()).get(0).getQuantity();
        assertThat(productRepository.findStockById(product.getId())).contains(20 - savedQuantity);
    }

    @Test
    @DisplayName("같은 사용자의 서로 다른 checkoutToken 동시 요청은 직전 예약을 복구하고 활성 주문 하나만 남긴다")
    void sameUserWithDifferentCheckoutTokens_leavesOneActiveReservation() throws Exception {
        User user = saveUser("replace@test.com");
        Product product = saveProduct(10, 5_000);
        List<OrderItemRequest> items = items(item(product, 1));
        String fingerprint = fingerprintGenerator.generate(items);

        List<Attempt> attempts = runConcurrently(10, index -> create(
                user.getId(), items, "replacement-" + index, fingerprint
        ));

        assertThat(successes(attempts)).hasSize(10);
        assertThat(unexpectedFailures(attempts)).isEmpty();
        assertThat(orderRepository.count()).isEqualTo(10);
        assertThat(orderRepository.findAll())
                .filteredOn(order -> order.getStatus() == OrderStatus.CREATED)
                .hasSize(1);
        assertThat(orderRepository.findAll())
                .filteredOn(order -> order.getStatus() == OrderStatus.EXPIRED)
                .hasSize(9);
        assertThat(productRepository.findStockById(product.getId())).contains(9);
    }

    @Test
    @DisplayName("상품 순서가 반대인 복수 상품 동시 주문도 데드락 없이 재고를 일관되게 차감한다")
    void reversedMultiProductOrders_completeWithoutDeadlock() throws Exception {
        Product first = saveProduct(6, 3_000);
        Product second = saveProduct(6, 4_000);
        List<User> users = saveUsers(12, "multi");
        List<OrderItemRequest> forward = items(item(first, 1), item(second, 1));
        List<OrderItemRequest> reverse = items(item(second, 1), item(first, 1));

        List<Attempt> attempts = runConcurrently(12, index -> {
            List<OrderItemRequest> requestItems = index % 2 == 0 ? forward : reverse;
            return create(
                    users.get(index).getId(),
                    requestItems,
                    "multi-" + index,
                    fingerprintGenerator.generate(requestItems)
            );
        });

        assertThat(successes(attempts)).hasSize(6);
        assertThat(failures(attempts, ErrorCode.INSUFFICIENT_STOCK)).hasSize(6);
        assertThat(unexpectedFailures(attempts)).isEmpty();
        assertThat(productRepository.findStockById(first.getId())).contains(0);
        assertThat(productRepository.findStockById(second.getId())).contains(0);
        assertThat(orderRepository.count()).isEqualTo(6);
        assertThat(orderItemRepository.count()).isEqualTo(12);
    }

    @Test
    @DisplayName("동시 구매 제한 예약은 PostgreSQL 원자 연산으로 최대 수량을 넘지 않는다")
    void concurrentPurchaseLimitReservations_neverExceedLimit() throws Exception {
        User user = saveUser("purchase-limit@test.com");
        Product product = saveProduct(20, 10_000);
        product.setMaxPurchaseQuantity(3);
        productRepository.saveAndFlush(product);

        OrderItem counterItem = OrderItem.builder()
                .product(product)
                .quantity(1)
                .price(product.getPrice())
                .lineTotalPrice(product.getPrice())
                .purchaseLimitAtOrder(3)
                .build();

        List<Attempt> attempts = runConcurrently(20, index -> {
            try {
                transactionTemplate.executeWithoutResult(status ->
                        userPurchaseLimitService.reserve(user.getId(), List.of(counterItem)));
                return Attempt.success(null);
            } catch (BusinessException exception) {
                return Attempt.businessFailure(exception.getErrorCode());
            } catch (Throwable throwable) {
                return Attempt.unexpectedFailure(throwable);
            }
        });

        assertThat(successes(attempts)).hasSize(3);
        assertThat(failures(attempts, ErrorCode.MAX_PURCHASE_QUANTITY_EXCEEDED)).hasSize(17);
        assertThat(unexpectedFailures(attempts)).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT reserved_quantity FROM user_product_purchase_counter WHERE user_id = ? AND product_id = ?",
                Long.class,
                user.getId(),
                product.getId()
        )).isEqualTo(3L);
    }

    private Attempt create(
            Long userId,
            List<OrderItemRequest> items,
            String checkoutToken,
            String fingerprint
    ) {
        try {
            return Attempt.success(transactionService.createOrder(
                    userId, items, RESERVATION_SECONDS, checkoutToken, fingerprint
            ));
        } catch (BusinessException exception) {
            return Attempt.businessFailure(exception.getErrorCode());
        } catch (Throwable throwable) {
            return Attempt.unexpectedFailure(throwable);
        }
    }

    private <T> List<T> runConcurrently(
            int threadCount,
            IndexedTask<T> task
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();

        try {
            for (int index = 0; index < threadCount; index++) {
                int taskIndex = index;
                Callable<T> callable = () -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("concurrency start latch timed out");
                    }
                    return task.run(taskIndex);
                };
                futures.add(executor.submit(callable));
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(20, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private List<Attempt> successes(List<Attempt> attempts) {
        return attempts.stream()
                .filter(attempt -> attempt.errorCode() == null && attempt.unexpected() == null)
                .toList();
    }

    private List<Attempt> failures(List<Attempt> attempts, ErrorCode errorCode) {
        return attempts.stream().filter(attempt -> attempt.errorCode() == errorCode).toList();
    }

    private List<Attempt> unexpectedFailures(List<Attempt> attempts) {
        return attempts.stream().filter(attempt -> attempt.unexpected() != null).toList();
    }

    private List<User> saveUsers(int count, String prefix) {
        List<User> users = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            users.add(saveUser(prefix + "-" + index + "-" + UUID.randomUUID() + "@test.com"));
        }
        return users;
    }

    private User saveUser(String email) {
        return userRepository.saveAndFlush(User.builder()
                .email(email)
                .password("encoded-password")
                .name("tester")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private Product saveProduct(int stock, int price) {
        Product product = new Product();
        product.setName("product-" + UUID.randomUUID());
        product.setDescription("concurrency test product");
        product.setPrice(price);
        product.setInitialStock(stock);
        product.setStock(stock);
        product.setSoldCount(0);
        product.setType(ProductType.NORMAL);
        product.setStatus(ProductStatus.ACTIVE);
        product.setUpdatedAt(LocalDateTime.now());
        return productRepository.saveAndFlush(product);
    }

    private OrderItemRequest item(Product product, int quantity) {
        return new OrderItemRequest(product.getId(), quantity);
    }

    private List<OrderItemRequest> items(OrderItemRequest... items) {
        return List.of(items);
    }

    private record Attempt(
            OrderResponse response,
            ErrorCode errorCode,
            Throwable unexpected
    ) {
        static Attempt success(OrderResponse response) {
            return new Attempt(response, null, null);
        }

        static Attempt businessFailure(ErrorCode errorCode) {
            return new Attempt(null, errorCode, null);
        }

        static Attempt unexpectedFailure(Throwable throwable) {
            return new Attempt(null, null, throwable);
        }
    }

    @FunctionalInterface
    private interface IndexedTask<T> {
        T run(int index);
    }
}
