import { createBrowserRouter, Navigate, Outlet } from 'react-router-dom';
import { useAuth } from './providers/AuthProvider';
import { LoginPage } from '../features/auth/LoginPage';
import { AppShell } from '../features/layout/AppShell';
import { HomePage } from '../features/dashboard/HomePage';
import { GolemsPage } from '../features/golems/GolemsPage';
import { GolemRolesPage } from '../features/golems/GolemRolesPage';
import { BoardsPage } from '../features/boards/BoardsPage';
import { KanbanBoardPage } from '../features/boards/KanbanBoardPage';
import { BoardEditorPage } from '../features/boards/BoardEditorPage';
import { CardThreadPage } from '../features/chat/CardThreadPage';
import { ApprovalsPage } from '../features/approvals/ApprovalsPage';
import { AuditPage } from '../features/audit/AuditPage';
import { BudgetsPage } from '../features/budgets/BudgetsPage';
import { SystemSettingsPage } from '../features/settings/SystemSettingsPage';

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
            element: <BoardsPage />,
          },
          {
            path: '/approvals',
            element: <ApprovalsPage />,
          },
          {
            path: '/audit',
            element: <AuditPage />,
          },
          {
            path: '/budgets',
            element: <BudgetsPage />,
          },
          {
            path: '/settings',
            element: <SystemSettingsPage />,
          },
          {
            path: '/boards/:boardId',
            element: <KanbanBoardPage />,
          },
          {
            path: '/boards/:boardId/settings',
            element: <BoardEditorPage />,
          },
          {
            path: '/cards/:cardId/thread',
            element: <CardThreadPage />,
          },
        ],
      },
    ],
  },
]);
