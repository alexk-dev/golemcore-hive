import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import type * as ApprovalsApiModule from '../../lib/api/approvalsApi';
import { listApprovals } from '../../lib/api/approvalsApi';
import { ApprovalsPage } from './ApprovalsPage';

vi.mock('../../lib/api/approvalsApi', async () => {
  const actual = await vi.importActual<typeof ApprovalsApiModule>('../../lib/api/approvalsApi');
  return {
    ...actual,
    approveApproval: vi.fn(),
    listApprovals: vi.fn(),
    rejectApproval: vi.fn(),
  };
});

const listApprovalsMock = vi.mocked(listApprovals);

describe('ApprovalsPage', () => {
  it('renders a compact approval queue with inline actions', async () => {
    listApprovalsMock.mockResolvedValue([
      {
        id: 'approval_1',
        commandId: 'command_1',
        runId: 'run_1',
        threadId: 'thread_1',
        boardId: 'board_1',
        cardId: 'card_1',
        golemId: 'golem_1',
        requestedByActorId: 'op_1',
        requestedByActorName: 'admin',
        riskLevel: 'HIGH',
        reason: 'Large spend',
        estimatedCostMicros: 320000,
        commandBody: 'Deploy the budget migration',
        status: 'PENDING',
        requestedAt: '2026-03-20T18:00:00Z',
        updatedAt: '2026-03-20T18:00:00Z',
        decidedAt: null,
        decidedByActorId: null,
        decidedByActorName: null,
        decisionComment: null,
      },
    ]);

    const { container } = renderPage();

    expect(await screen.findByRole('heading', { name: 'Approval queue' })).toBeInTheDocument();
    expect(await screen.findByText('Deploy the budget migration')).toBeInTheDocument();
    expect(screen.queryByText('Gate destructive and high-cost work')).not.toBeInTheDocument();
    expect(screen.getByText('Pending 1')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Approve' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reject' })).toBeInTheDocument();
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
        <ApprovalsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}
