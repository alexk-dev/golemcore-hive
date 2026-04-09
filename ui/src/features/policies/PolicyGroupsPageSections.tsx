import type { FormEvent } from 'react';
import { Link } from 'react-router-dom';
import type { GolemSummary } from '../../lib/api/golemsApi';
import type { PolicyGroup, PolicyGroupSpecResponse } from '../../lib/api/policiesApi';
import { formatTimestamp } from '../../lib/format';
import { type PolicyRolloutSummary, summarizeRollout } from './PolicyGroupsPageSupport';

interface CreatePolicyFormState {
  slug: string;
  name: string;
  description: string;
}

export function PolicyGroupsSidebar({
  policies,
  allGolems,
  selectedGroupId,
  createForm,
  isCreating,
  onCreateFieldChange,
  onCreateSubmit,
}: {
  policies: PolicyGroup[];
  allGolems: GolemSummary[];
  selectedGroupId: string | null;
  createForm: CreatePolicyFormState;
  isCreating: boolean;
  onCreateFieldChange: (field: keyof CreatePolicyFormState, value: string) => void;
  onCreateSubmit: (event: FormEvent<HTMLFormElement>) => void;
}) {
  return (
    <aside className="grid gap-4">
      <section className="panel p-4">
        <div className="flex items-center justify-between gap-3">
          <div>
            <h1 className="text-lg font-bold tracking-tight text-foreground">Policy groups</h1>
            <p className="mt-1 text-sm text-muted-foreground">
              Centralize model routing, provider settings, and rollout authority for the fleet.
            </p>
          </div>
          <span className="pill">{policies.length} total</span>
        </div>
      </section>

      <section className="panel p-4">
        <h2 className="text-sm font-bold text-foreground">Create policy group</h2>
        <form className="mt-3 grid gap-3" onSubmit={onCreateSubmit}>
          <label className="grid gap-1">
            <span className="text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">Slug</span>
            <input
              value={createForm.slug}
              onChange={(event) => onCreateFieldChange('slug', event.target.value)}
              className="border border-border bg-panel/90 px-3 py-2 text-sm outline-none transition focus:border-primary"
              placeholder="default-routing"
            />
          </label>
          <label className="grid gap-1">
            <span className="text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">Name</span>
            <input
              value={createForm.name}
              onChange={(event) => onCreateFieldChange('name', event.target.value)}
              className="border border-border bg-panel/90 px-3 py-2 text-sm outline-none transition focus:border-primary"
              placeholder="Default Routing"
            />
          </label>
          <label className="grid gap-1">
            <span className="text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">Description</span>
            <textarea
              value={createForm.description}
              onChange={(event) => onCreateFieldChange('description', event.target.value)}
              rows={2}
              className="border border-border bg-panel/90 px-3 py-2 text-sm outline-none transition focus:border-primary"
              placeholder="Primary policy for engineering golems"
            />
          </label>
          <button
            type="submit"
            disabled={isCreating || !createForm.slug.trim() || !createForm.name.trim()}
            className="bg-primary px-3 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-60"
          >
            {isCreating ? 'Creating…' : 'Create policy group'}
          </button>
        </form>
      </section>

      <section className="panel p-4">
        <div className="flex items-center justify-between gap-3">
          <h2 className="text-sm font-bold text-foreground">Policies</h2>
          <span className="text-xs text-muted-foreground">{policies.length} listed</span>
        </div>
        <div className="mt-3 grid gap-2">
          {policies.length ? (
            policies.map((policy) => (
              <PolicyGroupListItem
                key={policy.id}
                policy={policy}
                rollout={summarizeRollout(policy.id, allGolems)}
                selected={policy.id === selectedGroupId}
              />
            ))
          ) : (
            <p className="text-sm text-muted-foreground">
              No policy groups yet. Create one to start centralizing model control.
            </p>
          )}
        </div>
      </section>
    </aside>
  );
}

function PolicyGroupListItem({
  policy,
  rollout,
  selected,
}: {
  policy: PolicyGroup;
  rollout: PolicyRolloutSummary;
  selected: boolean;
}) {
  const containerClassName = selected
    ? 'border-foreground bg-primary text-primary-foreground'
    : 'border-border/70 bg-panel/80 hover:bg-muted';
  const labelClassName = selected ? 'text-xs text-foreground/70' : 'text-xs text-muted-foreground';
  const badgeClassName = selected ? 'border-foreground/20 text-foreground/80' : 'border-border/70 text-muted-foreground';
  const metricsClassName = selected ? 'mt-3 grid gap-1 text-xs text-foreground/80' : 'mt-3 grid gap-1 text-xs text-muted-foreground';

  return (
    <Link to={`/policies/${policy.id}`} className={['border px-3 py-3 text-left transition', containerClassName].join(' ')}>
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold">{policy.name}</p>
          <p className={labelClassName}>
            {policy.slug} · v{policy.currentVersion || 0}
          </p>
        </div>
        <span
          className={[
            'rounded-full border px-2 py-0.5 text-[10px] font-semibold uppercase tracking-[0.16em]',
            badgeClassName,
          ].join(' ')}
        >
          {policy.status}
        </span>
      </div>
      <div className={metricsClassName}>
        <RolloutMetric label="Bound" value={String(rollout.total)} />
        <RolloutMetric label="In sync" value={String(rollout.inSync)} />
        <RolloutMetric label="Drifted" value={String(rollout.outOfSync + rollout.applyFailed)} />
      </div>
    </Link>
  );
}

