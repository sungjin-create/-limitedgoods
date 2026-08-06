import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import exec from 'k6/execution';
import { Counter, Trend } from 'k6/metrics';

import { loginAs } from './lib/auth.js';
import { idListEnv, numberEnv } from './lib/config.js';
import { createOrder, enterQueue, getQueueStatus, heartbeatQueue } from './lib/requests.js';

const MODE = (__ENV.MODE || 'queue').toLowerCase();
const AUTH_MODE = (__ENV.AUTH_MODE || 'prepared').toLowerCase();
const TOKEN_FILE = __ENV.TOKEN_FILE || './lib/access-tokens.json';
const HOT_PRODUCT_ID = idListEnv('HOT_PRODUCT_ID')[0];
const USER_COUNT = numberEnv('USER_COUNT', 1000);
const ARRIVAL_RATE = numberEnv('ARRIVAL_RATE', 100);
const ARRIVAL_DURATION = __ENV.ARRIVAL_DURATION || '10s';
const PRE_ALLOCATED_VUS = numberEnv('PRE_ALLOCATED_VUS', USER_COUNT);
const MAX_VUS = numberEnv('MAX_VUS', USER_COUNT);
const POLL_SECONDS = numberEnv('POLL_SECONDS', 3);
const MAX_POLLS = numberEnv('MAX_POLLS', 100);
const GRACEFUL_STOP = __ENV.GRACEFUL_STOP || '6m';

const journeyDuration = new Trend('comparison_journey_duration', true);
const createdJourneyDuration = new Trend('comparison_created_journey_duration', true);
const soldOutJourneyDuration = new Trend('comparison_sold_out_journey_duration', true);
const queueWaitDuration = new Trend('comparison_queue_wait_duration', true);
const completedJourneys = new Counter('comparison_completed_journeys');
const configurationErrors = new Counter('comparison_configuration_errors');
const skippedOverflowIterations = new Counter('comparison_skipped_overflow_iterations');
const queueEnterResults = new Counter('comparison_queue_enter_results');
const orderAttempts = new Counter('comparison_order_attempts');

if (!['prepared', 'login'].includes(AUTH_MODE)) {
    throw new Error(`AUTH_MODE must be prepared or login: ${AUTH_MODE}`);
}

const preparedTokens = AUTH_MODE === 'prepared'
    ? new SharedArray('queue comparison access tokens', () => {
        let parsed;

        try {
            parsed = JSON.parse(open(TOKEN_FILE));
        } catch (error) {
            throw new Error(`failed to read TOKEN_FILE=${TOKEN_FILE}: ${error.message}`);
        }

        if (!Array.isArray(parsed) || parsed.some((token) => typeof token !== 'string' || !token)) {
            throw new Error(`TOKEN_FILE must contain a JSON array of access tokens: ${TOKEN_FILE}`);
        }

        return parsed;
    })
    : [];

if (!['queue', 'direct', 'queue_enter_only'].includes(MODE)) {
    throw new Error(`MODE must be queue, direct, or queue_enter_only: ${MODE}`);
}

if (!HOT_PRODUCT_ID) {
    throw new Error('HOT_PRODUCT_ID is required. Example: -e HOT_PRODUCT_ID=16');
}

if (AUTH_MODE === 'prepared' && preparedTokens.length < USER_COUNT) {
    throw new Error(
        `TOKEN_FILE has ${preparedTokens.length} tokens but USER_COUNT=${USER_COUNT}: ${TOKEN_FILE}`,
    );
}

const thresholds = {
    unexpected_errors: ['rate<0.001'],
    checks: ['rate>0.99'],
    dropped_iterations: ['count==0'],
    comparison_configuration_errors: ['count==0'],
};

if (MODE === 'queue') {
    thresholds['http_req_duration{endpoint:order_create}'] = ['p(95)<1000'];
    thresholds['http_req_duration{endpoint:queue_enter}'] = ['p(95)<500'];
    thresholds['http_req_duration{endpoint:queue_status}'] = ['p(95)<300'];
} else if (MODE === 'direct') {
    thresholds['http_req_duration{endpoint:order_create}'] = ['p(95)<1000'];
} else if (MODE === 'queue_enter_only') {
    thresholds['http_req_duration{endpoint:queue_enter}'] = ['p(95)<500'];
}

