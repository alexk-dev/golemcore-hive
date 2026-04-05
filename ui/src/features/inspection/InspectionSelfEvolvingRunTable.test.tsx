import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { SelfEvolvingRun } from '../../lib/api/selfEvolvingApi';
import { InspectionSelfEvolvingRunTable } from './InspectionSelfEvolvingRunTable';

describe('InspectionSelfEvolvingRunTable', () => {
  it('renders readonly run summaries and exposes selection', () => {
    const onSelectRun = vi.fn();
    const runs: SelfEvolvingRun[] = [
      {
        id: 'run-1',
        golemId: 'golem_1',
        sessionId: 'web:conv-1',
        traceId: 'trace-1',
        artifactBundleId: 'bundle-1',
        artifactBundleStatus: 'active',
        status: 'COMPLETED',
        outcomeStatus: 'COMPLETED',
        processStatus: 'HEALTHY',
        promotionRecommendation: 'approve_gated',
        outcomeSummary: 'done',
        processSummary: 'good',
        confidence: 0.91,
        processFindings: ['No tier escalation required'],
        startedAt: '2026-03-30T20:00:00Z',
        completedAt: '2026-03-30T20:00:30Z',
        updatedAt: '2026-03-30T20:00:31Z',
      },
    ];

    render(<InspectionSelfEvolvingRunTable runs={runs} selectedRunId={null} onSelectRun={onSelectRun} />);

    expect(screen.getByText('run-1')).toBeInTheDocument();
    expect(screen.getAllByText('COMPLETED')).toHaveLength(2);

    fireEvent.click(screen.getByRole('button', { name: /run-1/i }));

    expect(onSelectRun).toHaveBeenCalledWith('run-1');
  });
});
