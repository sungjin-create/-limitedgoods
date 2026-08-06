import http from 'k6/http';

import { BASE_URL, SEARCH_KEYWORD, jsonHeaders, randomInt } from './config.js';
import {
    createdOrders,
    queueSoldOut,
    recordResponse,
} from './metrics.js';

function params(endpoint, token) {
    return {
        headers: jsonHeaders(token),
        tags: { endpoint, name: endpoint },
    };
}

export function browseProducts() {
    const page = randomInt(0, 4);
    const response = http.get(
        `${BASE_URL}/api/product?page=${page}&size=10&sort=id,asc`,
        params('product_list'),
    );
    return recordResponse(response, 'product_list');
}

export function searchProducts(keyword = SEARCH_KEYWORD) {
    const response = http.get(
        `${BASE_URL}/api/product/search?keyword=${encodeURIComponent(keyword)}&page=0&size=10`,
        params('product_search'),
    );
    return recordResponse(response, 'product_search');
}

export function getMyOrders(token) {
    const response = http.get(
        `${BASE_URL}/api/user/order`,
        params('order_history', token),
    );
    return recordResponse(response, 'order_history');
}

export function getUserInfo(token) {
    const response = http.get(
        `${BASE_URL}/api/user/info`,
        params('user_info', token),
    );
    return recordResponse(response, 'user_info');
}

export function enterQueue(token, productId, expectedCodes = ['QUEUE_001']) {
    const response = http.post(
        `${BASE_URL}/api/user/queue/enter`,
        JSON.stringify({ productId }),
        params('queue_enter', token),
    );
    const result = recordResponse(response, 'queue_enter', expectedCodes);
    if (result.code === 'QUEUE_001') queueSoldOut.add(1);
    return result;
}

export function getQueueStatus(token, productId) {
    const response = http.get(
        `${BASE_URL}/api/user/queue/status?productId=${productId}`,
        params('queue_status', token),
    );
    const result = recordResponse(response, 'queue_status', ['QUEUE_001']);
    if (result.code === 'QUEUE_001') queueSoldOut.add(1);
    return result;
}

export function heartbeatQueue(token, productId) {
    const response = http.post(
        `${BASE_URL}/api/user/queue/heartbeat`,
        JSON.stringify({ productId }),
        params('queue_heartbeat', token),
    );
    return recordResponse(response, 'queue_heartbeat');
}

export function createOrder(token, productId, admissionToken = null, expectedCodes = []) {
    const response = http.post(
        `${BASE_URL}/api/user/order/create`,
        JSON.stringify({
            checkoutToken: `k6-${__VU}-${__ITER}-${Date.now()}`,
            admissionToken,
            items: [{ productId, quantity: 1 }],
        }),
        params('order_create', token),
    );
    const result = recordResponse(response, 'order_create', expectedCodes);
    if (result.success) createdOrders.add(1);
    return result;
}
