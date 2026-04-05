import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { GolemSummary } from '../../lib/api/golemsApi';
import {
  compareSelfEvolvingArtifacts,
  searchSelfEvolvingArtifacts,
} from '../../lib/api/selfEvolvingApi';
import { GolemsSelfEvolvingArtifactsPanel } from './GolemsSelfEvolvingArtifactsPanel';

vi.mock('../../lib/api/selfEvolvingApi', () => ({
  searchSelfEvolvingArtifacts: vi.fn(),
  compareSelfEvolvingArtifacts: vi.fn(),
}));

const searchSelfEvolvingArtifactsMock = vi.mocked(searchSelfEvolvingArtifacts);
const compareSelfEvolvingArtifactsMock = vi.mocked(compareSelfEvolvingArtifacts);

describe('GolemsSelfEvolvingArtifactsPanel', () => {
  it('renders fleet search and compare summary', async () => {
    searchSelfEvolvingArtifactsMock.mockResolvedValue([
      {
        golemId: 'golem-1',
        artifactStreamId: 'stream-1',
        originArtifactStreamId: 'stream-1',
        artifactKey: 'skill:planner',
        artifactAliases: ['skill:planner'],
        artifactType: 'skill',
        artifactSubtype: 'skill',
        displayName: 'Planner skill',
        latestRevisionId: 'rev-2',
        activeRevisionId: 'rev-2',
        latestCandidateRevisionId: 'rev-2',
        currentLifecycleState: 'active',
        currentRolloutStage: 'active',
        hasRegression: false,
        hasPendingApproval: false,
        campaignCount: 1,
        projectionSchemaVersion: 1,
        sourceBotVersion: 'bot-1',
        projectedAt: '2026-03-30T20:00:00Z',
        updatedAt: '2026-03-30T20:00:00Z',
        stale: false,
        staleReason: null,
      },
      {
        golemId: 'golem-2',
        artifactStreamId: 'stream-1',
        originArtifactStreamId: 'stream-1',
        artifactKey: 'skill:planner',
        artifactAliases: ['skill:planner'],
        artifactType: 'skill',
        artifactSubtype: 'skill',
        displayName: 'Planner skill',
        latestRevisionId: 'rev-3',
        activeRevisionId: 'rev-3',
        latestCandidateRevisionId: 'rev-3',
        currentLifecycleState: 'active',
        currentRolloutStage: 'active',
        hasRegression: true,
        hasPendingApproval: true,
        campaignCount: 2,
        projectionSchemaVersion: 1,
        sourceBotVersion: 'bot-2',
        projectedAt: '2026-03-30T20:10:00Z',
        updatedAt: '2026-03-30T20:10:00Z',
        stale: true,
        staleReason: 'Missing lineage projection',
      },
    ]);
    compareSelfEvolvingArtifactsMock.mockResolvedValue({
      artifactStreamId: 'stream-1',
      leftGolemId: 'golem-1',
      rightGolemId: 'golem-2',
      leftRevisionId: 'rev-2',
      rightRevisionId: 'rev-3',
      leftNormalizedHash: 'hash-2',
      rightNormalizedHash: 'hash-3',
      sameContent: false,
      leftStale: false,
      rightStale: true,
      summary: 'Artifacts diverged across golems',
      normalizationSchemaVersion: 1,
      projectedAt: '2026-03-30T20:11:00Z',
      warnings: ['Right artifact projection is stale: Missing lineage projection'],
    });

    render(
      <QueryClientProvider client={new QueryClient()}>
        <GolemsSelfEvolvingArtifactsPanel golems={createGolems()} />
      </QueryClientProvider>,
    );

    expect(await screen.findByText('Compare across golems')).toBeInTheDocument();
    expect(await screen.findAllByText('Planner skill')).toHaveLength(2);

    fireEvent.click(screen.getAllByRole('button', { name: /set left/i })[0]);
    fireEvent.click(screen.getAllByRole('button', { name: /set right/i })[1]);

    await waitFor(() => {
      expect(compareSelfEvolvingArtifactsMock).toHaveBeenCalledWith('stream-1', 'golem-1', 'golem-2', 'rev-2', 'rev-3');
    });

    expect(await screen.findByText('Artifacts diverged across golems')).toBeInTheDocument();
    expect(screen.getAllByText(/stale/i).length).toBeGreaterThan(0);
  });
});

function createGolems(): GolemSummary[] {
  return [
    {
      id: 'golem-1',
      displayName: 'Atlas One',
      hostLabel: 'host-1',
      runtimeVersion: 'bot-1',
      state: 'ONLINE',
      lastHeartbeatAt: '2026-03-30T20:00:00Z',
      lastSeenAt: '2026-03-30T20:00:00Z',
      missedHeartbeatCount: 0,
      roleSlugs: ['worker'],
    },
    {
      id: 'golem-2',
      displayName: 'Atlas Two',
      hostLabel: 'host-2',
      runtimeVersion: 'bot-2',
      state: 'ONLINE',
      lastHeartbeatAt: '2026-03-30T20:10:00Z',
      lastSeenAt: '2026-03-30T20:10:00Z',
      missedHeartbeatCount: 0,
      roleSlugs: ['worker'],
    },
  ];
}
