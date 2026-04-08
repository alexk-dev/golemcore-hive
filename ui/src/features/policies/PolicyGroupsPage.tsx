import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { listGolems } from '../../lib/api/golemsApi';
import {
  bindGolemPolicyGroup,
  createPolicyGroup,
  getPolicyGroup,
  listPolicyGroupVersions,
  listPolicyGroups,
  publishPolicyGroup,
  rollbackPolicyGroup,
  type PolicyDraftSpec,
  type PolicyGroupSpecResponse,
  unbindGolemPolicyGroup,
  updatePolicyGroupDraft,
} from '../../lib/api/policiesApi';
import { formatTimestamp } from '../../lib/format';

const EMPTY_DRAFT_SPEC: PolicyDraftSpec = {
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

export function PolicyGroupsPage() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const { groupId } = useParams<{ groupId: string }>();
  const [createForm, setCreateForm] = useState({
    slug: '',
    name: '',
    description: '',
  });
  const [draftEditorValue, setDraftEditorValue] = useState(JSON.stringify(EMPTY_DRAFT_SPEC, null, 2));
  const [draftEditorError, setDraftEditorError] = useState<string | null>(null);
  const [publishSummary, setPublishSummary] = useState('');
  const [selectedAttachGolemId, setSelectedAttachGolemId] = useState('');

  const policiesQuery = useQuery({
    queryKey: ['policy-groups'],
    queryFn: listPolicyGroups,
  });
  const golemsQuery = useQuery({
    queryKey: ['golems'],
    queryFn: () => listGolems(),
  });

  const selectedGroupId = groupId ?? policiesQuery.data?.[0]?.id ?? null;

  const policyQuery = useQuery({
    queryKey: ['policy-group', selectedGroupId],
    queryFn: () => getPolicyGroup(selectedGroupId ?? ''),
    enabled: Boolean(selectedGroupId),
  });
  const versionsQuery = useQuery({
    queryKey: ['policy-group-versions', selectedGroupId],
    queryFn: () => listPolicyGroupVersions(selectedGroupId ?? ''),
    enabled: Boolean(selectedGroupId),
  });

  useEffect(() => {
    if (!groupId && policiesQuery.data?.length) {
      void navigate(`/policies/${policiesQuery.data[0].id}`, { replace: true });
    }
  }, [groupId, navigate, policiesQuery.data]);

  useEffect(() => {
    const nextValue = JSON.stringify(toEditableDraft(policyQuery.data?.draftSpec), null, 2);
    setDraftEditorValue(nextValue);
    setDraftEditorError(null);
  }, [policyQuery.data?.id, policyQuery.data?.draftSpec]);

  const createPolicyMutation = useMutation({
    mutationFn: createPolicyGroup,
    onSuccess: async (createdPolicy) => {
      await queryClient.invalidateQueries({ queryKey: ['policy-groups'] });
      setCreateForm({ slug: '', name: '', description: '' });
      void navigate(`/policies/${createdPolicy.id}`);
    },
  });
  const updateDraftMutation = useMutation({
    mutationFn: async (draftSpec: PolicyDraftSpec) => {
      if (!selectedGroupId) {
        return null;
      }
      return updatePolicyGroupDraft(selectedGroupId, draftSpec);
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['policy-group', selectedGroupId] }),
        queryClient.invalidateQueries({ queryKey: ['policy-groups'] }),
      ]);
    },
  });
  const publishMutation = useMutation({
    mutationFn: async (changeSummary: string) => {
      if (!selectedGroupId) {
        return null;
      }
      return publishPolicyGroup(selectedGroupId, changeSummary);
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['policy-group', selectedGroupId] }),
        queryClient.invalidateQueries({ queryKey: ['policy-group-versions', selectedGroupId] }),
        queryClient.invalidateQueries({ queryKey: ['policy-groups'] }),
        queryClient.invalidateQueries({ queryKey: ['golems'] }),
      ]);
      setPublishSummary('');
    },
  });
  const rollbackMutation = useMutation({
    mutationFn: async (version: number) => {
      if (!selectedGroupId) {
        return null;
      }
      return rollbackPolicyGroup(selectedGroupId, version, `Rollback to v${version} from Hive UI`);
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['policy-group', selectedGroupId] }),
        queryClient.invalidateQueries({ queryKey: ['policy-group-versions', selectedGroupId] }),
        queryClient.invalidateQueries({ queryKey: ['policy-groups'] }),
        queryClient.invalidateQueries({ queryKey: ['golems'] }),
      ]);
    },
  });
  const bindMutation = useMutation({
    mutationFn: async (golemId: string) => {
      if (!selectedGroupId) {
        return null;
      }
      return bindGolemPolicyGroup(golemId, selectedGroupId);
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['golems'] }),
        queryClient.invalidateQueries({ queryKey: ['policy-group', selectedGroupId] }),
        queryClient.invalidateQueries({ queryKey: ['policy-groups'] }),
      ]);
      setSelectedAttachGolemId('');
    },
  });
  const unbindMutation = useMutation({
    mutationFn: unbindGolemPolicyGroup,
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['golems'] }),
        queryClient.invalidateQueries({ queryKey: ['policy-group', selectedGroupId] }),
        queryClient.invalidateQueries({ queryKey: ['policy-groups'] }),
      ]);
    },
  });

  const selectedPolicy = policyQuery.data ?? null;
  const allGolems = golemsQuery.data ?? [];
  const boundGolems = selectedGroupId
    ? allGolems.filter((golem) => golem.policyBinding?.policyGroupId === selectedGroupId)
    : [];

  return (
    <div className="grid gap-5 xl:grid-cols-[320px_minmax(0,1fr)]">
      <aside className="grid gap-4">
        <section className="panel p-4">
          <div className="flex items-center justify-between gap-3">
            <div>
              <h1 className="text-lg font-bold tracking-tight text-foreground">Policy groups</h1>
              <p className="mt-1 text-sm text-muted-foreground">
                Centralize model routing, provider settings, and rollout authority for the fleet.
              </p>
            </div>
            <span className="pill">{policiesQuery.data?.length ?? 0} total</span>
          </div>
        </section>

        <section className="panel p-4">
          <h2 className="text-sm font-bold text-foreground">Create policy group</h2>
          <form
            className="mt-3 grid gap-3"
            onSubmit={(event) => {
              event.preventDefault();
              void createPolicyMutation.mutateAsync({
                slug: createForm.slug.trim(),
                name: createForm.name.trim(),
                description: createForm.description.trim() || undefined,
              });
            }}
          >
            <label className="grid gap-1">
              <span className="text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">Slug</span>
              <input
                value={createForm.slug}
                onChange={(event) => setCreateForm((current) => ({ ...current, slug: event.target.value }))}
                className="border border-border bg-white/90 px-3 py-2 text-sm outline-none transition focus:border-primary"
                placeholder="default-routing"
              />
            </label>
            <label className="grid gap-1">
              <span className="text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">Name</span>
              <input
                value={createForm.name}
                onChange={(event) => setCreateForm((current) => ({ ...current, name: event.target.value }))}
                className="border border-border bg-white/90 px-3 py-2 text-sm outline-none transition focus:border-primary"
                placeholder="Default Routing"
              />
            </label>
            <label className="grid gap-1">
              <span className="text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
                Description
              </span>
              <textarea
                value={createForm.description}
                onChange={(event) => setCreateForm((current) => ({ ...current, description: event.target.value }))}
                rows={2}
                className="border border-border bg-white/90 px-3 py-2 text-sm outline-none transition focus:border-primary"
                placeholder="Primary policy for engineering golems"
              />
            </label>
            <button
              type="submit"
              disabled={createPolicyMutation.isPending || !createForm.slug.trim() || !createForm.name.trim()}
              className="bg-foreground px-3 py-2 text-sm font-semibold text-white disabled:opacity-60"
            >
              {createPolicyMutation.isPending ? 'Creating…' : 'Create policy group'}
            </button>
          </form>
        </section>

        <section className="panel p-4">
          <div className="flex items-center justify-between gap-3">
            <h2 className="text-sm font-bold text-foreground">Policies</h2>
            <span className="text-xs text-muted-foreground">{policiesQuery.data?.length ?? 0} listed</span>
          </div>
          <div className="mt-3 grid gap-2">
            {(policiesQuery.data ?? []).map((policy) => {
              const rollout = summarizeRollout(policy.id, allGolems);
              const selected = policy.id === selectedGroupId;
              return (
                <Link
                  key={policy.id}
                  to={`/policies/${policy.id}`}
                  className={[
                    'border px-3 py-3 text-left transition',
                    selected ? 'border-foreground bg-foreground text-white' : 'border-border/70 bg-white/80 hover:bg-white',
                  ].join(' ')}
                >
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <p className="truncate text-sm font-semibold">{policy.name}</p>
                      <p className={selected ? 'text-xs text-white/70' : 'text-xs text-muted-foreground'}>
                        {policy.slug} · v{policy.currentVersion || 0}
                      </p>
                    </div>
                    <span
                      className={[
                        'rounded-full border px-2 py-0.5 text-[10px] font-semibold uppercase tracking-[0.16em]',
                        selected ? 'border-white/20 text-white/80' : 'border-border/70 text-muted-foreground',
                      ].join(' ')}
                    >
                      {policy.status}
                    </span>
                  </div>
                  <div className={selected ? 'mt-3 grid gap-1 text-xs text-white/80' : 'mt-3 grid gap-1 text-xs text-muted-foreground'}>
                    <div className="flex items-center justify-between gap-3">
                      <span>Bound</span>
                      <span>{rollout.total}</span>
                    </div>
                    <div className="flex items-center justify-between gap-3">
                      <span>In sync</span>
                      <span>{rollout.inSync}</span>
                    </div>
                    <div className="flex items-center justify-between gap-3">
                      <span>Drifted</span>
                      <span>{rollout.outOfSync + rollout.applyFailed}</span>
                    </div>
                  </div>
                </Link>
              );
            })}
            {!policiesQuery.data?.length ? (
              <p className="text-sm text-muted-foreground">No policy groups yet. Create one to start centralizing model control.</p>
            ) : null}
          </div>
        </section>
      </aside>

      <div className="grid gap-4">
        {selectedPolicy ? (
          <>
            <section className="panel p-5">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <p className="text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground">
                    {selectedPolicy.slug}
                  </p>
                  <h2 className="mt-1 text-xl font-bold tracking-tight text-foreground">{selectedPolicy.name}</h2>
                  <p className="mt-2 max-w-3xl text-sm text-muted-foreground">
                    {selectedPolicy.description || 'No description set yet.'}
                  </p>
                </div>
                <div className="grid gap-1 text-right text-xs text-muted-foreground">
                  <span>Published {formatTimestamp(selectedPolicy.lastPublishedAt)}</span>
                  <span>Updated {formatTimestamp(selectedPolicy.updatedAt)}</span>
                </div>
              </div>

              <div className="mt-4 grid gap-3 md:grid-cols-4">
                <MetricCard label="Current version" value={`v${selectedPolicy.currentVersion || 0}`} />
                <MetricCard label="Bound golems" value={String(boundGolems.length)} />
                <MetricCard label="In sync" value={String(summarizeRollout(selectedPolicy.id, allGolems).inSync)} />
                <MetricCard
                  label="Needs attention"
                  value={String(
                    summarizeRollout(selectedPolicy.id, allGolems).outOfSync
                      + summarizeRollout(selectedPolicy.id, allGolems).applyFailed
                      + summarizeRollout(selectedPolicy.id, allGolems).syncPending,
                  )}
                />
              </div>
            </section>

            <section className="grid gap-4 2xl:grid-cols-[minmax(0,1.1fr)_minmax(320px,0.9fr)]">
              <section className="panel p-5">
                <div className="flex items-center justify-between gap-3">
                  <div>
                    <h3 className="text-base font-bold tracking-tight text-foreground">Draft spec</h3>
                    <p className="mt-1 text-sm text-muted-foreground">
                      Raw policy package editor. Existing provider secrets stay preserved unless you explicitly set a new
                      `apiKey`.
                    </p>
                  </div>
                  <button
                    type="button"
                    disabled={updateDraftMutation.isPending}
                    onClick={async () => {
                      try {
                        const parsed = JSON.parse(draftEditorValue) as PolicyDraftSpec;
                        setDraftEditorError(null);
                        await updateDraftMutation.mutateAsync(parsed);
                      } catch (error) {
                        setDraftEditorError(error instanceof Error ? error.message : 'Invalid JSON payload');
                      }
                    }}
                    className="bg-foreground px-3 py-1.5 text-sm font-semibold text-white disabled:opacity-60"
                  >
                    {updateDraftMutation.isPending ? 'Saving…' : 'Save draft'}
                  </button>
                </div>

                <SecretStatusPanel spec={selectedPolicy.draftSpec} />

                <label className="mt-4 grid gap-2">
                  <span className="text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
                    Draft spec JSON
                  </span>
                  <textarea
                    aria-label="Draft spec JSON"
                    value={draftEditorValue}
                    onChange={(event) => {
                      setDraftEditorValue(event.target.value);
                      if (draftEditorError) {
                        setDraftEditorError(null);
                      }
                    }}
                    rows={26}
                    className="min-h-[520px] border border-border bg-white px-3 py-3 font-mono text-xs leading-6 text-foreground outline-none transition focus:border-primary"
                  />
                </label>
                {draftEditorError ? (
                  <p className="mt-3 border border-rose-300 bg-rose-100 px-3 py-2 text-sm text-rose-900">
                    {draftEditorError}
                  </p>
                ) : null}
              </section>

              <div className="grid gap-4">
                <section className="panel p-5">
                  <h3 className="text-base font-bold tracking-tight text-foreground">Publish</h3>
                  <p className="mt-1 text-sm text-muted-foreground">
                    Push the current draft into an immutable version and mark every attached golem for sync.
                  </p>
                  <label className="mt-4 grid gap-2">
                    <span className="text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
                      Change summary
                    </span>
                    <input
                      value={publishSummary}
                      onChange={(event) => setPublishSummary(event.target.value)}
                      className="border border-border bg-white/90 px-3 py-2 text-sm outline-none transition focus:border-primary"
                      placeholder="Explain what changed in this release"
                    />
                  </label>
                  <button
                    type="button"
                    disabled={publishMutation.isPending}
                    onClick={async () => {
                      await publishMutation.mutateAsync(publishSummary.trim() || 'Published from Hive UI');
                    }}
                    className="mt-4 bg-accent px-3 py-2 text-sm font-semibold text-accent-foreground disabled:opacity-60"
                  >
                    {publishMutation.isPending ? 'Publishing…' : 'Publish version'}
                  </button>
                </section>

                <section className="panel p-5">
                  <div className="flex items-center justify-between gap-3">
                    <h3 className="text-base font-bold tracking-tight text-foreground">Versions</h3>
                    <span className="text-xs text-muted-foreground">{versionsQuery.data?.length ?? 0} stored</span>
                  </div>
                  <div className="mt-4 grid gap-3">
                    {(versionsQuery.data ?? []).map((version) => (
                      <article key={version.version} className="border border-border/70 bg-white/80 p-4">
                        <div className="flex items-start justify-between gap-3">
                          <div>
                            <p className="text-sm font-semibold text-foreground">Version {version.version}</p>
                            <p className="mt-1 text-xs text-muted-foreground">
                              {version.changeSummary || 'No summary'} · {formatTimestamp(version.publishedAt)}
                            </p>
                            <p className="mt-2 text-xs text-muted-foreground">
                              Providers {Object.keys(version.specSnapshot?.llmProviders ?? {}).join(', ') || '—'}
                              {' · '}Default model {version.specSnapshot?.modelCatalog?.defaultModel || '—'}
                            </p>
                          </div>
                          {version.version !== selectedPolicy.currentVersion ? (
                            <button
                              type="button"
                              onClick={async () => {
                                await rollbackMutation.mutateAsync(version.version);
                              }}
                              disabled={rollbackMutation.isPending}
                              className="border border-border bg-white px-3 py-1.5 text-xs font-semibold text-foreground disabled:opacity-60"
                            >
                              Rollback to v{version.version}
                            </button>
                          ) : (
                            <span className="pill">current</span>
                          )}
                        </div>
                      </article>
                    ))}
                  </div>
                </section>
              </div>
            </section>

            <section className="panel p-5">
              <div className="flex flex-wrap items-end justify-between gap-4">
                <div>
                  <h3 className="text-base font-bold tracking-tight text-foreground">Bound golems</h3>
                  <p className="mt-1 text-sm text-muted-foreground">
                    Attach a golem to make Hive authoritative for its model routing package.
                  </p>
                </div>
                <div className="grid gap-2 sm:grid-cols-[220px_auto]">
                  <label className="grid gap-1">
                    <span className="text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
                      Attach golem
                    </span>
                    <select
                      aria-label="Attach golem"
                      value={selectedAttachGolemId}
                      onChange={(event) => setSelectedAttachGolemId(event.target.value)}
                      className="border border-border bg-white/90 px-3 py-2 text-sm outline-none transition focus:border-primary"
                    >
                      <option value="">Choose a golem</option>
                      {allGolems.map((golem) => (
                        <option key={golem.id} value={golem.id}>
                          {golem.displayName}
                        </option>
                      ))}
                    </select>
                  </label>
                  <button
                    type="button"
                    disabled={bindMutation.isPending || !selectedAttachGolemId}
                    onClick={async () => {
                      if (!selectedAttachGolemId) {
                        return;
                      }
                      await bindMutation.mutateAsync(selectedAttachGolemId);
                    }}
                    className="h-[42px] self-end bg-foreground px-3 py-2 text-sm font-semibold text-white disabled:opacity-60"
                  >
                    {bindMutation.isPending ? 'Attaching…' : 'Attach selected'}
                  </button>
                </div>
              </div>

              {boundGolems.length ? (
                <div className="mt-4 overflow-x-auto border border-border/70">
                  <table className="min-w-full divide-y divide-border/70 text-sm">
                    <thead className="bg-muted/40 text-left text-xs uppercase tracking-[0.16em] text-muted-foreground">
                      <tr>
                        <th className="px-3 py-2 font-semibold">Golem</th>
                        <th className="px-3 py-2 font-semibold">Versions</th>
                        <th className="px-3 py-2 font-semibold">Sync</th>
                        <th className="px-3 py-2 font-semibold">Last error</th>
                        <th className="px-3 py-2 font-semibold">Actions</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-border/60 bg-white/80">
                      {boundGolems.map((golem) => (
                        <tr key={golem.id}>
                          <td className="px-3 py-3">
                            <div>
                              <p className="font-semibold text-foreground">{golem.displayName}</p>
                              <p className="text-xs text-muted-foreground">{golem.hostLabel || golem.id}</p>
                            </div>
                          </td>
                          <td className="px-3 py-3 text-muted-foreground">
                            {formatVersionPair(
                              golem.policyBinding?.targetVersion ?? null,
                              golem.policyBinding?.appliedVersion ?? null,
                            )}
                          </td>
                          <td className="px-3 py-3">
                            <span className={syncBadgeClassName(golem.policyBinding?.syncStatus)}>{golem.policyBinding?.syncStatus || 'UNBOUND'}</span>
                          </td>
                          <td className="px-3 py-3 text-xs text-muted-foreground">
                            {golem.policyBinding?.lastErrorDigest || '—'}
                          </td>
                          <td className="px-3 py-3">
                            <button
                              type="button"
                              aria-label={`Detach ${golem.displayName}`}
                              onClick={async () => {
                                await unbindMutation.mutateAsync(golem.id);
                              }}
                              disabled={unbindMutation.isPending}
                              className="border border-border bg-white px-3 py-1.5 text-xs font-semibold text-foreground disabled:opacity-60"
                            >
                              Detach
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <p className="mt-4 text-sm text-muted-foreground">No golems are bound to this policy group yet.</p>
              )}
            </section>
          </>
        ) : (
          <section className="panel p-8 text-sm text-muted-foreground">
            Select a policy group to inspect its draft, versions, and rollout health.
          </section>
        )}
      </div>
    </div>
  );
}

function MetricCard({ label, value }: { label: string; value: string }) {
  return (
    <article className="border border-border/70 bg-white/80 p-4">
      <p className="text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">{label}</p>
      <p className="mt-2 text-2xl font-bold tracking-tight text-foreground">{value}</p>
    </article>
  );
}

function SecretStatusPanel({ spec }: { spec: PolicyGroupSpecResponse | null }) {
  const providerEntries = Object.entries(spec?.llmProviders ?? {});

  return (
    <div className="mt-4 grid gap-2 md:grid-cols-2">
      {providerEntries.length ? (
        providerEntries.map(([providerKey, provider]) => (
          <div key={providerKey} className="border border-border/70 bg-white/80 px-3 py-2 text-xs text-muted-foreground">
            <p className="font-semibold text-foreground">{providerKey}</p>
            <p className="mt-1">
              Secret {provider.apiKeyPresent ? 'present' : 'missing'} · {provider.apiType || 'unknown type'}
            </p>
          </div>
        ))
      ) : (
        <div className="border border-border/70 bg-white/80 px-3 py-2 text-xs text-muted-foreground">
          No providers configured in the current draft.
        </div>
      )}
    </div>
  );
}

function summarizeRollout(
  policyGroupId: string,
  golems: Array<{ policyBinding?: { policyGroupId: string; syncStatus: string | null } | null }>,
) {
  const summary = {
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

function toEditableDraft(spec: PolicyGroupSpecResponse | null | undefined): PolicyDraftSpec {
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

function formatVersionPair(targetVersion: number | null, appliedVersion: number | null) {
  if (!targetVersion) {
    return '—';
  }
  return `Target v${targetVersion} · Applied ${appliedVersion ? `v${appliedVersion}` : '—'}`;
}

function syncBadgeClassName(syncStatus: string | null | undefined) {
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
