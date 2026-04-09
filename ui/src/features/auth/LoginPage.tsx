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
      setError('Login failed. Check credentials.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center px-4 py-10">
      <section className="panel w-full max-w-sm p-6">
        <form className="space-y-5" onSubmit={handleSubmit}>
          <div>
            <span className="pill">Golemcore Hive</span>
            <h2 className="mt-3 text-2xl font-bold tracking-tight text-foreground">Sign in</h2>
          </div>

          <div className="space-y-3">
            <label className="block space-y-1.5">
              <span className="text-sm font-medium text-foreground">Username</span>
              <input
                className="w-full border border-border bg-panel/80 px-4 py-2.5 text-sm text-foreground outline-none transition focus:border-primary focus:ring-1 focus:ring-primary/50"
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                autoComplete="username"
                placeholder="admin"
              />
            </label>

            <label className="block space-y-1.5">
              <span className="text-sm font-medium text-foreground">Password</span>
              <input
                type="password"
                className="w-full border border-border bg-panel/80 px-4 py-2.5 text-sm text-foreground outline-none transition focus:border-primary focus:ring-1 focus:ring-primary/50"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                autoComplete="current-password"
              />
            </label>
          </div>

          {error ? (
            <div className="border border-red-800 bg-red-950/50 px-4 py-2.5 text-sm text-red-400">{error}</div>
          ) : null}

          <button
            type="submit"
            disabled={submitting}
            className="w-full bg-primary px-4 py-2.5 text-sm font-semibold text-primary-foreground shadow-glow transition hover:opacity-95 disabled:opacity-60"
          >
            {submitting ? 'Signing in...' : 'Sign in'}
          </button>
        </form>
      </section>
    </div>
  );
}
