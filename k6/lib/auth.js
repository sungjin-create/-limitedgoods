import http from 'k6/http';
import { check } from 'k6';

import {
    BASE_URL,
    USER_COUNT,
    credentials,
    jsonHeaders,
} from './config.js';
import { recordResponse } from './metrics.js';

const TOKEN_REFRESH_AFTER_MS = 20 * 60 * 1000;

let cachedToken = null;
let cachedUserIndex = null;
let authenticatedAt = 0;

export function loginAs(index) {
    const user = credentials(index);
    const response = http.post(
        `${BASE_URL}/api/user/login`,
        JSON.stringify({ email: user.email, password: user.password }),
        {
            headers: jsonHeaders(),
            tags: { endpoint: 'login', name: 'POST /api/user/login' },
        },
    );
    const result = recordResponse(response, 'login');
    const token = result.body?.data?.accessToken;

    check(response, {
        'login succeeds': () => result.success && typeof token === 'string' && token.length > 0,
    });

    return token || null;
}

export function tokenForCurrentVu() {
    const userIndex = ((__VU - 1) % USER_COUNT) + 1;
    const expired = Date.now() - authenticatedAt >= TOKEN_REFRESH_AFTER_MS;

    if (!cachedToken || cachedUserIndex !== userIndex || expired) {
        cachedToken = loginAs(userIndex);
        cachedUserIndex = userIndex;
        authenticatedAt = Date.now();
    }

    return cachedToken;
}
