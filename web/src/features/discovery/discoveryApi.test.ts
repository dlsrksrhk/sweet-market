import { afterEach, describe, expect, it, vi } from 'vitest';
import { recordProductView, type EventDetail } from './discoveryApi';

afterEach(() => vi.unstubAllGlobals());

describe('recordProductView', () => {
  it('상품_상세_조회_기록_실패를_화면_오류로_전파하지_않는다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('network unavailable')));

    await expect(recordProductView(1)).resolves.toBeUndefined();
  });

  it('이벤트_상세_계약은_대상_상품_카드를_포함한다', () => {
    const event: EventDetail = {
      eventType: 'COUPON', id: 1, title: '플랫폼 쿠폰', label: null, storeId: null, storeName: null,
      representativeImageUrl: null, endsAt: '2026-07-16T12:00:00Z', products: [],
    };

    expect(event.products).toEqual([]);
  });
});
