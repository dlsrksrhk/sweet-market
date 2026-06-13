import { type FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../features/auth/AuthProvider';
import { type ApiError } from '../shared/api/http';

export function SignupPage() {
  const { signup } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [nickname, setNickname] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);

    try {
      await signup(email, password, nickname);
      navigate('/', { replace: true });
    } catch (apiError) {
      setError(toErrorMessage(apiError));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="auth-page">
      <div className="auth-panel">
        <h1>회원가입</h1>
        <form className="auth-form" onSubmit={handleSubmit}>
          <label>
            이메일
            <input
              type="email"
              autoComplete="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              required
            />
          </label>
          <label>
            비밀번호
            <input
              type="password"
              autoComplete="new-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
            />
          </label>
          <label>
            닉네임
            <input
              type="text"
              autoComplete="nickname"
              value={nickname}
              onChange={(event) => setNickname(event.target.value)}
              required
            />
          </label>
          {error ? <p className="error-text">{error}</p> : null}
          <button type="submit" disabled={submitting}>
            {submitting ? '가입 중' : '회원가입'}
          </button>
        </form>
        <p className="auth-link">
          이미 계정이 있다면 <Link to="/login">로그인</Link>
        </p>
      </div>
    </section>
  );
}

function toErrorMessage(error: unknown) {
  const apiError = error as Partial<ApiError>;
  const fieldMessage = apiError.fieldErrors?.[0]?.message;

  return fieldMessage ?? apiError.message ?? '회원가입에 실패했습니다.';
}
