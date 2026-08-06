import { check } from 'k6';

import { loginAs } from './lib/auth.js';
import {
    browseProducts,
    getMyOrders,
    getUserInfo,
    searchProducts,
} from './lib/requests.js';

export const options = {
    scenarios: {
        smoke: {
            executor: 'shared-iterations',
            vus: 1,
            iterations: 1,
            maxDuration: '1m',
        },
    },
    thresholds: {
        unexpected_errors: ['rate==0'],
        checks: ['rate==1'],
    },
};

export default function () {
    const list = browseProducts();
    const search = searchProducts();
    const token = loginAs(1);

    check(null, {
        'public product APIs respond': () => list.success && search.success,
        'prepared test user logs in': () => !!token,
    });

    if (!token) return;

    const info = getUserInfo(token);
    const orders = getMyOrders(token);

    check(null, {
        'authenticated APIs respond': () => info.success && orders.success,
    });
}
