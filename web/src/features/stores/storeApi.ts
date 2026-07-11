import { api } from '../../shared/api/http';

export type StoreType = 'PERSONAL' | 'BUSINESS';

export type StoreStatus = 'PENDING' | 'ACTIVE' | 'REJECTED' | 'SUSPENDED';

export type PrivateStore = {
  storeId: number;
  type: StoreType;
  publicName: string;
  introduction: string;
  status: StoreStatus;
  legalBusinessName: string | null;
  businessRegistrationId: string | null;
  rejectionReason: string | null;
};

export type PublicStore = Pick<PrivateStore, 'storeId' | 'type' | 'publicName' | 'introduction'>;

export type AdminBusinessStore = PrivateStore & {
  ownerMemberId: number;
  createdAt: string;
  updatedAt: string;
};

export type BusinessApplicationInput = {
  publicName: string;
  introduction: string;
  legalBusinessName: string;
  businessRegistrationId: string;
};

export type StoreProfileInput = {
  publicName: string;
  introduction: string;
  legalBusinessName?: string;
  businessRegistrationId?: string;
};

export type AdminBusinessStoreSearchInput = {
  status?: StoreStatus;
  page: number;
  size: number;
};

type Page<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
};

export const storeQueryKeys = {
  all: ['stores'] as const,
  me: () => [...storeQueryKeys.all, 'me'] as const,
  public: (storeId: number) => [...storeQueryKeys.all, 'public', storeId] as const,
  admin: () => [...storeQueryKeys.all, 'admin'] as const,
  adminList: (input: AdminBusinessStoreSearchInput) =>
    [...storeQueryKeys.admin(), 'list', input.status ?? null, input.page, input.size] as const,
  adminDetail: (storeId: number) => [...storeQueryKeys.admin(), 'detail', storeId] as const,
};

export function getMyStores() {
  return api<PrivateStore[]>('/api/stores/me');
}

export function getPublicStore(storeId: number) {
  return api<PublicStore>(`/api/stores/${storeId}`);
}

export function applyBusinessStore(input: BusinessApplicationInput) {
  return api<PrivateStore>('/api/stores/business-applications', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function resubmitBusinessStore(storeId: number, input: BusinessApplicationInput) {
  return api<PrivateStore>(`/api/stores/business-applications/${storeId}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  });
}

export function updateStoreProfile(storeId: number, input: StoreProfileInput) {
  return api<PrivateStore>(`/api/stores/${storeId}/profile`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  });
}

export function getAdminBusinessStores(input: AdminBusinessStoreSearchInput) {
  const searchParams = new URLSearchParams();

  if (input.status) {
    searchParams.set('status', input.status);
  }

  searchParams.set('page', String(input.page));
  searchParams.set('size', String(input.size));

  return api<Page<AdminBusinessStore>>(`/api/admin/business-stores?${searchParams.toString()}`);
}

export function getAdminBusinessStore(storeId: number) {
  return api<AdminBusinessStore>(`/api/admin/business-stores/${storeId}`);
}

export function approveBusinessStore(storeId: number) {
  return api<PrivateStore>(`/api/admin/business-stores/${storeId}/approve`, {
    method: 'POST',
  });
}

export function rejectBusinessStore(storeId: number, reason: string) {
  return api<PrivateStore>(`/api/admin/business-stores/${storeId}/reject`, {
    method: 'POST',
    body: JSON.stringify({ reason }),
  });
}

export function suspendBusinessStore(storeId: number) {
  return api<PrivateStore>(`/api/admin/business-stores/${storeId}/suspend`, {
    method: 'POST',
  });
}

export function reactivateBusinessStore(storeId: number) {
  return api<PrivateStore>(`/api/admin/business-stores/${storeId}/reactivate`, {
    method: 'POST',
  });
}
