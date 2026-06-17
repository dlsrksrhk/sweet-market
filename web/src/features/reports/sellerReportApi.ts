import { api } from '../../shared/api/http';

export type ProductStatus = 'ON_SALE' | 'RESERVED' | 'SOLD_OUT' | 'HIDDEN';
export type OrderStatus = 'CREATED' | 'PAID' | 'SHIPPING' | 'DELIVERED' | 'CONFIRMED' | 'CANCELED';

export type SellerReportPeriod = {
  recentDays: number;
  recentFrom: string;
  recentTo: string;
};

export type SellerReportTotalSummary = {
  activeProductCount: number;
  soldOutProductCount: number;
  confirmedOrderCount: number;
  completedSettlementAmount: number;
  unsettledConfirmedAmount: number;
};

export type SellerReportRecentSummary = {
  orderedCount: number;
  confirmedOrderCount: number;
  completedSettlementAmount: number;
  unsettledConfirmedAmount: number;
};

export type SellerReportSummary = {
  total: SellerReportTotalSummary;
  recent30Days: SellerReportRecentSummary;
};

export type SellerStatusCount = {
  status: ProductStatus | OrderStatus;
  count: number;
};

export type SellerDashboardReport = {
  generatedAt: string;
  period: SellerReportPeriod;
  summary: SellerReportSummary;
  productStatusCounts: SellerStatusCount[];
  orderStatusCounts: SellerStatusCount[];
};

export function getSellerDashboardReport() {
  return api<SellerDashboardReport>('/api/seller/reports/dashboard');
}
