import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { GolemDetailsModal } from './GolemDetailsPanel';

vi.mock('./GolemStatusBadge', () => ({
  GolemStatusBadge: ({ state }: { state: string }) => <span>{state}</span>,
}));

describe('GolemDetailsModal', () => {
  it('shows current policy binding and rollout state', () => {
    render(
      <MemoryRouter>
        <GolemDetailsModal
          golem={{
            id: 'golem_1',
            displayName: 'Atlas',
            hostLabel: 'host-a',
            runtimeVersion: 'bot-1.2.3',
            buildVersion: 'build-42',
            state: 'ONLINE',
            registeredAt: '2026-04-08T10:00:00Z',
            createdAt: '2026-04-08T10:00:00Z',
            updatedAt: '2026-04-08T10:00:00Z',
            lastHeartbeatAt: '2026-04-08T10:30:00Z',
            lastSeenAt: '2026-04-08T10:30:00Z',
            lastStateChangeAt: '2026-04-08T10:00:00Z',
            heartbeatIntervalSeconds: 30,
            missedHeartbeatCount: 0,
            pauseReason: null,
            revokeReason: null,
            controlChannelUrl: 'ws://localhost:8080/ws/golems/control',
            supportedChannels: ['control', 'events'],
            capabilities: {
              providers: ['openai'],
              modelFamilies: ['gpt'],
              enabledTools: ['shell'],
              enabledAutonomyFeatures: ['planning'],
              capabilityTags: ['policy'],
              supportedChannels: ['control', 'events'],
              snapshotHash: 'abc123',
              defaultModel: 'gpt-5',
            },
            lastHeartbeat: {
              status: 'healthy',
              currentRunState: 'IDLE',
              currentCardId: null,
              currentThreadId: null,
              modelTier: 'pro',
              inputTokens: 0,
              outputTokens: 0,
              accumulatedCostMicros: 0,
              queueDepth: 0,
              healthSummary: 'ready',
              lastErrorSummary: null,
              uptimeSeconds: 120,
              capabilitySnapshotHash: 'abc123',
              policyGroupId: 'pg_1',
              targetPolicyVersion: 2,
              appliedPolicyVersion: 1,
              syncStatus: 'OUT_OF_SYNC',
              lastPolicyErrorDigest: 'provider timeout',
            },
            roleSlugs: ['developer'],
            policyBinding: {
              policyGroupId: 'pg_1',
              targetVersion: 2,
              appliedVersion: 1,
              syncStatus: 'OUT_OF_SYNC',
              lastSyncRequestedAt: '2026-04-08T10:25:00Z',
              lastAppliedAt: '2026-04-08T10:10:00Z',
              lastErrorDigest: 'provider timeout',
              lastErrorAt: '2026-04-08T10:26:00Z',
              driftSince: '2026-04-08T10:25:00Z',
            },
          }}
          roles={[]}
          policies={[
            {
              id: 'pg_1',
              slug: 'default-routing',
              name: 'Default Routing',
              description: 'Primary policy',
              status: 'ACTIVE',
              currentVersion: 2,
              draftSpec: null,
              createdAt: '2026-04-08T10:00:00Z',
              updatedAt: '2026-04-08T10:00:00Z',
              lastPublishedAt: '2026-04-08T10:10:00Z',
              lastPublishedBy: 'operator_1',
              lastPublishedByName: 'Admin',
              boundGolemCount: 1,
            },
          ]}
          isBusy={false}
          onClose={() => undefined}
          onToggleRole={async () => undefined}
          onPause={() => undefined}
          onResume={async () => undefined}
          onRevoke={() => undefined}
        />
      </MemoryRouter>,
    );

    expect(screen.getByText('default-routing')).toBeInTheDocument();
    expect(screen.getAllByText('OUT_OF_SYNC').length).toBeGreaterThan(0);
    expect(screen.getByText('provider timeout')).toBeInTheDocument();
    expect(screen.getByText('Target v2')).toBeInTheDocument();
  });
});
