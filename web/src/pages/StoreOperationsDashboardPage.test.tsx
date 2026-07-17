import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import type { OperableStore } from '../features/stores/storeOperationsApi';
import { CampaignTable, OperationsRouteLinks, ProjectionFreshness } from './StoreOperationsDashboardPage';

const ownerStore: OperableStore = {
  storeId: 1,
  publicName: '사업자 상점',
  type: 'BUSINESS',
  status: 'ACTIVE',
  role: 'OWNER',
};

describe('StoreOperationsDashboardPage roles', () => {
  it('활성_사업자상점_OWNER에게만_캠페인관리링크를_보인다', () => {
    const ownerHtml = renderToStaticMarkup(<MemoryRouter><OperationsRouteLinks store={ownerStore} /></MemoryRouter>);
    const managerHtml = renderToStaticMarkup(
      <MemoryRouter><OperationsRouteLinks store={{ ...ownerStore, role: 'MANAGER' }} /></MemoryRouter>,
    );

    expect(ownerHtml).toContain('프로모션 관리');
    expect(ownerHtml).toContain('쿠폰 관리');
    expect(managerHtml).not.toContain('프로모션 관리');
    expect(managerHtml).not.toContain('쿠폰 관리');
    expect(managerHtml).toContain('판매 내역');
    expect(managerHtml).toContain('재고·상품');
  });
});

describe('ProjectionFreshness', () => {
  it('추적전과_프로젝션지연을_서로_다른_상태로_설명한다', () => {
    const trackingHtml = renderToStaticMarkup(
      <ProjectionFreshness generatedAt="2026-07-17T00:05:10Z" projectionUpdatedAt={null} projectionLagSeconds={0} trackingStartedAt={null} />,
    );
    const delayedHtml = renderToStaticMarkup(
      <ProjectionFreshness generatedAt="2026-07-17T00:05:10Z" projectionUpdatedAt={null} projectionLagSeconds={0} trackingStartedAt="2026-05-01T00:00:00Z" />,
    );

    expect(trackingHtml).toContain('운영 지표 추적을 아직 시작하지 않았습니다');
    expect(delayedHtml).toContain('프로젝션 반영이 지연되고 있습니다');
  });
});

describe('dashboard drilldown tables', () => {
  it('헤더와_데이터셀의_접근성_role을_제공한다', () => {
    const html = renderToStaticMarkup(
      <MemoryRouter>
        <CampaignTable
          store={ownerStore}
          page={{
            content: [{
              id: 'campaign-1', latestBucketStart: '2026-07-17T00:00:00Z', campaignKind: 'COUPON', campaignId: 1,
              ownerType: 'STORE', ownerStoreId: 1, status: 'ACTIVE', claimSuccessCount: 1, claimFailureCount: 0,
              redemptionSuccessCount: 1, redemptionFailureCount: 0, orderSuccessCount: 1, purchaseFailureCount: 0,
              promotionDiscounts: { applied: 0, realized: 0, canceled: 0, refunded: 0 },
              couponDiscounts: { applied: 1000, realized: 1000, canceled: 0, refunded: 0 },
            }],
            totalElements: 1, totalPages: 1, size: 20, number: 0, first: true, last: true, empty: false,
          }}
        />
      </MemoryRouter>,
    );

    expect(html.match(/<th(?:\s|>)/g)).toHaveLength(7);
    expect(html.match(/<td/g)).toHaveLength(7);
  });
});
