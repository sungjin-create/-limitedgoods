import { Counter, Rate } from 'k6/metrics';

export const unexpectedErrors = new Rate('unexpected_errors');
export const businessRejections = new Counter('business_rejections');
export const createdOrders = new Counter('created_orders');
export const queueSoldOut = new Counter('queue_sold_out');

export function safeJson(response) {
    try {
        return response.json();
    } catch (_) {
        return null;
    }
}

export function recordResponse(response, endpoint, expectedBusinessCodes = []) {
    const body = safeJson(response);
    const code = body?.code;
    const httpSuccess = response.status >= 200 && response.status < 300;
    const apiSuccess = body === null || body.success !== false;
    const success = httpSuccess && apiSuccess;
    const expectedBusinessFailure = !success && expectedBusinessCodes.includes(code);

    unexpectedErrors.add(success || expectedBusinessFailure ? 0 : 1, { endpoint });

    if (expectedBusinessFailure) {
        businessRejections.add(1, { endpoint, code: code || 'unknown' });
    }

    return {
        body,
        code,
        status: response.status,
        success,
        expectedBusinessFailure,
    };
}
