import type { GolemSummary } from '../../lib/api/golemsApi';
import type {
  PolicyAutonomyConfigResponse,
  PolicyDraftAutonomyConfig,
  PolicyDraftMcpCatalogEntry,
  PolicyDraftMcpConfig,
  PolicyDraftMemoryConfig,
  PolicyDraftSpec,
  PolicyDraftToolsConfig,
  PolicyGroupSpecResponse,
  PolicyMcpConfigResponse,
  PolicyMemoryConfigResponse,
  PolicyToolsConfigResponse,
} from '../../lib/api/policiesApi';

export const EMPTY_DRAFT_SPEC: PolicyDraftSpec = {
  schemaVersion: 1,
  llmProviders: {},
  modelRouter: {
    temperature: 0.7,
    routing: null,
    tiers: {},
    dynamicTierEnabled: true,
  },
  modelCatalog: {
    defaultModel: null,
    models: {},
  },
  tools: {
    filesystemEnabled: true,
    shellEnabled: true,
    skillManagementEnabled: true,
    skillTransitionEnabled: true,
    tierEnabled: true,
    goalManagementEnabled: true,
    shellEnvironmentVariables: [],
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
    catalog: [],
  },
  autonomy: {
    enabled: false,
    tickIntervalSeconds: 1,
    taskTimeLimitMinutes: 10,
    autoStart: true,
    maxGoals: 3,
    modelTier: 'balanced',
    reflectionEnabled: true,
    reflectionFailureThreshold: 2,
    reflectionModelTier: null,
    reflectionTierPriority: null,
    notifyMilestones: true,
  },
};

export interface PolicyRolloutSummary {
  total: number;
  inSync: number;
  syncPending: number;
  applyFailed: number;
  outOfSync: number;
}

export function summarizeRollout(policyGroupId: string, golems: GolemSummary[]): PolicyRolloutSummary {
  const summary: PolicyRolloutSummary = {
    total: 0,
    inSync: 0,
    syncPending: 0,
    applyFailed: 0,
    outOfSync: 0,
  };

  for (const golem of golems) {
    if (golem.policyBinding?.policyGroupId !== policyGroupId) {
      continue;
    }
    summary.total += 1;
    if (golem.policyBinding.syncStatus === 'IN_SYNC') {
      summary.inSync += 1;
    } else if (golem.policyBinding.syncStatus === 'SYNC_PENDING') {
      summary.syncPending += 1;
    } else if (golem.policyBinding.syncStatus === 'APPLY_FAILED') {
      summary.applyFailed += 1;
    } else {
      summary.outOfSync += 1;
    }
  }

  return summary;
}

export function toEditableDraft(spec: PolicyGroupSpecResponse | null | undefined): PolicyDraftSpec {
  if (!spec) {
    return EMPTY_DRAFT_SPEC;
  }

  const llmProviders = Object.fromEntries(
    Object.entries(spec.llmProviders ?? {}).map(([providerKey, provider]) => [
      providerKey,
      {
        baseUrl: provider.baseUrl,
        requestTimeoutSeconds: provider.requestTimeoutSeconds,
        apiType: provider.apiType,
        legacyApi: provider.legacyApi,
      },
    ]),
  );

  return {
    schemaVersion: spec.schemaVersion ?? 1,
    llmProviders,
    modelRouter: spec.modelRouter
      ? {
          temperature: spec.modelRouter.temperature,
          routing: spec.modelRouter.routing
            ? {
                model: spec.modelRouter.routing.model,
                reasoning: spec.modelRouter.routing.reasoning,
              }
            : null,
          tiers: Object.fromEntries(
            Object.entries(spec.modelRouter.tiers ?? {}).map(([tierKey, tier]) => [
              tierKey,
              {
                model: tier.model,
                reasoning: tier.reasoning,
              },
            ]),
          ),
          dynamicTierEnabled: spec.modelRouter.dynamicTierEnabled,
        }
      : EMPTY_DRAFT_SPEC.modelRouter,
    modelCatalog: spec.modelCatalog
      ? {
          defaultModel: spec.modelCatalog.defaultModel,
          models: Object.fromEntries(
            Object.entries(spec.modelCatalog.models ?? {}).map(([modelKey, model]) => [
              modelKey,
              {
                provider: model.provider,
                displayName: model.displayName,
                supportsVision: model.supportsVision,
                supportsTemperature: model.supportsTemperature,
                maxInputTokens: model.maxInputTokens,
              },
            ]),
          ),
        }
      : EMPTY_DRAFT_SPEC.modelCatalog,
    tools: toEditableTools(spec.tools),
    memory: toEditableMemory(spec.memory),
    mcp: toEditableMcp(spec.mcp),
    autonomy: toEditableAutonomy(spec.autonomy),
  };
}

