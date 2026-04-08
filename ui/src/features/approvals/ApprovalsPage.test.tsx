import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { approveApproval, listApprovals, rejectApproval } from '../../lib/api/approvalsApi';
import { ApprovalsPage } from './ApprovalsPage';

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

vi.mock('../../lib/api/approvalsApi', () => ({
  listApprovals: vi.fn(),
  approveApproval: vi.fn(),
  rejectApproval: vi.fn(),
}));

const listApprovalsMock = vi.mocked(listApprovals);
const approveApprovalMock = vi.mocked(approveApproval);
const rejectApprovalMock = vi.mocked(rejectApproval);

describe('ApprovalsPage', () => {
  beforeEach(() => {
    approveApprovalMock.mockResolvedValue({} as never);
    rejectApprovalMock.mockResolvedValue({} as never);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.clearAllMocks();
  });

  it('renders selfevolving promotion approvals with contextual evidence', async () => {
    listApprovalsMock.mockResolvedValue([
      {
        id: 'apr_promo_1',
        commandId: null,
        runId: 'run-1',
        threadId: null,
        boardId: null,
        cardId: null,
        golemId: 'golem-1',
        requestedByActorId: 'operator-1',
        requestedByActorName: 'Hive Admin',
        riskLevel: null,
        reason: 'Awaiting approval before rollout',
        estimatedCostMicros: 0,
        commandBody: '',
        status: 'PENDING',
        requestedAt: '2026-03-31T18:00:00Z',
        updatedAt: '2026-03-31T18:00:00Z',
        decidedAt: null,
        decidedByActorId: null,
        decidedByActorName: null,
        decisionComment: null,
        subjectType: 'SELF_EVOLVING_PROMOTION',
        promotionContext: {
          candidateId: 'candidate-1',
          goal: 'fix',
          artifactType: 'skill',
          riskLevel: 'medium',
          expectedImpact: 'Reduce routing failures',
          sourceRunIds: ['run-1', 'run-2'],
        },
      },
    ]);

    renderPage();

    expect(await screen.findByText('SELF_EVOLVING_PROMOTION')).toBeInTheDocument();
    expect(screen.getByText('skill • fix')).toBeInTheDocument();
    expect(screen.getByText('Reduce routing failures')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Approve' }));

    expect(screen.getByText('Candidate: candidate-1')).toBeInTheDocument();
    expect(screen.getByText('Source runs: run-1, run-2')).toBeInTheDocument();
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
      <ApprovalsPage />
    </QueryClientProvider>,
  );
}
