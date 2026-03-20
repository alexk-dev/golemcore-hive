import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { getSystemSettings } from '../../lib/api/systemApi';
import { SystemSettingsPage } from './SystemSettingsPage';

vi.mock('../../lib/api/systemApi', async () => {
  const actual = await vi.importActual<typeof import('../../lib/api/systemApi')>('../../lib/api/systemApi');
  return {
    ...actual,
    acknowledgeNotification: vi.fn(),
    getSystemSettings: vi.fn(),
  };
});

const getSystemSettingsMock = vi.mocked(getSystemSettings);

describe('SystemSettingsPage', () => {
  it('renders grouped operational facts and dense notification rows', async () => {
    getSystemSettingsMock.mockResolvedValue({
      productionMode: true,
      storageBasePath: '/var/lib/hive',
      secureRefreshCookie: true,
      highCostThresholdMicros: 400000,
      retention: {
        approvalsDays: 14,
        auditDays: 30,
        notificationsDays: 7,
      },
      notifications: {
        approvalRequested: true,
        blockerRaised: false,
        golemOffline: true,
        commandFailed: true,
      },
      recentNotifications: [
        {
          id: 'notification_1',
          type: 'APPROVAL_REQUESTED',
          severity: 'WARN',
          title: 'Approval requested',
          message: 'Review high-cost command',
          boardId: 'board_1',
          cardId: 'card_1',
          threadId: null,
          golemId: 'golem_1',
          commandId: 'command_1',
          approvalId: 'approval_1',
          acknowledged: false,
          createdAt: '2026-03-20T18:00:00Z',
          acknowledgedAt: null,
        },
      ],
    });

    const { container } = renderPage();

    expect(await screen.findByRole('heading', { name: 'System settings' })).toBeInTheDocument();
    expect(await screen.findByText('/var/lib/hive')).toBeInTheDocument();
    expect(screen.queryByText('Operational defaults for self-hosted Hive')).not.toBeInTheDocument();
    expect(screen.getByText('Production mode')).toBeInTheDocument();
    expect(screen.getAllByText('Approval requested')).toHaveLength(2);
    expect(screen.getByText('Review high-cost command')).toBeInTheDocument();
    expect(container.querySelector('.soft-card')).toBeNull();
  });
});

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <SystemSettingsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}
