import { api } from '../../shared/api/http';

export type MemberRole = 'MEMBER' | 'ADMIN';

export type CurrentMember = {
  id: number;
  email: string;
  nickname: string;
  role: MemberRole;
};

export type AuthResponse = {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  member: {
    id: number;
    email: string;
    nickname: string;
  };
};

export function login(email: string, password: string) {
  return api<AuthResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  });
}

export function signup(email: string, password: string, nickname: string) {
  return api<AuthResponse['member']>('/api/auth/signup', {
    method: 'POST',
    body: JSON.stringify({ email, password, nickname }),
  });
}

export function getCurrentMember() {
  return api<CurrentMember>('/api/members/me');
}
