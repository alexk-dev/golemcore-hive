import { Link } from 'react-router-dom';
import type { EnrollmentToken, GolemRole, GolemSummary } from '../../lib/api/golemsApi';
import { PageHeader } from '../layout/PageHeader';
import { GolemStatusBadge } from './GolemStatusBadge';

const fleetStates = ['', 'ONLINE', 'DEGRADED', 'OFFLINE', 'PAUSED', 'REVOKED', 'PENDING_ENROLLMENT'];

function formatTimestamp(value: string | null) {
  if (!value) {
    return 'Never';
  }
  return new Date(value).toLocaleString();
}

export function GolemsHero({
  onCreateEnrollmentToken,
}: {
  onCreateEnrollmentToken: () => void;
}) {
  return (
    <section className="panel p-6 md:p-8">
      <PageHeader
        eyebrow="Fleet"
        title="Fleet registry"
        description="Track active runtimes, bind roles, and issue enrollment tokens from one workspace."
        actions={
          <>
            <Link
              to="/fleet/roles"
              className="border border-border bg-white/80 px-4 py-2 text-sm font-semibold text-foreground"
            >
              Manage roles
            </Link>
            <button
              type="button"
              onClick={onCreateEnrollmentToken}
              className="bg-foreground px-4 py-2 text-sm font-semibold text-white"
            >
              Create token
            </button>
          </>
        }
      />
    </section>
  );
}

export function GolemFiltersPanel({
  query,
  stateFilter,
  roleFilter,
  roles,
  onQueryChange,
  onStateFilterChange,
  onRoleFilterChange,
}: {
  query: string;
  stateFilter: string;
  roleFilter: string;
  roles: GolemRole[];
  onQueryChange: (value: string) => void;
  onStateFilterChange: (value: string) => void;
  onRoleFilterChange: (value: string) => void;
}) {
  return (
    <section className="section-surface p-4 md:p-5">
      <div className="grid gap-3 md:grid-cols-[minmax(0,1fr)_220px_220px]">
        <input
          value={query}
          onChange={(event) => onQueryChange(event.target.value)}
          placeholder="Search by golem name, host, or id"
          className="border border-border bg-white/90 px-3 py-2 text-sm outline-none transition focus:border-primary"
        />
        <select
          value={stateFilter}
          onChange={(event) => onStateFilterChange(event.target.value)}
          className="border border-border bg-white/90 px-3 py-2 text-sm outline-none transition focus:border-primary"
        >
          {fleetStates.map((state) => (
            <option key={state || 'all'} value={state}>
              {state || 'All states'}
            </option>
          ))}
        </select>
        <select
          value={roleFilter}
          onChange={(event) => onRoleFilterChange(event.target.value)}
          className="border border-border bg-white/90 px-3 py-2 text-sm outline-none transition focus:border-primary"
        >
          <option value="">All roles</option>
          {roles.map((role) => (
            <option key={role.slug} value={role.slug}>
              {role.slug}
            </option>
          ))}
        </select>
      </div>
    </section>
  );
}

export function GolemRegistryPanel({
  golems,
  selectedGolemId,
  onSelect,
}: {
  golems: GolemSummary[];
  selectedGolemId: string | null;
  onSelect: (golemId: string) => void;
}) {
  return (
    <article className="panel p-5 md:p-6">
      <div className="flex items-center justify-between gap-3 border-b border-border/70 pb-4">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground">Registry</p>
          <h2 className="mt-2 text-xl font-semibold tracking-[-0.03em] text-foreground">{golems.length} golems</h2>
        </div>
      </div>
      <div className="mt-4 grid gap-3">
        {golems.length ? (
          golems.map((golem) => {
            const selected = golem.id === selectedGolemId;
            return (
              <button
                key={golem.id}
                type="button"
                onClick={() => onSelect(golem.id)}
                className={[
                  'border p-4 text-left transition',
                  selected ? 'border-primary/40 bg-primary/5' : 'border-border/70 bg-white/70 hover:bg-white',
                ].join(' ')}
              >
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="text-base font-semibold tracking-[-0.02em] text-foreground">{golem.displayName}</p>
                    <p className="mt-1 text-sm text-muted-foreground">{golem.hostLabel || golem.id}</p>
                  </div>
                  <GolemStatusBadge state={golem.state} />
                </div>
                <div className="mt-3 grid gap-1 text-sm text-muted-foreground">
                  <div>Last seen: {formatTimestamp(golem.lastSeenAt)}</div>
                  <div>Roles: {golem.roleSlugs.length ? golem.roleSlugs.join(', ') : 'none'}</div>
                </div>
              </button>
            );
          })
        ) : (
          <div className="border border-dashed border-border p-4 text-sm text-muted-foreground">
            No golems yet. Create an enrollment token, copy the join code into `golemcore-bot`, and let the runtime register.
          </div>
        )}
      </div>
    </article>
  );
}

