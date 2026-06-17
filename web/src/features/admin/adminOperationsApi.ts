import { api } from '../../shared/api/http';

export type ProductStatus = 'ON_SALE' | 'RESERVED' | 'SOLD_OUT' | 'HIDDEN';
export type OrderStatus = 'CREATED' | 'PAID' | 'SHIPPING' | 'DELIVERED' | 'CONFIRMED' | 'CANCELED';
export type MemberRole = 'MEMBER' | 'ADMIN';

export type PageResponse<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
};

export type AdminProductSummary = {
  productId: number;
  sellerId: number;
  sellerNickname: string;
  title: string;
  price: number;
  status: ProductStatus;
  thumbnailUrl: string | null;
};

export type AdminProductDetail = AdminProductSummary & {
  description: string;
  imageUrls: string[];
};

export type AdminProductSearchInput = {
  sellerId?: number;
  status?: ProductStatus | '';
  keyword?: string;
  page: number;
  size: number;
};

export type AdminOrderSummary = {
  orderId: number;
  productId: number;
  productTitle: string;
  productPrice: number;
  buyerId: number;
  buyerNickname: string;
  sellerId: number;
  sellerNickname: string;
  status: OrderStatus;
  productStatus: ProductStatus;
  orderedAt: string;
};

export type AdminOrderDetail = AdminOrderSummary & {
  canceledAt: string | null;
  confirmedAt: string | null;
  settlementExists: boolean;
};

export type AdminOrderSearchInput = {
  buyerId?: number;
  sellerId?: number;
  status?: OrderStatus | '';
  productId?: number;
  page: number;
  size: number;
};

export type AdminMemberSummary = {
  memberId: number;
  email: string;
  nickname: string;
  role: MemberRole;
};

export type AdminMemberDetail = AdminMemberSummary & {
  productCount: number;
  orderCount: number;
};

export type AdminMemberSearchInput = {
  email?: string;
  nickname?: string;
  role?: MemberRole | '';
  page: number;
  size: number;
};

function appendOptionalParam(searchParams: URLSearchParams, key: string, value: string | number | undefined) {
  if (value !== undefined && value !== '') {
    searchParams.set(key, String(value));
  }
}

export function getAdminProducts(input: AdminProductSearchInput) {
  const searchParams = new URLSearchParams();
  appendOptionalParam(searchParams, 'sellerId', input.sellerId);
  appendOptionalParam(searchParams, 'status', input.status);
  appendOptionalParam(searchParams, 'keyword', input.keyword?.trim() || undefined);
  searchParams.set('page', String(input.page));
  searchParams.set('size', String(input.size));

  return api<PageResponse<AdminProductSummary>>(`/api/admin/products?${searchParams.toString()}`);
}

export function getAdminProductDetail(productId: number) {
  return api<AdminProductDetail>(`/api/admin/products/${productId}`);
}

export function hideAdminProduct(productId: number) {
  return api<AdminProductDetail>(`/api/admin/products/${productId}/hide`, {
    method: 'POST',
  });
}

export function getAdminOrders(input: AdminOrderSearchInput) {
  const searchParams = new URLSearchParams();
  appendOptionalParam(searchParams, 'buyerId', input.buyerId);
  appendOptionalParam(searchParams, 'sellerId', input.sellerId);
  appendOptionalParam(searchParams, 'status', input.status);
  appendOptionalParam(searchParams, 'productId', input.productId);
  searchParams.set('page', String(input.page));
  searchParams.set('size', String(input.size));

  return api<PageResponse<AdminOrderSummary>>(`/api/admin/orders?${searchParams.toString()}`);
}

export function getAdminOrderDetail(orderId: number) {
  return api<AdminOrderDetail>(`/api/admin/orders/${orderId}`);
}

export function getAdminMembers(input: AdminMemberSearchInput) {
  const searchParams = new URLSearchParams();
  appendOptionalParam(searchParams, 'email', input.email?.trim() || undefined);
  appendOptionalParam(searchParams, 'nickname', input.nickname?.trim() || undefined);
  appendOptionalParam(searchParams, 'role', input.role);
  searchParams.set('page', String(input.page));
  searchParams.set('size', String(input.size));

  return api<PageResponse<AdminMemberSummary>>(`/api/admin/members?${searchParams.toString()}`);
}

export function getAdminMemberDetail(memberId: number) {
  return api<AdminMemberDetail>(`/api/admin/members/${memberId}`);
}
