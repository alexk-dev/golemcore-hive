import { useState, type FormEvent } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../../app/providers/useAuth';

export function LoginPage() {
  const { login, status } = useAuth();
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('change-me-now');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (status === 'authenticated') {
    return <Navigate to="/" replace />;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await login(username, password);
    } catch {
      setError('Login failed. Check the bootstrap operator credentials.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center px-4 py-10">
      <div className="grid w-full max-w-5xl gap-6 lg:grid-cols-[1.1fr_0.9fr]">
        <section className="panel hidden p-8 lg:block">
          <div className="flex h-full flex-col justify-between gap-6">
            <div className="space-y-4">
              <span className="pill">Operator access</span>
              <h1 className="text-5xl font-bold tracking-[-0.05em] text-foreground">Hive control plane</h1>
              <p className="max-w-xl text-base leading-8 text-muted-foreground">
                Sign in with the bootstrap operator to manage fleet, boards, approvals, audit, and budgets.
              </p>
            </div>

            <div className="section-surface p-5">
              <p className="text-xs font-semibold uppercase tracking-[0.2em] text-muted-foreground">Access model</p>
              <p className="mt-3 text-lg font-semibold text-foreground">JWT access + refresh</p>
              <p className="mt-2 text-sm leading-6 text-muted-foreground">
                The browser keeps refresh tokens in an `HttpOnly` cookie while UI access tokens stay in memory only.
              </p>
            </div>
          </div>
        </section>

        <section className="panel p-6 md:p-8">
          <form className="space-y-6" onSubmit={handleSubmit}>
            <div className="space-y-3">
              <span className="pill">Sign in</span>
              <h2 className="text-3xl font-bold tracking-[-0.04em] text-foreground">Bootstrap the control plane</h2>
              <p className="text-sm leading-7 text-muted-foreground">Use the bootstrap operator credentials configured for this deployment.</p>
            </div>

            <div className="space-y-4">
              <label className="block space-y-2">
                <span className="text-sm font-medium text-foreground">Username</span>
                <input
                  className="w-full rounded-2xl border border-border bg-white/80 px-4 py-3 text-sm text-foreground outline-none ring-0 transition placeholder:text-muted-foreground focus:border-primary focus:shadow-[0_0_0_4px_rgba(234,88,12,0.12)]"
                  value={username}
                  onChange={(event) => setUsername(event.target.value)}
                  autoComplete="username"
                  placeholder="admin"
                />
              </label>

              <label className="block space-y-2">
                <span className="text-sm font-medium text-foreground">Password</span>
                <input
                  type="password"
                  className="w-full rounded-2xl border border-border bg-white/80 px-4 py-3 text-sm text-foreground outline-none ring-0 transition placeholder:text-muted-foreground focus:border-primary focus:shadow-[0_0_0_4px_rgba(234,88,12,0.12)]"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  autoComplete="current-password"
                  placeholder="change-me-now"
                />
              </label>
            </div>

            {error ? (
              <div className="rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>
            ) : null}

            <button
              type="submit"
              disabled={submitting}
              className="w-full rounded-2xl bg-foreground px-4 py-3 text-sm font-semibold text-white shadow-glow transition hover:opacity-95 disabled:cursor-not-allowed disabled:opacity-70"
            >
              {submitting ? 'Signing in...' : 'Enter Hive'}
            </button>
          </form>
        </section>
      </div>
    </div>
  );
}
