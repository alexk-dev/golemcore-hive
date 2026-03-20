import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { listBudgetSnapshots } from '../../lib/api/budgetsApi';
import { BudgetsPage } from './BudgetsPage';

vi.mock('../../lib/api/budgetsApi', async () => {
  return {
    listBudgetSnapshots: vi.fn(),
  };
});

const listBudgetSnapshotsMock = vi.mocked(listBudgetSnapshots);

describe('BudgetsPage', () => {
  it('renders dense snapshot rows instead of metric cards', async () => {
    listBudgetSnapshotsMock.mockResolvedValue([
      {
        id: 'budget_1',
        scopeType: 'BOARD',
        scopeId: 'board_1',
        scopeLabel: 'Board Alpha',
        boardId: 'board_1',
        cardId: null,
        golemId: null,
        commandCount: 4,
        runCount: 3,
        inputTokens: 1200,
        outputTokens: 800,
        actualCostMicros: 540000,
        estimatedPendingCostMicros: 90000,
        updatedAt: '2026-03-20T18:00:00Z',
      },
    ]);

    const { container } = renderPage();

    expect(await screen.findByRole('heading', { name: 'Budget snapshots' })).toBeInTheDocument();
    expect(await screen.findByRole('table')).toBeInTheDocument();
    expect(await screen.findByText('Board Alpha')).toBeInTheDocument();
    expect(screen.queryByText('Track cost and token pressure')).not.toBeInTheDocument();
    expect(screen.getByText('4 commands')).toBeInTheDocument();
    expect(screen.getByText('540,000 actual')).toBeInTheDocument();
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
        <BudgetsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}
