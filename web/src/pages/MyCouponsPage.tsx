import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRef, useState } from 'react';
import { claimCouponCampaign, couponQueryKeys, getAvailableCouponCampaigns, getMyCoupons, type MemberCouponStatus } from '../features/coupons/couponApi';
import { EmptyState, ErrorState, StatusBadge } from '../shared/ui/ResourceStates';
import { discountText, errorMessage, formatDate, money } from './CouponCampaignWorkspacePage';

const PAGE_SIZE = 20;
const filters: { value: MemberCouponStatus | ''; label: string }[] = [{ value: '', label: '전체' }, { value: 'ISSUED', label: '사용 가능' }, { value: 'USED', label: '사용 완료' }, { value: 'EXPIRED', label: '만료' }, { value: 'UNAVAILABLE', label: '사용 불가' }];
const reason: Record<string, string> = { SCHEDULED: '캠페인 시작 전입니다.', PAUSED: '캠페인이 일시 중지되었습니다.', ENDED: '캠페인이 종료되었습니다.' };

export function MyCouponsPage() {
  const client = useQueryClient(); const [walletStatus, setWalletStatus] = useState<MemberCouponStatus | ''>(''); const [issueMessage, setIssueMessage] = useState<string | null>(null);
  const pendingClaimCampaignIdsRef = useRef(new Set<number>()); const [pendingClaimCampaignIds, setPendingClaimCampaignIds] = useState<Set<number>>(new Set());
  const availableInput = { page: 0, size: PAGE_SIZE }; const walletInput = { page: 0, size: PAGE_SIZE, status: walletStatus || undefined };
  const availableQuery = useQuery({ queryKey: couponQueryKeys.available(availableInput), queryFn: () => getAvailableCouponCampaigns(availableInput) });
  const walletQuery = useQuery({ queryKey: couponQueryKeys.wallet(walletInput), queryFn: () => getMyCoupons(walletInput) });
  const claimMutation = useMutation({ mutationFn: claimCouponCampaign, onSuccess: async (coupon) => { setIssueMessage(`“${coupon.title}” 쿠폰이 발급되었습니다.`); await Promise.all([client.invalidateQueries({ queryKey: couponQueryKeys.all })]); }, onError: (error) => setIssueMessage(errorMessage(error, '쿠폰을 발급하지 못했습니다.')) });
  const claimCampaign = (campaignId: number) => {
    if (pendingClaimCampaignIdsRef.current.has(campaignId)) return;
    pendingClaimCampaignIdsRef.current.add(campaignId);
    setPendingClaimCampaignIds(new Set(pendingClaimCampaignIdsRef.current));
    setIssueMessage(null);
    claimMutation.mutate(campaignId, { onSettled: () => { pendingClaimCampaignIdsRef.current.delete(campaignId); setPendingClaimCampaignIds(new Set(pendingClaimCampaignIdsRef.current)); } });
  };
  const coupons = walletQuery.data?.content ?? [];
  return <main className="coupon-wallet"><section className="coupon-workspace-header"><div><p className="eyebrow">MY COUPONS</p><h1>내 쿠폰</h1><p>발급 가능한 쿠폰과 보유 쿠폰을 분리해서 확인할 수 있습니다.</p></div></section><section className="coupon-panel"><div className="coupon-panel-heading"><div><p className="eyebrow">AVAILABLE</p><h2>발급 가능한 쿠폰</h2></div></div>{issueMessage ? <p className={claimMutation.isError ? 'error-text' : 'status-text'} role="status">{issueMessage}</p> : null}{availableQuery.isLoading ? <p className="status-text">발급 가능한 쿠폰을 불러오고 있습니다.</p> : null}{availableQuery.error ? <ErrorState message={errorMessage(availableQuery.error, '발급 가능한 쿠폰을 불러오지 못했습니다.')} /> : null}{availableQuery.data?.content.length === 0 ? <EmptyState title="현재 발급 가능한 쿠폰이 없습니다" /> : null}<div className="coupon-list">{availableQuery.data?.content.map((campaign) => { const claimPending = pendingClaimCampaignIds.has(campaign.id); return <article className="coupon-card" key={campaign.id}><div><StatusBadge status={campaign.effectiveStatus} /><h3>{campaign.title}</h3><p>{discountText(campaign)} · 최소 {money(campaign.minimumPurchaseAmount)}원</p><span>{campaign.validityType === 'COMMON_EXPIRY' ? `공통 만료 ${formatDate(campaign.commonExpiresAt ?? '')}` : `발급 후 ${campaign.validityDays}일`}</span></div><div className="coupon-card-actions"><button className="text-button" type="button" disabled={campaign.claimed || claimPending} onClick={() => claimCampaign(campaign.id)}>{campaign.claimed ? '발급 완료' : claimPending ? '발급 중' : '쿠폰 받기'}</button></div></article>; })}</div></section><section className="coupon-panel"><div className="coupon-panel-heading"><div><p className="eyebrow">WALLET</p><h2>보유 쿠폰</h2></div><label className="coupon-filter">상태<select value={walletStatus} onChange={(event) => setWalletStatus(event.target.value as MemberCouponStatus | '')}>{filters.map((filter) => <option key={filter.value} value={filter.value}>{filter.label}</option>)}</select></label></div>{walletQuery.isLoading ? <p className="status-text">보유 쿠폰을 불러오고 있습니다.</p> : null}{walletQuery.error ? <ErrorState message={errorMessage(walletQuery.error, '보유 쿠폰을 불러오지 못했습니다.')} /> : null}{walletQuery.data && coupons.length === 0 ? <EmptyState title="해당 상태의 쿠폰이 없습니다" /> : null}<div className="coupon-list">{coupons.map((coupon) => <article className="coupon-card coupon-wallet-card" key={coupon.id}><div><StatusBadge status={coupon.status} /><h3>{coupon.title}</h3><p>{discountText(coupon)} · 최소 {money(coupon.minimumPurchaseAmount)}원</p><span>만료 {formatDate(coupon.validUntil)}</span>{coupon.status === 'UNAVAILABLE' ? <p className="coupon-unavailable-reason">{reason[coupon.unavailabilityReason ?? ''] ?? '현재 사용할 수 없는 쿠폰입니다.'}</p> : null}</div><div className="coupon-card-actions"><span>{coupon.stackable ? '중복 적용 가능' : '중복 적용 불가'}</span></div></article>)}</div></section></main>;
}
