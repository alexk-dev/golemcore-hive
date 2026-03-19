import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from './providers/useAuth';

export function RequireAuth() {
  const { status } = useAuth();

  if (status === 'loading') {
    return (
      <div className="flex min-h-screen items-center justify-center px-6">
        <div className="panel flex max-w-md flex-col items-center gap-4 px-8 py-10 text-center">
          <span className="pill">Loading</span>
          <h1 className="text-2xl font-bold tracking-[-0.04em] text-foreground">Restoring operator session</h1>
          <p className="text-sm leading-7 text-muted-foreground">
            Hive is checking the refresh cookie and rebuilding the access token in memory.
          </p>
        </div>
      </div>
    );
  }

  if (status === 'unauthenticated') {
    return <Navigate to="/login" replace />;
  }

  return <Outlet />;
}
