import { check, sleep } from 'k6';
import exec from 'k6/execution';

import { loginAs } from './lib/auth.js';
import { idListEnv, numberEnv } from './lib/config.js';
import { createOrder, enterQueue, getQueueStatus, heartbeatQueue } from './lib/requests.js';

const HOT_PRODUCT_ID = idListEnv('HOT_PRODUCT_ID')[0];
const USER_COUNT = numberEnv('USER_COUNT', 1000);
const ORDER_VUS = numberEnv('ORDER_VUS', 200);
const POLL_SECONDS = numberEnv('POLL_SECONDS', 3);
const MAX_POLLS = numberEnv('MAX_POLLS', 100);

if (!HOT_PRODUCT_ID) {
    throw new Error('HOT_PRODUCT_ID is required. Example: -e HOT_PRODUCT_ID=16');
}

export const options = {
    scenarios: {
        hot_product: {
            executor: 'shared-iterations',
            vus: ORDER_VUS,
            iterations: USER_COUNT,
            maxDuration: '10m',
        },
    },
    thresholds: {
        unexpected_errors: ['rate<0.001'],
        checks: ['rate>0.99'],
        'http_req_duration{endpoint:queue_enter}': ['p(95)<500'],
        'http_req_duration{endpoint:queue_status}': ['p(95)<300'],
        'http_req_duration{endpoint:order_create}': ['p(95)<1000'],
    },
};

export default function () {
    const userIndex = exec.scenario.iterationInTest + 1;
    const token = loginAs(userIndex);
    if (!token) return;

    const entered = enterQueue(token, HOT_PRODUCT_ID);
    if (entered.code === 'QUEUE_001') return;

    check(entered, {
        'queue entry succeeds or product is sold out': () => entered.success,
    });
    if (!entered.success) return;

    let admissionToken = entered.body?.data?.admissionToken || null;

    for (let attempt = 1; !admissionToken && attempt <= MAX_POLLS; attempt += 1) {
        sleep(POLL_SECONDS);

        if (attempt % 5 === 0) heartbeatQueue(token, HOT_PRODUCT_ID);

        const status = getQueueStatus(token, HOT_PRODUCT_ID);
        if (status.code === 'QUEUE_001') return;
        if (!status.success) return;

        admissionToken = status.body?.data?.admissionToken || null;
    }

    check(admissionToken, {
        'admission token is issued before timeout': (value) => !!value,
    });
    if (!admissionToken) return;

    const order = createOrder(
        token,
        HOT_PRODUCT_ID,
        admissionToken,
        ['PRODUCT_002'],
    );

    check(order, {
        'order succeeds or stock is exhausted': () => order.success || order.code === 'PRODUCT_002',
    });
}
