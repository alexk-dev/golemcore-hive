import { Link } from 'react-router-dom';
import type { EnrollmentToken, GolemRole, GolemSummary } from '../../lib/api/golemsApi';
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
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <span className="pill">Phase 2 live</span>
          <h2 className="mt-4 text-3xl font-bold tracking-[-0.04em] text-foreground">
            Register runtimes, bind roles, and watch fleet presence in one place.
          </h2>
          <p className="mt-3 max-w-3xl text-sm leading-7 text-muted-foreground">
            Hive now exposes reusable enrollment tokens, copy-ready join codes, machine JWT onboarding, heartbeat-aware
            status, and role assignment.
          </p>
        </div>
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
    <section className="panel p-5 md:p-6">
      <div className="grid gap-3 md:grid-cols-[minmax(0,1fr)_220px_220px]">
        <input
          value={query}
          onChange={(event) => onQueryChange(event.target.value)}
          placeholder="Search by golem name, host, or id"
          className="rounded-[20px] border border-border bg-white/90 px-4 py-3 text-sm outline-none transition focus:border-primary"
        />
        <select
          value={stateFilter}
          onChange={(event) => onStateFilterChange(event.target.value)}
          className="rounded-[20px] border border-border bg-white/90 px-4 py-3 text-sm outline-none transition focus:border-primary"
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
          className="rounded-[20px] border border-border bg-white/90 px-4 py-3 text-sm outline-none transition focus:border-primary"
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
      <div className="flex items-center justify-between gap-3">
        <div>
          <span className="pill">Fleet registry</span>
          <h3 className="mt-3 text-2xl font-bold tracking-[-0.04em] text-foreground">{golems.length} registered golems</h3>
        </div>
      </div>
      <div className="mt-5 grid gap-3">
        {golems.length ? (
          golems.map((golem) => {
            const selected = golem.id === selectedGolemId;
            return (
              <button
                key={golem.id}
                type="button"
                onClick={() => onSelect(golem.id)}
                className={[
                  'rounded-[22px] border p-4 text-left transition',
                  selected
                    ? 'border-primary/40 bg-primary/5 shadow-[0_10px_30px_rgba(238,109,52,0.12)]'
                    : 'border-border/70 bg-white/70 hover:bg-white',
                ].join(' ')}
              >
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <p className="text-lg font-bold tracking-[-0.03em] text-foreground">{golem.displayName}</p>
                    <p className="mt-1 text-sm text-muted-foreground">{golem.hostLabel || golem.id}</p>
                  </div>
                  <GolemStatusBadge state={golem.state} />
                </div>
                <div className="mt-4 grid gap-2 text-sm text-muted-foreground">
                  <div>Last seen: {formatTimestamp(golem.lastSeenAt)}</div>
                  <div>Roles: {golem.roleSlugs.length ? golem.roleSlugs.join(', ') : 'none'}</div>
                </div>
              </button>
            );
          })
        ) : (
          <div className="rounded-[24px] border border-dashed border-border p-6 text-sm leading-6 text-muted-foreground">
            No golems have enrolled yet. Create an enrollment token, copy its join code into `golemcore-bot`, then let the
            bot join and register itself.
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
      <div className="flex items-start justify-between gap-4">
        <div>
          <span className="pill">Enrollment tokens</span>
          <h3 className="mt-4 text-2xl font-bold tracking-[-0.04em] text-foreground">Reusable join tokens</h3>
        </div>
      </div>

      <div className="mt-5 grid gap-3">
        {tokens.length ? (
          tokens.map((token) => (
            <div key={token.id} className="rounded-[22px] border border-border/70 bg-white/70 p-4">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <p className="text-sm font-semibold uppercase tracking-[0.16em] text-muted-foreground">{token.id}</p>
                  <p className="mt-2 text-sm leading-6 text-foreground">{token.note || token.preview}</p>
                  <p className="mt-2 text-sm text-muted-foreground">
                    Created by {token.createdByUsername || 'operator'} · expires {formatTimestamp(token.expiresAt)}
                  </p>
                  <p className="mt-1 text-sm text-muted-foreground">
                    Registrations {token.registrationCount}
                    {token.lastUsedAt ? ` · last used ${formatTimestamp(token.lastUsedAt)}` : ' · never used'}
                    {token.lastRegisteredGolemId ? ` · last golem ${token.lastRegisteredGolemId}` : ''}
                  </p>
                  <p className="mt-1 text-sm text-muted-foreground">{token.revoked ? 'Revoked' : 'Active'}</p>
                </div>
                {!token.revoked ? (
                  <button
                    type="button"
                    disabled={isRevoking}
                    onClick={() => onRevoke(token.id)}
                    className="rounded-full border border-rose-300 bg-rose-100 px-4 py-2 text-sm font-semibold text-rose-900"
                  >
                    Revoke
                  </button>
                ) : null}
              </div>
            </div>
          ))
        ) : (
          <p className="text-sm leading-6 text-muted-foreground">No enrollment tokens have been minted yet.</p>
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
