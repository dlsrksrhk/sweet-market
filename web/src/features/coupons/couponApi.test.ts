import { afterEach, describe, expect, it, vi } from 'vitest';
import { getCouponCampaignClaimState } from './couponApi';

afterEach(() => vi.unstubAllGlobals());

describe('getCouponCampaignClaimState', () => {
  it('캠페인_ID로_단건_발급상태를_조회한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ data: { id: 37, claimed: false, soldOut: false } })));
    vi.stubGlobal('fetch', fetchMock);
    vi.stubGlobal('localStorage', { getItem: vi.fn().mockReturnValue(null) });

    await expect(getCouponCampaignClaimState(37)).resolves.toMatchObject({ id: 37, claimed: false });
    expect(fetchMock).toHaveBeenCalledWith('http://localhost:8080/api/coupon-campaigns/37/claim-state', expect.anything());
  });
});
