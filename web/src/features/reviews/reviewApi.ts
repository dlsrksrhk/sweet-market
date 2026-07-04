import { api } from '../../shared/api/http';
import { type Page } from '../products/productApi';

export type Review = {
  reviewId: number;
  orderId: number;
  productId: number;
  buyerId: number;
  buyerNickname: string;
  rating: number;
  content: string;
  createdAt: string;
};

export type ReviewCreateInput = {
  rating: number;
  content: string;
};

export function createReview(orderId: number, input: ReviewCreateInput) {
  return api<Review>(`/api/orders/${orderId}/review`, {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function getProductReviews(productId: number) {
  return api<Page<Review>>(`/api/products/${productId}/reviews`);
}
