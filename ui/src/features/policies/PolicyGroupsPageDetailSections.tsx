import type { GolemSummary } from '../../lib/api/golemsApi';
import type { PolicyGroupVersion } from '../../lib/api/policiesApi';
import { formatTimestamp } from '../../lib/format';
import { formatVersionPair, syncBadgeClassName } from './PolicyGroupsPageSupport';

export function PolicyReleaseRail({
  versions,
  currentVersion,
  publishSummary,
  isPublishing,
  isRollingBack,
  onPublishSummaryChange,
  onPublish,
  onRollback,
}: {
  versions: PolicyGroupVersion[];
  currentVersion: number;
  publishSummary: string;
  isPublishing: boolean;
  isRollingBack: boolean;
  onPublishSummaryChange: (value: string) => void;
  onPublish: () => Promise<void>;
  onRollback: (version: number) => Promise<void>;
}) {
  return (
    <div className="grid gap-4">
      <section className="panel p-5">
        <h3 className="text-base font-bold tracking-tight text-foreground">Publish</h3>
        <p className="mt-1 text-sm text-muted-foreground">
          Push the current draft into an immutable version and mark every attached golem for sync.
        </p>
        <label className="mt-4 grid gap-2">
          <span className="text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">Change summary</span>
          <input
            value={publishSummary}
            onChange={(event) => onPublishSummaryChange(event.target.value)}
            className="border border-border bg-panel/90 px-3 py-2 text-sm outline-none transition focus:border-primary focus:ring-1 focus:ring-primary/50"
            placeholder="Explain what changed in this release"
          />
        </label>
        <button
          type="button"
          disabled={isPublishing}
          onClick={() => {
            void onPublish();
          }}
          className="mt-4 bg-accent px-3 py-2 text-sm font-semibold text-accent-foreground disabled:opacity-60"
        >
          {isPublishing ? 'Publishing…' : 'Publish version'}
        </button>
      </section>

      <section className="panel p-5">
        <div className="flex items-center justify-between gap-3">
          <h3 className="text-base font-bold tracking-tight text-foreground">Versions</h3>
          <span className="text-xs text-muted-foreground">{versions.length} stored</span>
        </div>
        <div className="mt-4 grid gap-3">
          {versions.map((version) => (
            <PolicyVersionCard
              key={version.version}
              version={version}
              currentVersion={currentVersion}
              isRollingBack={isRollingBack}
              onRollback={onRollback}
            />
          ))}
        </div>
      </section>
    </div>
  );
}

function PolicyVersionCard({
  version,
  currentVersion,
  isRollingBack,
  onRollback,
}: {
  version: PolicyGroupVersion;
  currentVersion: number;
  isRollingBack: boolean;
  onRollback: (version: number) => Promise<void>;
}) {
  const isCurrent = version.version === currentVersion;

  return (
    <article className="border border-border/70 bg-panel/80 p-4">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-foreground">Version {version.version}</p>
          <p className="mt-1 text-xs text-muted-foreground">{version.changeSummary || 'No summary'} · {formatTimestamp(version.publishedAt)}</p>
          <p className="mt-2 text-xs text-muted-foreground">
            Providers {Object.keys(version.specSnapshot?.llmProviders ?? {}).join(', ') || '—'} · Default model {version.specSnapshot?.modelCatalog?.defaultModel || '—'}
          </p>
        </div>
        {isCurrent ? (
          <span className="pill">current</span>
        ) : (
          <button
            type="button"
            onClick={() => {
              void onRollback(version.version);
            }}
            disabled={isRollingBack}
            className="border border-border bg-panel px-3 py-1.5 text-xs font-semibold text-foreground disabled:opacity-60"
          >
            Rollback to v{version.version}
          </button>
        )}
      </div>
    </article>
  );
}

export function PolicyBindingsSection({
  allGolems,
  boundGolems,
  selectedAttachGolemId,
  isBinding,
  isUnbinding,
  onSelectedAttachGolemIdChange,
  onBindSelected,
  onUnbind,
}: {
  allGolems: GolemSummary[];
  boundGolems: GolemSummary[];
  selectedAttachGolemId: string;
  isBinding: boolean;
  isUnbinding: boolean;
  onSelectedAttachGolemIdChange: (value: string) => void;
  onBindSelected: () => Promise<void>;
  onUnbind: (golemId: string) => Promise<void>;
}) {
  return (
    <section className="panel p-5">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h3 className="text-base font-bold tracking-tight text-foreground">Bound golems</h3>
          <p className="mt-1 text-sm text-muted-foreground">Attach a golem to make Hive authoritative for its model routing package.</p>
        </div>
        <div className="grid gap-2 sm:grid-cols-[220px_auto]">
          <label className="grid gap-1">
            <span className="text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">Attach golem</span>
            <select
              aria-label="Attach golem"
              value={selectedAttachGolemId}
              onChange={(event) => onSelectedAttachGolemIdChange(event.target.value)}
              className="border border-border bg-panel/90 px-3 py-2 text-sm outline-none transition focus:border-primary focus:ring-1 focus:ring-primary/50"
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
            disabled={isBinding || !selectedAttachGolemId}
            onClick={() => {
              void onBindSelected();
            }}
            className="h-[42px] self-end bg-primary px-3 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-60"
          >
            {isBinding ? 'Attaching…' : 'Attach selected'}
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
            <tbody className="divide-y divide-border/60 bg-panel/80">
              {boundGolems.map((golem) => (
                <tr key={golem.id}>
                  <td className="px-3 py-3">
                    <div>
                      <p className="font-semibold text-foreground">{golem.displayName}</p>
                      <p className="text-xs text-muted-foreground">{golem.hostLabel || golem.id}</p>
                    </div>
                  </td>
                  <td className="px-3 py-3 text-muted-foreground">
                    {formatVersionPair(golem.policyBinding?.targetVersion ?? null, golem.policyBinding?.appliedVersion ?? null)}
                  </td>
                  <td className="px-3 py-3">
                    <span className={syncBadgeClassName(golem.policyBinding?.syncStatus)}>{golem.policyBinding?.syncStatus || 'UNBOUND'}</span>
                  </td>
                  <td className="px-3 py-3 text-xs text-muted-foreground">{golem.policyBinding?.lastErrorDigest || '—'}</td>
                  <td className="px-3 py-3">
                    <button
                      type="button"
                      aria-label={`Detach ${golem.displayName}`}
                      onClick={() => {
                        void onUnbind(golem.id);
                      }}
                      disabled={isUnbinding}
                      className="border border-border bg-panel px-3 py-1.5 text-xs font-semibold text-foreground disabled:opacity-60"
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
        <div className="mt-4 soft-card px-4 py-6 text-center">
          <p className="text-sm text-muted-foreground">No golems are bound to this policy group yet.</p>
        </div>
      )}
    </section>
  );
}

export function PolicyGroupEmptyState() {
  return (
    <section className="panel p-8 text-sm text-muted-foreground">
      Select a policy group to inspect its draft, versions, and rollout health.
    </section>
  );
}
