import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { listBudgetSnapshots } from '../../lib/api/budgetsApi';
import { BudgetsPage } from './BudgetsPage';

vi.mock('@tanstack/react-virtual', () => ({
  useVirtualizer: ({ count, estimateSize }: { count: number; estimateSize: () => number }) => ({
    getTotalSize: () => count * estimateSize(),
    getVirtualItems: () =>
      Array.from({ length: count }, (_, index) => ({
        index,
        start: index * estimateSize(),
        key: index,
      })),
  }),
}));

vi.mock('../../lib/api/budgetsApi', () => ({
  listBudgetSnapshots: vi.fn(),
}));

const listBudgetSnapshotsMock = vi.mocked(listBudgetSnapshots);

describe('BudgetsPage', () => {
  beforeEach(() => {
    listBudgetSnapshotsMock.mockResolvedValue([
      {
        id: 'budget_objective_1',
        scopeType: 'OBJECTIVE',
        scopeId: 'objective-1',
        scopeLabel: 'Reduce onboarding latency',
        customerId: null,
        teamId: 'team-1',
        objectiveId: 'objective-1',
        serviceId: 'service-1',
        commandCount: 3,
        runCount: 2,
        inputTokens: 120,
        outputTokens: 60,
        actualCostMicros: 42,
        estimatedPendingCostMicros: 7,
        updatedAt: '2026-04-10T12:00:00Z',
      },
    ]);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.clearAllMocks();
  });

  it('filters and renders outcome budget scopes', async () => {
    renderPage();

    expect(await screen.findByText('Reduce onboarding latency')).toBeInTheDocument();
    expect(screen.getByText('OBJECTIVE')).toBeInTheDocument();
    expect(screen.getByText('42')).toBeInTheDocument();
    expect(listBudgetSnapshotsMock).toHaveBeenCalledWith(undefined, undefined);

    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'OBJECTIVE' } });
    fireEvent.change(screen.getByPlaceholderText(/filter by label/i), { target: { value: 'latency' } });

    await waitFor(() => {
      expect(listBudgetSnapshotsMock).toHaveBeenCalledWith('OBJECTIVE', 'latency');
    });
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

  render(
    <QueryClientProvider client={queryClient}>
      <BudgetsPage />
    </QueryClientProvider>,
  );
}
