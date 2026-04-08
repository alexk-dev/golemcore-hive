import type { GolemSummary } from '../../lib/api/golemsApi';
import type { PolicyDraftSpec, PolicyGroupSpecResponse } from '../../lib/api/policiesApi';

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
  return 'inline-flex items-center border border-border/70 bg-white/80 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-[0.16em] text-muted-foreground';
}