export const options = {
    scenarios: {
        queue_comparison: {
            executor: 'constant-arrival-rate',
            rate: ARRIVAL_RATE,
            timeUnit: '1s',
            duration: ARRIVAL_DURATION,
            preAllocatedVUs: PRE_ALLOCATED_VUS,
            maxVUs: MAX_VUS,
            gracefulStop: GRACEFUL_STOP,
        },
    },
    thresholds,
    tags: {
        comparison_mode: MODE,
        auth_mode: AUTH_MODE,
    },
    summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

function finishJourney(startedAt, outcome) {
    const duration = Date.now() - startedAt;
    journeyDuration.add(duration, { mode: MODE, outcome });

    if (outcome === 'created') createdJourneyDuration.add(duration, { mode: MODE });
    if (outcome === 'sold_out') soldOutJourneyDuration.add(duration, { mode: MODE });

    completedJourneys.add(1, { mode: MODE, outcome });
}

function getAdmissionToken(token, startedAt) {
    const entered = enterQueue(token, HOT_PRODUCT_ID);

    if (entered.code === 'QUEUE_001') {
        finishJourney(startedAt, 'sold_out');
        return null;
    }

    check(entered, {
        'queue entry succeeds or product is sold out': () => entered.success,
    });

    if (!entered.success) {
        finishJourney(startedAt, 'unexpected_failure');
        return null;
    }

    let admissionToken = entered.body?.data?.admissionToken || null;

    for (let attempt = 1; !admissionToken && attempt <= MAX_POLLS; attempt += 1) {
        sleep(POLL_SECONDS);

        if (attempt % 5 === 0) heartbeatQueue(token, HOT_PRODUCT_ID);

        const status = getQueueStatus(token, HOT_PRODUCT_ID);
        if (status.code === 'QUEUE_001') {
            finishJourney(startedAt, 'sold_out');
            return null;
        }

        if (!status.success) {
            finishJourney(startedAt, 'unexpected_failure');
            return null;
        }

        admissionToken = status.body?.data?.admissionToken || null;
    }

    check(admissionToken, {
        'admission token is issued before timeout': (value) => !!value,
    });

    if (!admissionToken) {
        finishJourney(startedAt, 'admission_timeout');
        return null;
    }

    queueWaitDuration.add(Date.now() - startedAt, { mode: MODE });
    return admissionToken;
}

function runQueueEnterOnly(token, startedAt) {
    // 단독 진단에서는 품절도 성공으로 취급하지 않는다. OPEN 상품이어야 한다.
    const entered = enterQueue(token, HOT_PRODUCT_ID, []);
    const outcome = entered.success
        ? 'success'
        : entered.code || `http_${entered.status || 'unknown'}`;

    queueEnterResults.add(1, {
        outcome,
        status: String(entered.status || 0),
    });

    check(entered, {
        'queue entry succeeds': () => entered.success,
    });

    finishJourney(
        startedAt,
        entered.success ? 'queue_entered' : 'unexpected_failure',
    );
}

export default function () {
    const iterationIndex = exec.scenario.iterationInTest;

    // constant-arrival-rate는 시간 경계에서 목표치보다 한 번 더 iteration을 시작할 수 있다.
    // 준비된 사용자 수를 넘는 요청이 로그인 실패로 섞이지 않도록 정확히 USER_COUNT에서 자른다.
    if (iterationIndex >= USER_COUNT) {
        skippedOverflowIterations.add(1, { mode: MODE });
        return;
    }

    const userIndex = iterationIndex + 1;
    const token = AUTH_MODE === 'prepared'
        ? preparedTokens[iterationIndex]
        : loginAs(userIndex);
    if (!token) return;

    const startedAt = Date.now();

    if (MODE === 'queue_enter_only') {
        runQueueEnterOnly(token, startedAt);
        return;
    }

    const admissionToken = MODE === 'queue'
        ? getAdmissionToken(token, startedAt)
        : null;

    if (MODE === 'queue' && !admissionToken) return;

    orderAttempts.add(1, { mode: MODE });

    const order = createOrder(
        token,
        HOT_PRODUCT_ID,
        admissionToken,
        ['PRODUCT_002'],
    );

    if (MODE === 'direct' && order.code === 'QUEUE_002') {
        configurationErrors.add(1, { mode: MODE, reason: 'bypass_disabled' });
    }

    check(order, {
        'order succeeds or stock is exhausted': () =>
            order.success || order.code === 'PRODUCT_002',
    });

    if (order.success) {
        finishJourney(startedAt, 'created');
    } else if (order.code === 'PRODUCT_002') {
        finishJourney(startedAt, 'sold_out');
    } else {
        finishJourney(startedAt, 'unexpected_failure');
    }
}
