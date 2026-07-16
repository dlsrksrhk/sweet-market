import { api } from '../../shared/api/http';
import type { CatalogProductCard } from '../catalog/catalogApi';

export type DiscoveryEventType = 'PROMOTION' | 'COUPON';

export type ActiveEvent = {
  eventType: DiscoveryEventType;
  eventId: number;
  title: string;
  label: string | null;
  storeId: number | null;
  storeName: string | null;
  representativeImageUrl: string | null;
  endsAt: string;
};

export type EventDetail = {
  eventType: DiscoveryEventType;
  id: number;
  title: string;
  label: string | null;
  storeId: number | null;
  storeName: string | null;
  representativeImageUrl: string | null;
  endsAt: string;
  products: CatalogProductCard[];
};

export const discoveryQueryKeys = {
  all: ['discovery'] as const,
  events: () => [...discoveryQueryKeys.all, 'events'] as const,
  popularProducts: () => [...discoveryQueryKeys.all, 'popular-products'] as const,
  event: (eventType: DiscoveryEventType, eventId: number) => [...discoveryQueryKeys.all, 'events', eventType, eventId] as const,
};

export function getActiveEvents() {
  return api<ActiveEvent[]>('/api/discovery/events');
}

export function getPopularProducts() {
  return api<CatalogProductCard[]>('/api/discovery/popular-products');
}

export function getEventDetail(eventType: DiscoveryEventType, eventId: number) {
  return api<EventDetail>(`/api/discovery/events/${eventType}/${eventId}`);
}

export async function recordProductView(productId: number) {
  try {
    await api<void>(`/api/products/${productId}/views`, {
      method: 'POST',
      credentials: 'include',
    });
  } catch {
    // View analytics must never block a product-detail screen.
  }
}
