import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { listGolems } from '../../lib/api/golemsApi';
import {
  bindGolemPolicyGroup,
  getPolicyGroup,
  listPolicyGroupVersions,
  listPolicyGroups,
  rollbackPolicyGroup,
  unbindGolemPolicyGroup,
  updatePolicyGroupDraft,
} from '../../lib/api/policiesApi';
import { PolicyGroupsPage } from './PolicyGroupsPage';

vi.mock('../../lib/api/policiesApi', () => ({
  listPolicyGroups: vi.fn(),
  getPolicyGroup: vi.fn(),
  listPolicyGroupVersions: vi.fn(),
  createPolicyGroup: vi.fn(),
  updatePolicyGroupDraft: vi.fn(),
  publishPolicyGroup: vi.fn(),
  rollbackPolicyGroup: vi.fn(),
  bindGolemPolicyGroup: vi.fn(),
  unbindGolemPolicyGroup: vi.fn(),
}));

vi.mock('../../lib/api/golemsApi', () => ({
  listGolems: vi.fn(),
}));

const listPolicyGroupsMock = vi.mocked(listPolicyGroups);
const getPolicyGroupMock = vi.mocked(getPolicyGroup);
const listPolicyGroupVersionsMock = vi.mocked(listPolicyGroupVersions);
const updatePolicyGroupDraftMock = vi.mocked(updatePolicyGroupDraft);
const bindGolemPolicyGroupMock = vi.mocked(bindGolemPolicyGroup);
const unbindGolemPolicyGroupMock = vi.mocked(unbindGolemPolicyGroup);
const rollbackPolicyGroupMock = vi.mocked(rollbackPolicyGroup);
const listGolemsMock = vi.mocked(listGolems);

function runtimeSpecResponse() {
  return {
    tools: {
      filesystemEnabled: true,
      shellEnabled: true,
      skillManagementEnabled: true,
      skillTransitionEnabled: true,
      tierEnabled: true,
      goalManagementEnabled: true,
      shellEnvironmentVariables: [{ name: 'GITHUB_TOKEN', valuePresent: true }],
    },
    memory: {
      version: 2,
      enabled: true,
      softPromptBudgetTokens: 1800,
      maxPromptBudgetTokens: 6000,
      workingTopK: 6,
      episodicTopK: 8,
      semanticTopK: 8,
      proceduralTopK: 4,
      promotionEnabled: true,
      promotionMinConfidence: 0.75,
      decayEnabled: true,
      decayDays: 90,
      retrievalLookbackDays: 30,
      codeAwareExtractionEnabled: true,
      disclosure: {
        mode: 'summary',
        promptStyle: 'balanced',
        toolExpansionEnabled: true,
        disclosureHintsEnabled: true,
        detailMinScore: 0.8,
      },
      reranking: {
        enabled: true,
        profile: 'balanced',
      },
      diagnostics: {
        verbosity: 'basic',
      },
    },
    mcp: {
      enabled: true,
      defaultStartupTimeout: 30,
      defaultIdleTimeout: 5,
      catalog: [
        {
          name: 'github',
          description: 'GitHub tools',
          command: 'npx -y @modelcontextprotocol/server-github',
          envPresent: { GITHUB_TOKEN: true },
          startupTimeoutSeconds: 45,
          idleTimeoutMinutes: 10,
          enabled: true,
        },
      ],
    },
    autonomy: {
      enabled: true,
      tickIntervalSeconds: 1,
      taskTimeLimitMinutes: 10,
      autoStart: true,
      maxGoals: 3,
      modelTier: 'balanced',
      reflectionEnabled: true,
      reflectionFailureThreshold: 2,
      reflectionModelTier: 'reasoning',
      reflectionTierPriority: true,
      notifyMilestones: true,
    },
  };
}

