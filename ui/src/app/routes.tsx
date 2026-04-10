import { Navigate, createBrowserRouter, useParams } from 'react-router-dom';
import { RequireAuth } from './RequireAuth';
import { LoginPage } from '../features/auth/LoginPage';
import { AppShell } from '../features/layout/AppShell';
import { GolemsPage } from '../features/golems/GolemsPage';
import { GolemRolesPage } from '../features/golems/GolemRolesPage';
import { KanbanBoardPage } from '../features/boards/KanbanBoardPage';
import { BoardEditorPage } from '../features/boards/BoardEditorPage';
import { CardThreadPage } from '../features/chat/CardThreadPage';
import { GolemChatPage } from '../features/chat/GolemChatPage';
import { ApprovalsPage } from '../features/approvals/ApprovalsPage';
import { AuditPage } from '../features/audit/AuditPage';
import { BudgetsPage } from '../features/budgets/BudgetsPage';
import { SystemSettingsPage } from '../features/settings/SystemSettingsPage';
import { InspectionPage } from '../features/inspection/InspectionPage';
import { PolicyGroupsPage } from '../features/policies/PolicyGroupsPage';
import { HomePage } from '../features/dashboard/HomePage';
import { ObjectivesPage } from '../features/objectives/ObjectivesPage';
import { ServicesPage } from '../features/services/ServicesPage';
import { TeamsPage } from '../features/teams/TeamsPage';

function LegacyBoardQueueRedirect() {
  const { boardId = '' } = useParams();
  return <Navigate to={`/services/${boardId}`} replace />;
}

function LegacyBoardSettingsRedirect() {
  const { boardId = '' } = useParams();
  return <Navigate to={`/services/${boardId}/settings`} replace />;
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
            path: '/services',
            element: <ServicesPage />,
          },
          {
            path: '/teams',
            element: <TeamsPage />,
          },
          {
            path: '/objectives',
            element: <ObjectivesPage />,
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
            element: <Navigate to="/services" replace />,
          },
          {
            path: '/policies',
            element: <PolicyGroupsPage />,
          },
          {
            path: '/policies/:groupId',
            element: <PolicyGroupsPage />,
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
            path: '/services/:serviceId',
            element: <KanbanBoardPage />,
          },
          {
            path: '/services/:serviceId/settings',
            element: <BoardEditorPage />,
          },
          {
            path: '/boards/:boardId',
            element: <LegacyBoardQueueRedirect />,
          },
          {
            path: '/boards/:boardId/settings',
            element: <LegacyBoardSettingsRedirect />,
          },
          {
            path: '/cards/:cardId/thread',
            element: <CardThreadPage />,
          },
          {
            path: '/fleet/chat',
            element: <GolemChatPage />,
          },
          {
            path: '/fleet/chat/:golemId',
            element: <GolemChatPage />,
          },
          {
            path: '/fleet/inspection/:golemId',
            element: <InspectionPage />,
          },
        ],
      },
    ],
  },
]);