export function EnrollmentTokensPanel({
  tokens,
  isRevoking,
  onRevoke,
}: {
  tokens: EnrollmentToken[];
  isRevoking: boolean;
  onRevoke: (tokenId: string) => void;
}) {
  return (
    <section className="panel p-6 md:p-8">
      <PageHeader
        eyebrow="Enrollment"
        title="Reusable join tokens"
        description="Keep token management compact; revoke only when a join path should stop accepting new runtimes."
        meta={<span>{tokens.length} active records</span>}
      />

      <div className="mt-4">
        {tokens.length ? (
          <div className="section-surface px-4">
            {tokens.map((token) => (
              <div key={token.id} className="dense-row">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="text-sm font-semibold text-foreground">{token.id}</p>
                    <span className="text-[11px] font-semibold uppercase tracking-[0.14em] text-muted-foreground">
                      {token.revoked ? 'Revoked' : 'Active'}
                    </span>
                  </div>
                  <p className="mt-1 text-sm text-muted-foreground">{token.note || token.preview}</p>
                  <p className="mt-1 text-sm text-muted-foreground">
                    Created by {token.createdByUsername || 'operator'} · expires {formatTimestamp(token.expiresAt)}
                  </p>
                  <p className="mt-1 text-sm text-muted-foreground">
                    Registrations {token.registrationCount}
                    {token.lastUsedAt ? ` · last used ${formatTimestamp(token.lastUsedAt)}` : ' · never used'}
                    {token.lastRegisteredGolemId ? ` · last golem ${token.lastRegisteredGolemId}` : ''}
                  </p>
                </div>
                {!token.revoked ? (
                  <button
                    type="button"
                    disabled={isRevoking}
                    onClick={() => onRevoke(token.id)}
                    className="border border-rose-300 bg-rose-100 px-3 py-2 text-sm font-semibold text-rose-900"
                  >
                    Revoke
                  </button>
                ) : null}
              </div>
            ))}
          </div>
        ) : (
          <p className="text-sm text-muted-foreground">No enrollment tokens have been minted yet.</p>
        )}
      </div>
    </section>
  );
}

export function GolemActionDialog({
  mode,
  reason,
  isPending,
  onReasonChange,
  onCancel,
  onSubmit,
}: {
  mode: 'pause' | 'revoke' | null;
  reason: string;
  isPending: boolean;
  onReasonChange: (value: string) => void;
  onCancel: () => void;
  onSubmit: () => Promise<void>;
}) {
  if (!mode) {
    return null;
  }

  const title = mode === 'pause' ? 'Pause golem' : 'Revoke golem';
  const heading = mode === 'pause' ? 'Record a pause reason' : 'Record a revoke reason';
  const description = mode === 'pause'
    ? 'Optional context helps operators understand why this runtime is being paused.'
    : 'A revoke reason is recommended so the fleet history stays auditable.';
  const placeholder = mode === 'pause'
    ? 'Optional note for the pause action.'
    : 'Recommended note explaining why this runtime is being revoked.';
  const submitLabel = isPending ? (mode === 'pause' ? 'Pausing...' : 'Revoking...') : title;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-foreground/20 px-4 py-6 backdrop-blur-sm">
      <div className="panel w-full max-w-xl p-6 md:p-8">
        <div className="flex items-start justify-between gap-4">
          <div>
            <span className="pill">{title}</span>
            <h3 className="mt-4 text-2xl font-bold tracking-[-0.04em] text-foreground">{heading}</h3>
            <p className="mt-3 text-sm leading-6 text-muted-foreground">{description}</p>
          </div>
          <button
            type="button"
            onClick={onCancel}
            className="rounded-full border border-border bg-white/70 px-3 py-2 text-sm font-semibold text-foreground"
          >
            Close
          </button>
        </div>

        <form
          className="mt-6 grid gap-4"
          onSubmit={(event) => {
            event.preventDefault();
            void onSubmit();
          }}
        >
          <label className="grid gap-2">
            <span className="text-sm font-semibold text-foreground">Reason</span>
            <textarea
              value={reason}
              onChange={(event) => onReasonChange(event.target.value)}
              rows={5}
              className="rounded-[20px] border border-border bg-white/90 px-4 py-3 text-sm outline-none transition focus:border-primary"
              placeholder={placeholder}
            />
          </label>
          <div className="flex flex-wrap justify-end gap-3">
            <button
              type="button"
              onClick={onCancel}
              className="rounded-full border border-border bg-white/80 px-4 py-2 text-sm font-semibold text-foreground"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isPending}
              className="rounded-full bg-foreground px-4 py-2 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-60"
            >
              {submitLabel}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
