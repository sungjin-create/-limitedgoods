package com.limitedgoods.limitedgoods.queue.service;

import com.limitedgoods.limitedgoods.common.exception.BusinessException;
import com.limitedgoods.limitedgoods.common.exception.ErrorCode;
import com.limitedgoods.limitedgoods.product.infrastructure.redis.ProductRedisKeys;
import com.limitedgoods.limitedgoods.queue.dto.QueueAdmissionResult;
import com.limitedgoods.limitedgoods.queue.infrastructure.redis.QueueRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdmissionTokenService {

    private static final Duration TOKEN_TTL = Duration.ofSeconds(3000000);

    private final RedisTemplate<String, String> redisTemplate;

    private static final RedisScript<String> CLAIM_SCRIPT = RedisScript.of(
            """
            -- KEYS[1]: admission token 키
            --          예) limitedgoods:v1:queue:{10}:admission:token:{uuid}
            -- KEYS[2]: 상품 대기열 generation 키
            --
            -- ARGV[1]: userId
            -- ARGV[2]: productId
            -- ARGV[3]: claimId. 동일 주문 요청을 구분하는 값

            -- 현재 상품 대기열의 세대를 조회한다.
            -- 아직 generation 키가 없다면 최초 세대인 0으로 취급한다.
            local generation = redis.call('GET', KEYS[2]) or '0'

            -- 아직 주문에 사용되지 않은 토큰이 가져야 할 정확한 값이다.
            -- 형식: READY|generation|userId|productId
            local readyValue = 'READY|' .. generation .. '|' .. ARGV[1] .. '|' .. ARGV[2]

            -- 이 주문 요청이 토큰을 점유했을 때 저장할 값이다.
            -- 형식: PROCESSING|generation|userId|productId|claimId
            local processingValue = 'PROCESSING|' .. generation .. '|' .. ARGV[1] .. '|' .. ARGV[2] .. '|' .. ARGV[3]

            -- 클라이언트가 제출한 admission token의 현재 상태를 조회한다.
            local tokenValue = redis.call('GET', KEYS[1])

            -- 같은 claimId의 요청이 이미 토큰을 점유했다면 멱등 재요청이다.
            -- 상태를 다시 변경하지 않고 claim 당시 generation을 반환한다.
            if tokenValue == processingValue then
                return generation
            end

            -- 토큰이 없거나, 다른 사용자/상품/세대의 토큰이거나,
            -- 다른 요청이 이미 점유한 토큰이면 claim할 수 없다.
            if tokenValue ~= readyValue then
                return nil
            end

            -- 상태를 READY에서 PROCESSING으로 바꾸더라도 기존 만료 시간은 유지한다.
            -- PTTL은 남은 TTL을 밀리초 단위로 반환한다.
            local ttl = redis.call('PTTL', KEYS[1])

            -- 이미 만료됐거나 TTL이 없는 비정상 토큰은 사용하지 않는다.
            if ttl <= 0 then
                return nil
            end

            -- READY 토큰을 현재 주문 요청이 점유한 PROCESSING 상태로 변경한다.
            redis.call('SET', KEYS[1], processingValue, 'PX', ttl)

            -- 이후 complete/release에서 동일 세대의 토큰을 검증할 수 있도록
            -- claim 시점의 generation을 Java 코드로 반환한다.
            return generation
            """,
            String.class
    );

    private static final RedisScript<Long> COMPLETE_CONSUMPTION_SCRIPT = RedisScript.of(
            """
            -- KEYS[1]: admission token 키
            -- KEYS[2]: 사용자별 admission track 키
            --
            -- ARGV[1]: claim 당시 generation
            -- ARGV[2]: userId
            -- ARGV[3]: productId
            -- ARGV[4]: claimId
            -- ARGV[5]: admission token UUID

            -- 주문이 실제로 점유했던 PROCESSING 값을 재구성한다.
            local processingValue = 'PROCESSING|' .. ARGV[1] .. '|' .. ARGV[2] .. '|' .. ARGV[3] .. '|' .. ARGV[4]

            -- Redis에 저장된 실제 토큰 상태를 조회한다.
            local tokenValue = redis.call('GET', KEYS[1])

            -- 다른 요청의 토큰이거나 이미 처리된 토큰이면 삭제하지 않는다.
            if tokenValue ~= processingValue then
                return 0
            end

            -- 주문 생성이 성공했으므로 소비된 admission token을 삭제한다.
            redis.call('DEL', KEYS[1])

            -- track에는 현재 사용자에게 발급된 token UUID가 저장되어 있다.
            local trackedToken = redis.call('GET', KEYS[2])

            -- track이 지금 소비한 토큰을 가리킬 때만 함께 삭제한다.
            -- 다른 최신 토큰을 가리키고 있다면 건드리지 않는다.
            if trackedToken == ARGV[5] then
                redis.call('DEL', KEYS[2])
            end

            -- 토큰 소비와 track 정리가 정상적으로 완료되었다.
            return 1
            """,
            Long.class
    );

    private static final RedisScript<String> ENTER_QUEUE_AND_ISSUE_TOKEN_SCRIPT = RedisScript.of(
            """
            -- KEYS[1]: 상품 sold-out 키
            -- KEYS[2]: 상품 대기열 generation 키
            -- KEYS[3]: waiting ZSET. score=최초 진입 시각, member=userId
            -- KEYS[4]: 사용자별 admission track 키. value=token UUID
            -- KEYS[5]: 이번 요청에서 생성할 admission token 키
            -- KEYS[6]: activity ZSET. score=마지막 활동 시각, member=userId
            --
            -- ARGV[1]: 현재 시각 milliseconds
            -- ARGV[2]: userId
            -- ARGV[3]: 새 admission token UUID
            -- ARGV[4]: token TTL milliseconds
            -- ARGV[5]: 즉시 입장 가능한 active window 크기
            -- ARGV[6]: productId
            -- ARGV[7]: admission token Redis 키 prefix

            -- 품절 표시가 있으면 waiting 등록과 토큰 발급을 모두 거절한다.
            if redis.call('EXISTS', KEYS[1]) == 1 then
                return 'SOLD_OUT'
            end

            -- 현재 대기열 세대를 조회한다. 키가 없으면 최초 세대 0이다.
            local generation = redis.call('GET', KEYS[2]) or '0'

            -- 사용자를 waiting에 최초 한 번만 등록한다.
            -- NX를 사용하므로 재진입 요청이 최초 score와 순서를 바꾸지 않는다.
            redis.call('ZADD', KEYS[3], 'NX', ARGV[1], ARGV[2])

            -- 사용자의 마지막 활동 시간을 등록하거나 현재 시각으로 갱신한다.
            -- 예: key=queue:{10}:activity, score=현재 시각, member=userId
            redis.call('ZADD', KEYS[6], ARGV[1], ARGV[2])

            -- waiting 안에서 사용자의 0 기반 순위를 조회한다.
            local rank = redis.call('ZRANK', KEYS[3], ARGV[2])

            -- 방금 등록했는데도 순위가 없다면 비정상 상태다.
            if not rank then
                return 'ERROR'
            end

            local activeWindow = tonumber(ARGV[5])

            if rank >= activeWindow then
                local position = rank - activeWindow + 1
                return 'WAITING:' .. position
            end

            -- 현재 세대에서 이 사용자에게 발급할 READY 토큰 값이다.
            local readyValue = 'READY|' .. generation .. '|' .. ARGV[2] .. '|' .. ARGV[6]

            -- 동일 사용자/상품/세대의 PROCESSING 토큰인지 확인할 prefix다.
            local processingPrefix = 'PROCESSING|' .. generation .. '|' .. ARGV[2] .. '|' .. ARGV[6] .. '|'

            -- 이 사용자에게 이미 발급된 token UUID가 있는지 확인한다.
            local existingToken = redis.call('GET', KEYS[4])

            if existingToken then
                -- track에 저장된 UUID로 실제 token Redis 키를 조립한다.
                local existingTokenKey = ARGV[7] .. existingToken

                -- 기존 토큰이 아직 존재하고 어떤 상태인지 조회한다.
                local existingValue = redis.call('GET', existingTokenKey)

                -- 현재 generation의 READY 토큰이면 새로 발급하지 않고 재사용한다.
                if existingValue == readyValue then
                    return 'ADMITTED:' .. existingToken
                end

                -- 같은 generation에서 이미 주문 처리 중인 토큰도 중복 발급하지 않는다.
                if existingValue and
                    string.sub(existingValue, 1, string.len(processingPrefix)) == processingPrefix
                then
                    return 'ADMITTED:' .. existingToken
                end

                -- token 만료, 이전 generation 등으로 track이 오래됐다면 정리한다.
                redis.call('DEL', existingTokenKey)
                redis.call('DEL', KEYS[4])
            end

            -- 현재 generation의 새 READY 토큰을 TTL과 함께 생성한다.
            -- NX는 UUID 충돌 시 기존 키를 덮어쓰지 않도록 보호한다.
            local created = redis.call('SET', KEYS[5], readyValue, 'PX', ARGV[4], 'NX')

            -- UUID 키가 이미 있으면 Java에서 새 UUID로 재시도한다.
            if not created then
                return 'RETRY'
            end

            -- 사용자별 track에 방금 발급한 token UUID를 같은 TTL로 저장한다.
            redis.call('SET', KEYS[4], ARGV[3], 'PX', ARGV[4])

            -- Java 코드가 클라이언트 응답으로 변환할 token UUID를 반환한다.
            return 'ADMITTED:' .. ARGV[3]
            """,
            String.class
            );

    private static final RedisScript<String> GET_STATUS_AND_ISSUE_TOKEN_SCRIPT = RedisScript.of(
            """
            -- KEYS[1]: 상품 sold-out 키
            -- KEYS[2]: 상품 대기열 generation 키
            -- KEYS[3]: waiting ZSET. score=최초 진입 시각, member=userId
            -- KEYS[4]: 사용자별 admission track 키. value=token UUID
            -- KEYS[5]: 이번 요청에서 생성할 admission token 키
            -- KEYS[6]: activity ZSET. score=마지막 활동 시각, member=userId
            --
            -- ARGV[1]: 현재 시각 milliseconds
            -- ARGV[2]: userId
            -- ARGV[3]: 새 admission token UUID
            -- ARGV[4]: token TTL milliseconds
            -- ARGV[5]: 즉시 입장 가능한 active window 크기
            -- ARGV[6]: productId
            -- ARGV[7]: admission token Redis 키 prefix

            -- 상태 조회 시점에 품절됐다면 순번이나 토큰 대신 품절을 반환한다.
            if redis.call('EXISTS', KEYS[1]) == 1 then
                return 'SOLD_OUT'
            end

            -- 현재 상품 대기열의 generation을 조회한다.
            local generation = redis.call('GET', KEYS[2]) or '0'

            -- enter와 달리 상태 조회에서는 새로 waiting에 넣지 않고 기존 순위만 찾는다.
            local rank = redis.call('ZRANK', KEYS[3], ARGV[2])

            -- waiting에 없는 사용자는 상태를 조회하거나 토큰을 받을 수 없다.
            if not rank then
                return 'NOT_FOUND'
            end

            -- 정상 상태 조회를 heartbeat처럼 취급해 마지막 활동 시간을 갱신한다.
            redis.call('ZADD', KEYS[6], ARGV[1], ARGV[2])

            -- active window 값을 숫자로 변환한다.
            local activeWindow = tonumber(ARGV[5])

            -- 아직 active window 밖이면 현재 대기 순번을 반환한다.
            if rank >= activeWindow then
                local position = rank - activeWindow + 1

                return 'WAITING:' .. position
            end

            -- active window에 들어온 사용자가 가져야 할 READY 토큰 값이다.
            local readyValue ='READY|' .. generation .. '|' .. ARGV[2] .. '|' .. ARGV[6]

            -- 이미 PROCESSING 상태인지 식별하기 위한 prefix다.
            local processingPrefix ='PROCESSING|' .. generation .. '|' .. ARGV[2] .. '|' .. ARGV[6] .. '|'

            -- 기존에 발급된 token UUID가 있는지 사용자 track에서 확인한다.
            local existingToken = redis.call('GET', KEYS[4])

            if existingToken then
                -- 기존 UUID로 실제 token Redis 키를 만든다.
                local existingTokenKey = ARGV[7] .. existingToken

                -- 기존 토큰의 실제 상태를 조회한다.
                local existingValue = redis.call('GET', existingTokenKey)

                -- 현재 generation의 READY 토큰이면 기존 토큰을 반환한다.
                if existingValue == readyValue then
                    return 'ADMITTED:' .. existingToken
                end

                -- 같은 generation의 PROCESSING 토큰도 중복 발급하지 않는다.
                if existingValue
                   and string.sub(existingValue, 1, string.len(processingPrefix)) == processingPrefix then
                    return 'ADMITTED:' .. existingToken
                end

                -- 만료됐거나 이전 generation인 오래된 토큰과 track을 정리한다.
                redis.call('DEL', existingTokenKey)

                redis.call('DEL', KEYS[4])
            end

            -- 기존 유효 토큰이 없다면 새 READY 토큰을 생성한다.
            local tokenCreated = redis.call( 'SET', KEYS[5], readyValue, 'PX', ARGV[4], 'NX')

            -- UUID 충돌 시 Java에서 새 UUID를 만들어 다시 시도한다.
            if not tokenCreated then
                return 'RETRY'
            end

            -- 사용자 track에 새 token UUID를 토큰과 같은 TTL로 저장한다.
            redis.call('SET', KEYS[4], ARGV[3], 'PX', ARGV[4])

            -- 입장 가능한 사용자에게 발급된 token UUID를 반환한다.
            return 'ADMITTED:' .. ARGV[3]
            """,
            String.class
        );

    private static final RedisScript<Long> RELEASE_CLAIM_SCRIPT = RedisScript.of(
            """
             -- KEYS[1]: admission token 키
             -- KEYS[2]: 사용자별 admission track 키
             -- KEYS[3]: 상품 대기열의 현재 generation 키
             --
             -- ARGV[1]: claim 당시 generation
             -- ARGV[2]: userId
             -- ARGV[3]: productId
             -- ARGV[4]: claimId
             -- ARGV[5]: admission token UUID

             -- 실패한 주문 요청이 점유했던 PROCESSING 값을 재구성한다.
             local processingValue = 'PROCESSING|' .. ARGV[1] .. '|' .. ARGV[2] .. '|' .. ARGV[3] .. '|' .. ARGV[4]

             -- Redis에 저장된 실제 토큰 상태를 조회한다.
             local tokenValue = redis.call('GET', KEYS[1])

             -- 해당 요청이 점유한 토큰이 아니면 release하지 않는다.
             if tokenValue ~= processingValue then
                 return 0
             end

             -- 품절/판매 종료 등으로 대기열이 무효화됐는지 확인하기 위해
             -- 현재 상품 generation을 조회한다.
             local currentGeneration = redis.call('GET', KEYS[3]) or '0'

             -- 사용자 track이 어떤 token UUID를 가리키는지 조회한다.
             local trackedToken = redis.call('GET', KEYS[2])

             -- claim 이후 generation이 바뀌었다면 이전 세대 토큰이다.
             -- 이 토큰은 READY로 되돌리지 않고 완전히 삭제한다.
             if currentGeneration ~= ARGV[1] then
                 redis.call('DEL', KEYS[1])

                 -- track이 삭제 대상 토큰을 가리킬 때만 함께 삭제한다.
                 if trackedToken == ARGV[5] then
                     redis.call('DEL', KEYS[2])
                 end

                 -- 2는 이전 generation 토큰을 정상 삭제했다는 의미다.
                 return 2
             end

             -- 같은 generation이면 토큰을 다시 READY로 돌리기 위해
             -- 기존 토큰의 남은 TTL을 조회한다.
             local ttl = redis.call('PTTL', KEYS[1])

             -- 이미 만료됐거나 TTL이 없는 토큰은 복구하지 않는다.
             if ttl <= 0 then
                 return 0
             end

             -- 동일 세대에서 다시 사용할 수 있는 READY 값을 만든다.
             local readyValue = 'READY|' .. ARGV[1] .. '|' .. ARGV[2] .. '|' .. ARGV[3]

             -- 명확한 비즈니스 실패이므로 PROCESSING 점유를 풀고
             -- 남은 TTL을 유지한 채 READY 상태로 복구한다.
             redis.call('SET', KEYS[1], readyValue, 'PX', ttl)

             -- 1은 현재 generation에서 READY 복구가 성공했다는 의미다.
             return 1
             """,
            Long.class
    );

    public Long claim(String token, Long userId, Long productId, String claimId) {
        String result = redisTemplate.execute(
                CLAIM_SCRIPT,
                List.of(
                        QueueRedisKeys.admissionToken(productId, token),
                        QueueRedisKeys.generation(productId)
                ),
                userId.toString(),
                productId.toString(),
                claimId
        );

        return result == null ? null  : Long.valueOf(result);
    }

    public QueueAdmissionResult enterQueueAndIssueToken(Long userId, Long productId, int activeWindow) {
        for (int attempt = 0; attempt < 3; attempt++) {
            String uuid = UUID.randomUUID().toString();

            String result = redisTemplate.execute(
                    ENTER_QUEUE_AND_ISSUE_TOKEN_SCRIPT,
                    List.of(
                            ProductRedisKeys.soldOut(productId),
                            QueueRedisKeys.generation(productId),
                            QueueRedisKeys.waiting(productId),
                            QueueRedisKeys.admissionTrack(productId, userId),
                            QueueRedisKeys.admissionToken(productId, uuid),
                            QueueRedisKeys.activity(productId)
                    ),
                    String.valueOf(System.currentTimeMillis()),
                    userId.toString(),
                    uuid,
                    String.valueOf(TOKEN_TTL.toMillis()),
                    String.valueOf(activeWindow),
                    productId.toString(),
                    QueueRedisKeys.admissionTokenPrefix(productId)
            );

            if (result == null || "ERROR".equals(result)) {
                throw new IllegalStateException("대기열 진입 처리에 실패했습니다.");
            }

            if ("SOLD_OUT".equals(result)) {
                throw new BusinessException(ErrorCode.QUEUE_SOLD_OUT);
            }

            if (result.equals("RETRY")) {
                continue;
            }

            if (result.startsWith("ADMITTED:")) {
                registerActiveProductBestEffort(productId);
                String token = result.substring("ADMITTED:".length());
                return QueueAdmissionResult.admitted(token);
            }

            if (result.startsWith("WAITING:")) {
                registerActiveProductBestEffort(productId);
                int position = Integer.parseInt(result.substring("WAITING:".length()));
                return QueueAdmissionResult.waiting(position);
            }

            throw new IllegalStateException("알 수 없는 대기열 처리 결과입니다: " + result);
        }

        throw new IllegalStateException("입장 토큰 생성에 실패했습니다.");
    }

    public boolean completeConsumption(String token, Long userId, Long productId, String claimId, long claimGeneration) {
        Long result = redisTemplate.execute(
                COMPLETE_CONSUMPTION_SCRIPT,
                List.of(
                        QueueRedisKeys.admissionToken(productId, token),
                        QueueRedisKeys.admissionTrack(productId, userId)
                ),
                String.valueOf(claimGeneration),
                userId.toString(),
                productId.toString(),
                claimId,
                token
        );

        return Long.valueOf(1L).equals(result);
    }

    public boolean releaseClaim(String token, Long userId, Long productId, String claimId, long claimGeneration) {
        Long result = redisTemplate.execute(
                RELEASE_CLAIM_SCRIPT,
                List.of(
                        QueueRedisKeys.admissionToken(productId, token),
                        QueueRedisKeys.admissionTrack(productId, userId),
                        QueueRedisKeys.generation(productId)
                ),
                String.valueOf(claimGeneration),
                userId.toString(),
                productId.toString(),
                claimId,
                token
        );

        return Long.valueOf(1L).equals(result) || Long.valueOf(2L).equals(result);
    }

    public QueueAdmissionResult getStatusAndIssueTokenIfEligible(Long userId, Long productId, int activeWindow) {
        for (int attempt = 0; attempt < 3; attempt++) {
            String token = UUID.randomUUID().toString();

            String result = redisTemplate.execute(
                    GET_STATUS_AND_ISSUE_TOKEN_SCRIPT,
                    List.of(
                            ProductRedisKeys.soldOut(productId),
                            QueueRedisKeys.generation(productId),
                            QueueRedisKeys.waiting(productId),
                            QueueRedisKeys.admissionTrack(productId, userId),
                            QueueRedisKeys.admissionToken(productId, token),
                            QueueRedisKeys.activity(productId)
                    ),
                    String.valueOf(System.currentTimeMillis()),
                    userId.toString(),
                    token,
                    String.valueOf(TOKEN_TTL.toMillis()),
                    String.valueOf(activeWindow),
                    productId.toString(),
                    QueueRedisKeys.admissionTokenPrefix(productId)
            );

            switch (result) {
                case "SOLD_OUT" -> throw new BusinessException(ErrorCode.QUEUE_SOLD_OUT);
                case "NOT_FOUND" -> throw new BusinessException(ErrorCode.QUEUE_NOT_FOUND);
                case "RETRY" -> {
                    continue;
                }
            }

            if (result.startsWith("ADMITTED:")) {
                return QueueAdmissionResult.admitted(result.substring("ADMITTED:".length()));
            }

            if (result.startsWith("WAITING:")) {
                return QueueAdmissionResult.waiting(
                        Integer.parseInt(
                                result.substring("WAITING:".length())
                        )
                );
            }

            throw new IllegalStateException("알 수 없는 대기열 처리 결과: " + result);
        }

        throw new IllegalStateException("입장 토큰 생성에 실패했습니다.");
    }

    private void registerActiveProductBestEffort(Long productId) {
        try {
            redisTemplate.opsForSet().add(QueueRedisKeys.activeProducts(), productId.toString());
        } catch (Exception exception) {
            log.warn("event=active_product_registration_failed productId={}", productId, exception);
        }
    }

}