describe('PolicyGroupsPage', () => {
  beforeEach(() => {
    listPolicyGroupsMock.mockResolvedValue([
      {
        id: 'pg_1',
        slug: 'default-routing',
        name: 'Default Routing',
        description: 'Primary policy',
        status: 'ACTIVE',
        currentVersion: 2,
        draftSpec: {
          schemaVersion: 1,
          llmProviders: {
            openai: {
              apiKeyPresent: true,
              baseUrl: 'https://api.example.com/openai',
              requestTimeoutSeconds: 30,
              apiType: 'openai',
              legacyApi: false,
            },
          },
          modelRouter: {
            temperature: 0.7,
            routing: {
              model: 'openai/gpt-5.1',
              reasoning: 'low',
            },
            tiers: {
              balanced: {
                model: 'openai/gpt-5.1',
                reasoning: 'low',
              },
            },
            dynamicTierEnabled: true,
          },
          modelCatalog: {
            defaultModel: 'openai/gpt-5.1',
            models: {
              'openai/gpt-5.1': {
                provider: 'openai',
                displayName: 'openai/gpt-5.1',
                supportsVision: true,
                supportsTemperature: true,
                maxInputTokens: 200000,
              },
            },
          },
          ...runtimeSpecResponse(),
          checksum: 'draft-checksum',
        },
        createdAt: '2026-04-08T10:00:00Z',
        updatedAt: '2026-04-08T10:30:00Z',
        lastPublishedAt: '2026-04-08T10:20:00Z',
        lastPublishedBy: 'operator_1',
        lastPublishedByName: 'Admin',
        boundGolemCount: 1,
      },
    ]);
    getPolicyGroupMock.mockResolvedValue({
      id: 'pg_1',
      slug: 'default-routing',
      name: 'Default Routing',
      description: 'Primary policy',
      status: 'ACTIVE',
      currentVersion: 2,
      draftSpec: {
        schemaVersion: 1,
        llmProviders: {
          openai: {
            apiKeyPresent: true,
            baseUrl: 'https://api.example.com/openai',
            requestTimeoutSeconds: 30,
            apiType: 'openai',
            legacyApi: false,
          },
        },
        modelRouter: {
          temperature: 0.7,
          routing: {
            model: 'openai/gpt-5.1',
            reasoning: 'low',
          },
          tiers: {
            balanced: {
              model: 'openai/gpt-5.1',
              reasoning: 'low',
            },
          },
          dynamicTierEnabled: true,
        },
        modelCatalog: {
          defaultModel: 'openai/gpt-5.1',
          models: {
            'openai/gpt-5.1': {
              provider: 'openai',
              displayName: 'openai/gpt-5.1',
              supportsVision: true,
              supportsTemperature: true,
              maxInputTokens: 200000,
            },
          },
        },
        ...runtimeSpecResponse(),
        checksum: 'draft-checksum',
      },
      createdAt: '2026-04-08T10:00:00Z',
      updatedAt: '2026-04-08T10:30:00Z',
      lastPublishedAt: '2026-04-08T10:20:00Z',
      lastPublishedBy: 'operator_1',
      lastPublishedByName: 'Admin',
      boundGolemCount: 1,
    });
    listPolicyGroupVersionsMock.mockResolvedValue([
      {
        version: 2,
        specSnapshot: {
          schemaVersion: 1,
          llmProviders: {
            openai: {
              apiKeyPresent: true,
              baseUrl: 'https://api.example.com/openai',
              requestTimeoutSeconds: 30,
              apiType: 'openai',
              legacyApi: false,
            },
          },
          modelRouter: {
            temperature: 0.7,
            routing: {
              model: 'openai/gpt-5.1',
              reasoning: 'low',
            },
            tiers: {
              balanced: {
                model: 'openai/gpt-5.1',
                reasoning: 'low',
              },
            },
            dynamicTierEnabled: true,
          },
          modelCatalog: {
            defaultModel: 'openai/gpt-5.1',
            models: {
              'openai/gpt-5.1': {
                provider: 'openai',
                displayName: 'openai/gpt-5.1',
                supportsVision: true,
                supportsTemperature: true,
                maxInputTokens: 200000,
              },
            },
          },
          ...runtimeSpecResponse(),
          checksum: 'version-two-checksum',
        },
        checksum: 'version-two-checksum',
        changeSummary: 'Switch provider',
        publishedAt: '2026-04-08T10:20:00Z',
        publishedBy: 'operator_1',
        publishedByName: 'Admin',
      },
      {
        version: 1,
        specSnapshot: {
          schemaVersion: 1,
          llmProviders: {},
          modelRouter: {
            temperature: 0.4,
            routing: null,
            tiers: {},
            dynamicTierEnabled: false,
          },
          modelCatalog: {
            defaultModel: 'openai/gpt-4.1',
            models: {},
          },
          ...runtimeSpecResponse(),
          checksum: 'version-one-checksum',
        },
        checksum: 'version-one-checksum',
        changeSummary: 'Initial publish',
        publishedAt: '2026-04-08T09:20:00Z',
        publishedBy: 'operator_1',
        publishedByName: 'Admin',
      },
    ]);
    listGolemsMock.mockResolvedValue([
      {
        id: 'golem_1',
        displayName: 'Atlas',
        hostLabel: 'host-a',
        runtimeVersion: 'bot-1.2.3',
        state: 'ONLINE',
        lastHeartbeatAt: '2026-04-08T10:40:00Z',
        lastSeenAt: '2026-04-08T10:40:00Z',
        missedHeartbeatCount: 0,
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
      },
      {
        id: 'golem_2',
        displayName: 'Rhea',
        hostLabel: 'host-b',
        runtimeVersion: 'bot-1.2.3',
        state: 'ONLINE',
        lastHeartbeatAt: '2026-04-08T10:41:00Z',
        lastSeenAt: '2026-04-08T10:41:00Z',
        missedHeartbeatCount: 0,
        roleSlugs: ['reviewer'],
        policyBinding: null,
      },
    ]);
    updatePolicyGroupDraftMock.mockResolvedValue({} as never);
    bindGolemPolicyGroupMock.mockResolvedValue({} as never);
    unbindGolemPolicyGroupMock.mockResolvedValue(undefined as never);
    rollbackPolicyGroupMock.mockResolvedValue({} as never);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.clearAllMocks();
  });

  it('renders policy rollout detail and supports draft save and binding actions', async () => {
    renderPage('/policies/pg_1');

    expect(await screen.findByRole('heading', { name: /policy groups/i })).toBeInTheDocument();
    expect((await screen.findAllByText('Default Routing')).length).toBeGreaterThan(0);
    expect((await screen.findAllByText('Bound golems')).length).toBeGreaterThan(0);
    expect((await screen.findAllByText('Atlas')).length).toBeGreaterThan(0);
    expect((await screen.findAllByText('OUT_OF_SYNC')).length).toBeGreaterThan(0);

    fireEvent.change(screen.getByLabelText(/attach golem/i), { target: { value: 'golem_2' } });
    fireEvent.click(screen.getByRole('button', { name: /attach selected/i }));

    await waitFor(() => {
      expect(bindGolemPolicyGroupMock).toHaveBeenCalledWith('golem_2', 'pg_1');
    });

    fireEvent.change(screen.getByLabelText(/draft spec json/i), {
      target: {
        value: JSON.stringify(
          {
            schemaVersion: 1,
            llmProviders: {
              openai: {
                baseUrl: 'https://api.example.com/openai',
                requestTimeoutSeconds: 45,
                apiType: 'openai',
              },
            },
            modelRouter: {
              temperature: 0.4,
              routing: {
                model: 'openai/gpt-5.1',
                reasoning: 'medium',
              },
              tiers: {
                balanced: {
                  model: 'openai/gpt-5.1',
                  reasoning: 'medium',
                },
              },
              dynamicTierEnabled: true,
            },
            modelCatalog: {
              defaultModel: 'openai/gpt-5.1',
              models: {
                'openai/gpt-5.1': {
                  provider: 'openai',
                  displayName: 'openai/gpt-5.1',
                  supportsVision: true,
                  supportsTemperature: true,
                  maxInputTokens: 200000,
                },
              },
            },
            tools: {
              filesystemEnabled: true,
              shellEnabled: false,
              skillManagementEnabled: true,
              skillTransitionEnabled: true,
              tierEnabled: true,
              goalManagementEnabled: true,
              shellEnvironmentVariables: [{ name: 'GITHUB_TOKEN', value: '' }],
            },
            memory: {
              enabled: true,
              disclosure: {
                mode: 'summary',
              },
            },
            mcp: {
              enabled: true,
              catalog: [
                {
                  name: 'github',
                  env: {
                    GITHUB_TOKEN: '',
                  },
                },
              ],
            },
            autonomy: {
              enabled: true,
            },
          },
          null,
          2,
        ),
      },
    });
    fireEvent.click(screen.getByRole('button', { name: /save draft/i }));

    await waitFor(() => {
      expect(updatePolicyGroupDraftMock).toHaveBeenCalledWith(
        'pg_1',
        expect.objectContaining({
          llmProviders: expect.objectContaining({
            openai: expect.objectContaining({
              requestTimeoutSeconds: 45,
            }),
          }),
          tools: expect.objectContaining({
            shellEnabled: false,
            shellEnvironmentVariables: [expect.objectContaining({ name: 'GITHUB_TOKEN', value: '' })],
          }),
          memory: expect.objectContaining({
            disclosure: expect.objectContaining({ mode: 'summary' }),
          }),
          mcp: expect.objectContaining({
            catalog: [
              expect.objectContaining({
                name: 'github',
                env: expect.objectContaining({ GITHUB_TOKEN: '' }),
              }),
            ],
          }),
          autonomy: expect.objectContaining({
            enabled: true,
          }),
        }),
      );
    });

    fireEvent.click(screen.getByRole('button', { name: /rollback to v1/i }));

    await waitFor(() => {
      expect(rollbackPolicyGroupMock).toHaveBeenCalledWith('pg_1', 1, expect.any(String));
    });

    fireEvent.click(screen.getByRole('button', { name: /detach atlas/i }));

    await waitFor(() => {
      expect(unbindGolemPolicyGroupMock).toHaveBeenCalledWith('golem_1', expect.anything());
    });
  });
});

function renderPage(initialEntry: string) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialEntry]}>
        <Routes>
          <Route path="/policies">
            <Route index element={<PolicyGroupsPage />} />
            <Route path=":groupId" element={<PolicyGroupsPage />} />
          </Route>
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}
