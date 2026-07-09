import { api } from '../../shared/api/http';
import { type PageResponse } from '../admin/adminOperationsApi';

export type RefundRequestStatus = 'REQUESTED' | 'APPROVED' | 'REJECTED';
export type RefundRequestStatusFilter = RefundRequestStatus | 'ALL';

export type RefundRequest = {
  id: number;
  orderId: number;
  productId: number;
  productTitle: string;
  buyerId: number;
  buyerNickname: string;
  sellerId: number;
  sellerNickname: string;
  reason: string;
  status: RefundRequestStatus;
  requestedAt: string;
  handledById: number | null;
  handledAt: string | null;
  rejectReason: string | null;
};

export type RefundRequestPage = PageResponse<RefundRequest>;

export type RefundRequestSearchInput = {
  status: RefundRequestStatusFilter;
  page: number;
  size: number;
};

function buildRefundRequestSearchParams(input: RefundRequestSearchInput) {
  const searchParams = new URLSearchParams();

  if (input.status !== 'ALL') {
    searchParams.set('status', input.status);
  }

  searchParams.set('page', String(input.page));
  searchParams.set('size', String(input.size));

  return searchParams.toString();
}

export function getSellerRefundRequests(input: RefundRequestSearchInput) {
  return api<RefundRequestPage>(`/api/seller/refund-requests?${buildRefundRequestSearchParams(input)}`);
}

export function getAdminRefundRequests(input: RefundRequestSearchInput) {
  return api<RefundRequestPage>(`/api/admin/refund-requests?${buildRefundRequestSearchParams(input)}`);
}

export function getMyRefundRequests(input: RefundRequestSearchInput) {
  return api<RefundRequestPage>(`/api/refund-requests/me?${buildRefundRequestSearchParams(input)}`);
}

export function approveSellerRefundRequest(refundRequestId: number) {
  return api<RefundRequest>(`/api/seller/refund-requests/${refundRequestId}/approve`, {
    method: 'POST',
  });
}

export function rejectSellerRefundRequest(refundRequestId: number, rejectReason: string) {
  return api<RefundRequest>(`/api/seller/refund-requests/${refundRequestId}/reject`, {
    method: 'POST',
    body: JSON.stringify({ rejectReason }),
  });
}

export function approveAdminRefundRequest(refundRequestId: number) {
  return api<RefundRequest>(`/api/admin/refund-requests/${refundRequestId}/approve`, {
    method: 'POST',
  });
}

export function rejectAdminRefundRequest(refundRequestId: number, rejectReason: string) {
  return api<RefundRequest>(`/api/admin/refund-requests/${refundRequestId}/reject`, {
    method: 'POST',
    body: JSON.stringify({ rejectReason }),
  });
}
