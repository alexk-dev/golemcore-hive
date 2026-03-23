import { Link } from 'react-router-dom';
import type { EnrollmentToken, GolemRole, GolemSummary } from '../../lib/api/golemsApi';
import { GolemStatusBadge } from './GolemStatusBadge';
import { formatTimestamp } from '../../lib/format';

const fleetStates = ['', 'ONLINE', 'DEGRADED', 'OFFLINE', 'PAUSED', 'REVOKED', 'PENDING_ENROLLMENT'];

export function GolemsHero({
  onCreateEnrollmentToken,
}: {
  onCreateEnrollmentToken: () => void;
}) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3">
      <div className="flex flex-wrap gap-2">
        <Link
          to="/fleet/roles"
          className="rounded-full border border-border bg-white/80 px-4 py-2 text-sm font-semibold text-foreground"
        >
          Manage roles
        </Link>
        <button
          type="button"
          onClick={onCreateEnrollmentToken}
          className="rounded-full bg-foreground px-4 py-2 text-sm font-semibold text-white"
        >
          Create enrollment token
        </button>
      </div>
    </div>
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
    <div className="grid gap-3 md:grid-cols-[minmax(0,1fr)_220px_220px]">
      <input
        value={query}
        onChange={(event) => onQueryChange(event.target.value)}
        placeholder="Search by name, host, or id"
        className="rounded-xl border border-border bg-white/90 px-4 py-2.5 text-sm outline-none transition focus:border-primary"
      />
      <select
        value={stateFilter}
        onChange={(event) => onStateFilterChange(event.target.value)}
        className="rounded-xl border border-border bg-white/90 px-4 py-2.5 text-sm outline-none transition focus:border-primary"
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
        className="rounded-xl border border-border bg-white/90 px-4 py-2.5 text-sm outline-none transition focus:border-primary"
      >
        <option value="">All roles</option>
        {roles.map((role) => (
          <option key={role.slug} value={role.slug}>
            {role.slug}
          </option>
        ))}
      </select>
    </div>
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
    <div className="grid gap-3">
      <p className="text-sm font-semibold text-foreground">{golems.length} golems</p>
      {golems.length ? (
        golems.map((golem) => {
          const selected = golem.id === selectedGolemId;
          return (
            <button
              key={golem.id}
              type="button"
              onClick={() => onSelect(golem.id)}
              className={[
                'rounded-xl border p-4 text-left transition',
                selected
                  ? 'border-primary/40 bg-primary/5 shadow-[0_8px_20px_rgba(238,109,52,0.1)]'
                  : 'border-border/70 bg-white/70 hover:bg-white',
              ].join(' ')}
            >
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <p className="text-sm font-bold text-foreground">{golem.displayName}</p>
                  <p className="mt-0.5 text-xs text-muted-foreground">{golem.hostLabel || golem.id}</p>
                </div>
                <GolemStatusBadge state={golem.state} />
              </div>
              <div className="mt-2 text-xs text-muted-foreground">
                Last seen: {formatTimestamp(golem.lastSeenAt)}
                {golem.roleSlugs.length ? ` · ${golem.roleSlugs.join(', ')}` : ''}
              </div>
            </button>
          );
        })
      ) : (
        <p className="text-sm text-muted-foreground">
          No golems enrolled yet. Create an enrollment token to get started.
        </p>
      )}
    </div>
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
  if (!tokens.length) {
    return null;
  }

  return (
    <section className="panel p-5">
      <h3 className="text-base font-bold tracking-tight text-foreground">Enrollment tokens</h3>
      <div className="mt-4 grid gap-3">
        {tokens.map((token) => (
          <div key={token.id} className="rounded-xl border border-border/70 bg-white/70 p-4">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div className="space-y-1">
                <p className="text-sm font-medium text-foreground">{token.note || token.preview}</p>
                <p className="text-xs text-muted-foreground">
                  By {token.createdByUsername || 'operator'} · expires {formatTimestamp(token.expiresAt)}
                  {' · '}{token.registrationCount} registrations
                  {token.revoked ? ' · Revoked' : ''}
                </p>
              </div>
              {!token.revoked ? (
                <button
                  type="button"
                  disabled={isRevoking}
                  onClick={() => onRevoke(token.id)}
                  className="rounded-full border border-rose-300 bg-rose-100 px-3 py-1.5 text-sm font-semibold text-rose-900"
                >
                  Revoke
                </button>
              ) : null}
            </div>
          </div>
        ))}
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
  const submitLabel = isPending ? (mode === 'pause' ? 'Pausing...' : 'Revoking...') : title;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-foreground/20 px-4 py-6 backdrop-blur-sm">
      <div className="panel w-full max-w-md p-5">
        <div className="flex items-center justify-between gap-3">
          <h3 className="text-lg font-bold tracking-tight text-foreground">{title}</h3>
          <button
            type="button"
            onClick={onCancel}
            className="rounded-full border border-border bg-white/70 px-3 py-1.5 text-sm font-semibold text-foreground"
          >
            Close
          </button>
        </div>

        <form
          className="mt-4 grid gap-4"
          onSubmit={(event) => {
            event.preventDefault();
            void onSubmit();
          }}
        >
          <label className="grid gap-1.5">
            <span className="text-sm font-semibold text-foreground">Reason</span>
            <textarea
              value={reason}
              onChange={(event) => onReasonChange(event.target.value)}
              rows={3}
              className="rounded-xl border border-border bg-white/90 px-4 py-2.5 text-sm outline-none transition focus:border-primary"
              placeholder="Optional reason"
            />
          </label>
          <div className="flex justify-end gap-3">
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
              className="rounded-full bg-foreground px-4 py-2 text-sm font-semibold text-white disabled:opacity-60"
            >
              {submitLabel}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
