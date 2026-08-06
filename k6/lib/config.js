export const BASE_URL = __ENV.BASE_URL || 'http://localhost:18080';
export const USER_COUNT = numberEnv('USER_COUNT', 1000);
export const USER_PREFIX = __ENV.USER_PREFIX || 'loadtest';
export const USER_PASSWORD = __ENV.USER_PASSWORD || 'Test1234!';
export const SEARCH_KEYWORD = __ENV.SEARCH_KEYWORD || 'loadtest';

export const NORMAL_PRODUCT_IDS = idListEnv('NORMAL_PRODUCT_IDS');
export const LIMITED_PRODUCT_IDS = idListEnv('LIMITED_PRODUCT_IDS');

export function numberEnv(name, fallback) {
    const raw = __ENV[name];
    if (raw === undefined || raw === '') return fallback;

    const value = Number(raw);
    if (!Number.isFinite(value) || value <= 0) {
        throw new Error(`${name} must be a positive number: ${raw}`);
    }
    return value;
}

export function idListEnv(name) {
    const raw = __ENV[name] || '';
    return raw
        .split(',')
        .map((value) => Number(value.trim()))
        .filter((value) => Number.isInteger(value) && value > 0);
}

export function requiredIdList(name, values) {
    if (!values.length) {
        throw new Error(`${name} is required. Example: -e ${name}=1,2,3`);
    }
    return values;
}

export function randomItem(values) {
    return values[Math.floor(Math.random() * values.length)];
}

export function randomInt(min, max) {
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

export function credentials(index) {
    return {
        name: `부하테스트사용자${index}`,
        email: `${USER_PREFIX}${index}@loadtest.local`,
        password: USER_PASSWORD,
        confirmPassword: USER_PASSWORD,
    };
}

export function jsonHeaders(token) {
    const headers = { 'Content-Type': 'application/json' };
    if (token) headers.Authorization = `Bearer ${token}`;
    return headers;
}
