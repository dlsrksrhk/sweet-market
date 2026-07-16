import { describe, expect, it, vi } from 'vitest';
import { recordProductView } from './discoveryApi';

describe('recordProductView', () => {
  it('상품_상세_조회_기록_실패를_화면_오류로_전파하지_않는다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('network unavailable')));

    await expect(recordProductView(1)).resolves.toBeUndefined();
  });
});