function toEditableTools(tools: PolicyToolsConfigResponse | null | undefined): PolicyDraftToolsConfig | null {
  if (!tools) {
    return EMPTY_DRAFT_SPEC.tools;
  }
  return {
    filesystemEnabled: tools.filesystemEnabled,
    shellEnabled: tools.shellEnabled,
    skillManagementEnabled: tools.skillManagementEnabled,
    skillTransitionEnabled: tools.skillTransitionEnabled,
    tierEnabled: tools.tierEnabled,
    goalManagementEnabled: tools.goalManagementEnabled,
    shellEnvironmentVariables: (tools.shellEnvironmentVariables ?? []).map((variable) => ({
      name: variable.name,
      value: variable.valuePresent ? '' : null,
    })),
  };
}

function toEditableMemory(memory: PolicyMemoryConfigResponse | null | undefined): PolicyDraftMemoryConfig | null {
  if (!memory) {
    return EMPTY_DRAFT_SPEC.memory;
  }
  return {
    version: memory.version,
    enabled: memory.enabled,
    softPromptBudgetTokens: memory.softPromptBudgetTokens,
    maxPromptBudgetTokens: memory.maxPromptBudgetTokens,
    workingTopK: memory.workingTopK,
    episodicTopK: memory.episodicTopK,
    semanticTopK: memory.semanticTopK,
    proceduralTopK: memory.proceduralTopK,
    promotionEnabled: memory.promotionEnabled,
    promotionMinConfidence: memory.promotionMinConfidence,
    decayEnabled: memory.decayEnabled,
    decayDays: memory.decayDays,
    retrievalLookbackDays: memory.retrievalLookbackDays,
    codeAwareExtractionEnabled: memory.codeAwareExtractionEnabled,
    disclosure: memory.disclosure
      ? {
          mode: memory.disclosure.mode,
          promptStyle: memory.disclosure.promptStyle,
          toolExpansionEnabled: memory.disclosure.toolExpansionEnabled,
          disclosureHintsEnabled: memory.disclosure.disclosureHintsEnabled,
          detailMinScore: memory.disclosure.detailMinScore,
        }
      : null,
    reranking: memory.reranking
      ? {
          enabled: memory.reranking.enabled,
          profile: memory.reranking.profile,
        }
      : null,
    diagnostics: memory.diagnostics
      ? {
          verbosity: memory.diagnostics.verbosity,
        }
      : null,
  };
}

function toEditableMcp(mcp: PolicyMcpConfigResponse | null | undefined): PolicyDraftMcpConfig | null {
  if (!mcp) {
    return EMPTY_DRAFT_SPEC.mcp;
  }
  return {
    enabled: mcp.enabled,
    defaultStartupTimeout: mcp.defaultStartupTimeout,
    defaultIdleTimeout: mcp.defaultIdleTimeout,
    catalog: (mcp.catalog ?? []).map(toEditableMcpCatalogEntry),
  };
}

function toEditableMcpCatalogEntry(entry: PolicyMcpConfigResponse['catalog'][number]): PolicyDraftMcpCatalogEntry {
  return {
    name: entry.name,
    description: entry.description,
    command: entry.command,
    env: Object.fromEntries(Object.keys(entry.envPresent ?? {}).map((envKey) => [envKey, ''])),
    startupTimeoutSeconds: entry.startupTimeoutSeconds,
    idleTimeoutMinutes: entry.idleTimeoutMinutes,
    enabled: entry.enabled,
  };
}

function toEditableAutonomy(
  autonomy: PolicyAutonomyConfigResponse | null | undefined,
): PolicyDraftAutonomyConfig | null {
  if (!autonomy) {
    return EMPTY_DRAFT_SPEC.autonomy;
  }
  return {
    enabled: autonomy.enabled,
    tickIntervalSeconds: autonomy.tickIntervalSeconds,
    taskTimeLimitMinutes: autonomy.taskTimeLimitMinutes,
    autoStart: autonomy.autoStart,
    maxGoals: autonomy.maxGoals,
    modelTier: autonomy.modelTier,
    reflectionEnabled: autonomy.reflectionEnabled,
    reflectionFailureThreshold: autonomy.reflectionFailureThreshold,
    reflectionModelTier: autonomy.reflectionModelTier,
    reflectionTierPriority: autonomy.reflectionTierPriority,
    notifyMilestones: autonomy.notifyMilestones,
  };
}

export function formatVersionPair(targetVersion: number | null, appliedVersion: number | null) {
  if (!targetVersion) {
    return '—';
  }
  return `Target v${targetVersion} · Applied ${appliedVersion ? `v${appliedVersion}` : '—'}`;
}

export function syncBadgeClassName(syncStatus: string | null | undefined) {
  if (syncStatus === 'IN_SYNC') {
    return 'inline-flex items-center border border-emerald-300 bg-emerald-100 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-[0.16em] text-emerald-900';
  }
  if (syncStatus === 'SYNC_PENDING') {
    return 'inline-flex items-center border border-amber-300 bg-amber-100 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-[0.16em] text-amber-900';
  }
  if (syncStatus === 'APPLY_FAILED' || syncStatus === 'OUT_OF_SYNC') {
    return 'inline-flex items-center border border-rose-300 bg-rose-100 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-[0.16em] text-rose-900';
  }
  return 'inline-flex items-center border border-border/70 bg-panel/80 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-[0.16em] text-muted-foreground';
}
