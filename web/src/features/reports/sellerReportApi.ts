import { api } from '../../shared/api/http';

export type ProductStatus = 'ON_SALE' | 'RESERVED' | 'SOLD_OUT' | 'HIDDEN';
export type OrderStatus = 'CREATED' | 'PAID' | 'SHIPPING' | 'DELIVERED' | 'CONFIRMED' | 'CANCELED';
export type SettlementStatus = 'READY' | 'COMPLETED' | 'FAILED';
export type SellerRecentSaleSettlementStatus = SettlementStatus | 'NONE';

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

export type SellerProductStatusCount = {
  status: ProductStatus;
  count: number;
};

export type SellerOrderStatusCount = {
  status: OrderStatus;
  count: number;
};

export type SellerDashboardReport = {
  generatedAt: string;
  period: SellerReportPeriod;
  summary: SellerReportSummary;
  productStatusCounts: SellerProductStatusCount[];
  orderStatusCounts: SellerOrderStatusCount[];
};

export type SellerPeriodInput = {
  from: string;
  to: string;
};

export type SellerPeriod = {
  from: string;
  to: string;
  days: number;
};

export type SellerPeriodSummary = {
  orderedCount: number;
  confirmedOrderCount: number;
  confirmedSalesAmount: number;
  completedSettlementAmount: number;
  unsettledConfirmedAmount: number;
  averageConfirmedOrderAmount: number;
};

export type SellerProductRanking = {
  productId: number;
  title: string;
  thumbnailUrl: string | null;
  confirmedOrderCount: number;
  confirmedSalesAmount: number;
  lastConfirmedAt: string;
};

export type SellerDailySales = {
  date: string;
  confirmedOrderCount: number;
  confirmedSalesAmount: number;
};

export type SellerRecentSale = {
  orderId: number;
  productId: number;
  productTitle: string;
  buyerNickname: string;
  amount: number;
  confirmedAt: string;
  settlementStatus: SellerRecentSaleSettlementStatus;
};

export type SellerRecentSettlement = {
  settlementId: number;
  orderId: number;
  productId: number;
  productTitle: string;
  amount: number;
  status: SettlementStatus;
  settledAt: string;
};

export type SellerPeriodReport = {
  generatedAt: string;
  period: SellerPeriod;
  summary: SellerPeriodSummary;
  productRankings: SellerProductRanking[];
  dailySales: SellerDailySales[];
  recentSales: SellerRecentSale[];
  recentSettlements: SellerRecentSettlement[];
};

export function getSellerDashboardReport() {
  return api<SellerDashboardReport>('/api/seller/reports/dashboard');
}

export function getSellerPeriodReport(input: SellerPeriodInput) {
  const params = new URLSearchParams({
    from: input.from,
    to: input.to,
  });

  return api<SellerPeriodReport>(`/api/seller/reports/period?${params.toString()}`);
}