function RolloutMetric({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-3">
      <span>{label}</span>
      <span>{value}</span>
    </div>
  );
}

export function PolicyGroupHeaderSection({
  policy,
  boundGolems,
  rollout,
}: {
  policy: PolicyGroup;
  boundGolems: GolemSummary[];
  rollout: PolicyRolloutSummary;
}) {
  const needsAttention = rollout.outOfSync + rollout.applyFailed + rollout.syncPending;

  return (
    <section className="panel p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground">{policy.slug}</p>
          <h2 className="mt-1 text-xl font-bold tracking-tight text-foreground">{policy.name}</h2>
          <p className="mt-2 max-w-3xl text-sm text-muted-foreground">{policy.description || 'No description set yet.'}</p>
        </div>
        <div className="grid gap-1 text-right text-xs text-muted-foreground">
          <span>Published {formatTimestamp(policy.lastPublishedAt)}</span>
          <span>Updated {formatTimestamp(policy.updatedAt)}</span>
        </div>
      </div>

      <div className="mt-4 grid gap-3 md:grid-cols-4">
        <MetricCard label="Current version" value={`v${policy.currentVersion || 0}`} />
        <MetricCard label="Bound golems" value={String(boundGolems.length)} />
        <MetricCard label="In sync" value={String(rollout.inSync)} />
        <MetricCard label="Needs attention" value={String(needsAttention)} />
      </div>
    </section>
  );
}

export function PolicyDraftSection({
  draftSpec,
  draftEditorValue,
  draftEditorError,
  isSaving,
  onDraftEditorChange,
  onSaveDraft,
}: {
  draftSpec: PolicyGroupSpecResponse | null;
  draftEditorValue: string;
  draftEditorError: string | null;
  isSaving: boolean;
  onDraftEditorChange: (value: string) => void;
  onSaveDraft: () => Promise<void>;
}) {
  return (
    <section className="panel p-5">
      <div className="flex items-center justify-between gap-3">
        <div>
          <h3 className="text-base font-bold tracking-tight text-foreground">Draft spec</h3>
          <p className="mt-1 text-sm text-muted-foreground">
            Raw policy package editor. Existing provider secrets stay preserved unless you explicitly set a new `apiKey`.
          </p>
        </div>
        <button
          type="button"
          disabled={isSaving}
          onClick={() => {
            void onSaveDraft();
          }}
          className="bg-primary px-3 py-1.5 text-sm font-semibold text-primary-foreground disabled:opacity-60"
        >
          {isSaving ? 'Saving…' : 'Save draft'}
        </button>
      </div>

      <SecretStatusPanel spec={draftSpec} />

      <label className="mt-4 grid gap-2">
        <span className="text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">Draft spec JSON</span>
        <textarea
          aria-label="Draft spec JSON"
          value={draftEditorValue}
          onChange={(event) => onDraftEditorChange(event.target.value)}
          rows={26}
          className="min-h-[520px] border border-border bg-panel px-3 py-3 font-mono text-xs leading-6 text-foreground outline-none transition focus:border-primary"
        />
      </label>
      {draftEditorError ? (
        <p className="mt-3 border border-rose-700 bg-rose-900/40 px-3 py-2 text-sm text-rose-300">{draftEditorError}</p>
      ) : null}
    </section>
  );
}

function MetricCard({ label, value }: { label: string; value: string }) {
  return (
    <article className="border border-border/70 bg-panel/80 p-4">
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
          <div key={providerKey} className="border border-border/70 bg-panel/80 px-3 py-2 text-xs text-muted-foreground">
            <p className="font-semibold text-foreground">{providerKey}</p>
            <p className="mt-1">Secret {provider.apiKeyPresent ? 'present' : 'missing'} · {provider.apiType || 'unknown type'}</p>
          </div>
        ))
      ) : (
        <div className="border border-border/70 bg-panel/80 px-3 py-2 text-xs text-muted-foreground">
          No providers configured in the current draft.
        </div>
      )}
    </div>
  );
}
