import { createBrowserRouter, Navigate, Outlet } from 'react-router-dom';
import { useAuth } from './providers/AuthProvider';
import { LoginPage } from '../features/auth/LoginPage';
import { AppShell } from '../features/layout/AppShell';
import { HomePage } from '../features/dashboard/HomePage';
import { GolemsPage } from '../features/golems/GolemsPage';
import { GolemRolesPage } from '../features/golems/GolemRolesPage';

function RequireAuth() {
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

function ComingSoonPage({ title, text }: { title: string; text: string }) {
  return (
    <div className="panel p-6 md:p-8">
      <span className="pill">Coming next</span>
      <h2 className="mt-4 text-3xl font-bold tracking-[-0.04em] text-foreground">{title}</h2>
      <p className="mt-3 max-w-2xl text-sm leading-7 text-muted-foreground">{text}</p>
    </div>
  );
}

export const routes = createBrowserRouter([
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <AppShell />,
        children: [
          {
            path: '/',
            element: <HomePage />,
          },
          {
            path: '/fleet',
            element: <GolemsPage />,
          },
          {
            path: '/fleet/roles',
            element: <GolemRolesPage />,
          },
          {
            path: '/boards',
            element: (
              <ComingSoonPage
                title="Boards and cards land in Phase 3"
                text="This route will become the multi-board Kanban surface with board teams, assignment policies, and flow remapping."
              />
            ),
          },
        ],
      },
    ],
  },
]);
