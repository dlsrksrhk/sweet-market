import http from 'k6/http';
import {check, group, sleep} from 'k6';
import {Counter, Rate} from 'k6/metrics';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const productId = __ENV.PRODUCT_ID || '1';
const catalogReads = new Counter('catalog_read_requests');
const catalogReadErrors = new Rate('catalog_read_errors');

export const options = {
    scenarios: {
        warmup: {
            executor: 'constant-vus',
            vus: 20,
            duration: '1m',
            exec: 'catalogRead',
        },
        measured_catalog_reads: {
            executor: 'constant-vus',
            startTime: '1m',
            vus: 100,
            duration: '5m',
            exec: 'catalogRead',
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<1000'],
        catalog_read_errors: ['rate<0.01'],
    },
};

export function catalogRead() {
    if (Math.random() < 0.7) {
        group('home_catalog', () => {
            const responses = http.batch([
                ['GET', `${baseUrl}/api/discovery/events`, null, {tags: {route: 'events'}}],
                ['GET', `${baseUrl}/api/discovery/popular-products`, null, {tags: {route: 'popularity'}}],
                ['GET', `${baseUrl}/api/catalog/products?size=20`, null, {tags: {route: 'catalog'}}],
            ]);
            record(responses);
        });
    } else {
        group('product_detail', () => {
            record([http.get(`${baseUrl}/api/products/${productId}`, {tags: {route: 'detail'}})]);
        });
    }

    sleep(1);
}

function record(responses) {
    for (const response of responses) {
        const success = check(response, {'catalog read returns 200': (result) => result.status === 200});
        catalogReads.add(1);
        catalogReadErrors.add(!success);
    }
}
