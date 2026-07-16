import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import { claimCouponCampaign, couponQueryKeys, getAvailableCouponCampaigns } from '../coupons/couponApi';
import { toProductImageSrc } from '../products/productApi';
import { ErrorState } from '../../shared/ui/ResourceStates';
import { discoveryQueryKeys, getEventDetail, type DiscoveryEventType } from './discoveryApi';

const dateTimeFormatter = new Intl.DateTimeFormat('ko-KR', { dateStyle: 'full', timeStyle: 'short' });

type EventDetailPanelProps = {
  eventType: DiscoveryEventType;
  eventId: number;
};

export function EventDetailPanel({ eventType, eventId }: EventDetailPanelProps) {
  const client = useQueryClient();
  const { loading: authLoading, member } = useAuth();
  const eventQuery = useQuery({ queryKey: discoveryQueryKeys.event(eventType, eventId), queryFn: () => getEventDetail(eventType, eventId) });
  const availableCouponsQuery = useQuery({
    queryKey: couponQueryKeys.available({ page: 0, size: 100 }),
    queryFn: () => getAvailableCouponCampaigns({ page: 0, size: 100 }),
    enabled: eventType === 'COUPON' && !authLoading && member !== null,
  });
  const claimMutation = useMutation({
    mutationFn: claimCouponCampaign,
    onSuccess: () => client.invalidateQueries({ queryKey: couponQueryKeys.all }),
  });

  if (eventQuery.isLoading) {
    return <div className="event-detail-panel event-detail-skeleton discovery-skeleton" aria-label="이벤트를 불러오는 중"><div /><span /><strong /><p /></div>;
  }

  if (eventQuery.error || !eventQuery.data) {
    return <ErrorState message="이벤트 정보를 불러오지 못했습니다." />;
  }

  const event = eventQuery.data;
  const imageSrc = toProductImageSrc(event.representativeImageUrl);
  const coupon = availableCouponsQuery.data?.content.find((campaign) => campaign.id === event.id);

  return (
    <article className="event-detail-panel">
      {imageSrc ? <img src={imageSrc} alt="" /> : <div className="event-detail-image-fallback">Sweet Market</div>}
      <div className="event-detail-copy">
        <p className="eyebrow">{event.eventType === 'PROMOTION' ? 'PROMOTION EVENT' : 'COUPON EVENT'}</p>
        <h1>{event.title}</h1>
        {event.label ? <p>{event.label}</p> : null}
        <p className="muted-text">{event.storeName} · {dateTimeFormatter.format(new Date(event.endsAt))} 종료</p>
        <div className="event-detail-actions">
          <Link className="primary-link" to={`/stores/${event.storeId}`}>이벤트 상품 보러가기</Link>
          {event.eventType === 'COUPON' ? <CouponClaimAction authLoading={authLoading} member={member} coupon={coupon} pending={claimMutation.isPending} onClaim={() => claimMutation.mutate(event.id)} /> : null}
        </div>
        {availableCouponsQuery.isError ? <p className="error-text">쿠폰 발급 상태를 불러오지 못했습니다.</p> : null}
        {claimMutation.isError ? <p className="error-text">쿠폰을 발급하지 못했습니다. 잠시 후 다시 시도해 주세요.</p> : null}
      </div>
    </article>
  );
}

type CouponClaimActionProps = {
  authLoading: boolean;
  member: ReturnType<typeof useAuth>['member'];
  coupon: Awaited<ReturnType<typeof getAvailableCouponCampaigns>>['content'][number] | undefined;
  pending: boolean;
  onClaim: () => void;
};

function CouponClaimAction({ authLoading, member, coupon, pending, onClaim }: CouponClaimActionProps) {
  if (authLoading) return null;
  if (!member) return <Link className="text-button" to="/login">로그인하고 쿠폰 받기</Link>;
  if (!coupon) return <p className="muted-text">현재 쿠폰 발급 상태를 확인할 수 없습니다.</p>;

  const disabled = coupon.claimed || coupon.soldOut || pending;
  const label = coupon.claimed ? '발급 완료' : coupon.soldOut ? '선착순 마감' : pending ? '발급 중' : '쿠폰 받기';
  return <button className="text-button" type="button" disabled={disabled} onClick={onClaim}>{label}</button>;
}
