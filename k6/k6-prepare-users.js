import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';

import {
    BASE_URL,
    USER_COUNT,
    credentials,
    jsonHeaders,
    numberEnv,
} from './lib/config.js';
import { recordResponse } from './lib/metrics.js';

const PREPARE_VUS = numberEnv('PREPARE_VUS', 20);

export const options = {
    scenarios: {
        prepare_users: {
            executor: 'shared-iterations',
            vus: PREPARE_VUS,
            iterations: USER_COUNT,
            maxDuration: '15m',
        },
    },
    thresholds: {
        unexpected_errors: ['rate<0.01'],
        checks: ['rate>0.99'],
    },
};

export default function () {
    const index = exec.scenario.iterationInTest + 1;
    const user = credentials(index);

    const signupResponse = http.post(
        `${BASE_URL}/api/user/signup`,
        JSON.stringify(user),
        {
            headers: jsonHeaders(),
            tags: { endpoint: 'signup', name: 'POST /api/user/signup' },
        },
    );
    const signup = recordResponse(signupResponse, 'signup', ['USER_002']);

    const loginResponse = http.post(
        `${BASE_URL}/api/user/login`,
        JSON.stringify({ email: user.email, password: user.password }),
        {
            headers: jsonHeaders(),
            tags: { endpoint: 'login', name: 'POST /api/user/login' },
        },
    );
    const login = recordResponse(loginResponse, 'login');

    check(loginResponse, {
        'user exists or was created': () => signup.success || signup.code === 'USER_002',
        'prepared user can log in': () => login.success && !!login.body?.data?.accessToken,
    });
}
