import { createBrowserRouter } from 'react-router-dom';
import { RequireAuth } from './RequireAuth';
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
