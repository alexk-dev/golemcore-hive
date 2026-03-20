import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import type * as GolemsApiModule from '../../lib/api/golemsApi';
import { getGolem, listEnrollmentTokens, listGolemRoles, listGolems } from '../../lib/api/golemsApi';
import { GolemsPage } from './GolemsPage';

vi.mock('../../lib/api/golemsApi', async () => {
  const actual = await vi.importActual<typeof GolemsApiModule>('../../lib/api/golemsApi');
  return {
    ...actual,
    getGolem: vi.fn(),
    listEnrollmentTokens: vi.fn(),
    listGolemRoles: vi.fn(),
    listGolems: vi.fn(),
    assignGolemRoles: vi.fn(),
    unassignGolemRoles: vi.fn(),
    createEnrollmentToken: vi.fn(),
    pauseGolem: vi.fn(),
    resumeGolem: vi.fn(),
    revokeEnrollmentToken: vi.fn(),
    revokeGolem: vi.fn(),
  };
});

const listGolemsMock = vi.mocked(listGolems);
const getGolemMock = vi.mocked(getGolem);
const listGolemRolesMock = vi.mocked(listGolemRoles);
const listEnrollmentTokensMock = vi.mocked(listEnrollmentTokens);

describe('GolemsPage', () => {
  it('uses a compact fleet header and hides empty capabilities blocks', async () => {
    listGolemsMock.mockResolvedValue([
      {
        id: 'golem_1',
        displayName: 'GolemCore Bot',
        hostLabel: '32afb2b6f333',
        runtimeVersion: '0.40.0',
        state: 'ONLINE',
        lastHeartbeatAt: '2026-03-20T18:53:10Z',
        lastSeenAt: '2026-03-20T18:53:10Z',
        missedHeartbeatCount: 0,
        roleSlugs: [],
      },
    ]);
    getGolemMock.mockResolvedValue({
      id: 'golem_1',
      displayName: 'GolemCore Bot',
      hostLabel: '32afb2b6f333',
      runtimeVersion: '0.40.0',
      buildVersion: '5392254',
      state: 'ONLINE',
      registeredAt: '2026-03-20T18:00:00Z',
      createdAt: '2026-03-20T18:00:00Z',
      updatedAt: '2026-03-20T18:00:00Z',
      lastHeartbeatAt: '2026-03-20T18:53:10Z',
      lastSeenAt: '2026-03-20T18:53:10Z',
      lastStateChangeAt: '2026-03-20T18:53:10Z',
      heartbeatIntervalSeconds: 30,
      missedHeartbeatCount: 0,
      pauseReason: null,
      revokeReason: null,
      controlChannelUrl: '/ws/golems/control',
      supportedChannels: ['hive'],
      capabilities: {
        providers: [],
        modelFamilies: [],
        enabledTools: [],
        enabledAutonomyFeatures: [],
        capabilityTags: [],
        supportedChannels: [],
        snapshotHash: null,
        defaultModel: null,
      },
      lastHeartbeat: {
        status: 'ONLINE',
        currentRunState: null,
        currentCardId: null,
        currentThreadId: null,
        modelTier: null,
        inputTokens: 0,
        outputTokens: 0,
        accumulatedCostMicros: 0,
        queueDepth: 0,
        healthSummary: null,
        lastErrorSummary: null,
        uptimeSeconds: 10,
        capabilitySnapshotHash: null,
      },
      roleSlugs: [],
    });
    listGolemRolesMock.mockResolvedValue([]);
    listEnrollmentTokensMock.mockResolvedValue([
      {
        id: 'et_1',
        preview: 'et_1.abc',
        note: 'prod',
        createdByUsername: 'admin',
        createdAt: '2026-03-20T18:00:00Z',
        expiresAt: '2026-03-20T19:00:00Z',
        lastUsedAt: '2026-03-20T18:40:00Z',
        registrationCount: 1,
        revokedAt: null,
        lastRegisteredGolemId: 'golem_1',
        revokeReason: null,
        revoked: false,
      },
    ]);

    renderPage();

    expect(await screen.findByRole('heading', { name: 'Fleet registry' })).toBeInTheDocument();
    expect(await screen.findByText('GolemCore Bot')).toBeInTheDocument();
    expect(await screen.findByText('et_1')).toBeInTheDocument();
    expect(screen.queryByText('Register runtimes, bind roles, and watch fleet presence in one place.')).not.toBeInTheDocument();
    expect(screen.queryByText('Capabilities')).not.toBeInTheDocument();
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
      <MemoryRouter>
        <GolemsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}
