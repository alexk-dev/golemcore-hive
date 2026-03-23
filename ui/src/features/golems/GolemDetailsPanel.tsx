import type { GolemDetails, GolemRole } from '../../lib/api/golemsApi';
import { GolemStatusBadge } from './GolemStatusBadge';
import { formatTimestamp } from '../../lib/format';

interface GolemDetailsPanelProps {
  golem: GolemDetails | null;
  roles: GolemRole[];
  isBusy: boolean;
  onToggleRole: (roleSlug: string, nextAssigned: boolean) => Promise<void>;
  onPause: () => void | Promise<void>;
  onResume: () => Promise<void>;
  onRevoke: () => void | Promise<void>;
}

export function GolemDetailsPanel({
  golem,
  roles,
  isBusy,
  onToggleRole,
  onPause,
  onResume,
  onRevoke,
}: GolemDetailsPanelProps) {
  if (!golem) {
    return (
      <div className="panel p-5 text-sm text-muted-foreground">
        Select a golem to see details.
      </div>
    );
  }

  return (
    <div className="panel p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-xl font-bold tracking-tight text-foreground">{golem.displayName}</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            {golem.hostLabel || 'No host'} · {golem.runtimeVersion || 'runtime n/a'}
          </p>
        </div>
        <GolemStatusBadge state={golem.state} />
      </div>

      <GolemStatsGrid golem={golem} />

      <GolemRolesSection
        roleSlugs={golem.roleSlugs}
        roles={roles}
        isBusy={isBusy}
        onToggleRole={onToggleRole}
      />

      <GolemLifecycleActions
        state={golem.state}
        pauseReason={golem.pauseReason}
        revokeReason={golem.revokeReason}
        isBusy={isBusy}
        onPause={onPause}
        onResume={onResume}
        onRevoke={onRevoke}
      />
    </div>
  );
}

function GolemStatsGrid({ golem }: { golem: GolemDetails }) {
  return (
    <div className="mt-4 grid gap-4 md:grid-cols-2">
      <dl className="grid gap-2 text-sm">
        <dt className="text-xs font-semibold text-muted-foreground">Presence</dt>
        <Row label="Last heartbeat" value={formatTimestamp(golem.lastHeartbeatAt)} />
        <Row label="Last seen" value={formatTimestamp(golem.lastSeenAt)} />
        <Row label="Missed heartbeats" value={String(golem.missedHeartbeatCount)} />
        <Row label="Interval" value={`${golem.heartbeatIntervalSeconds}s`} />
      </dl>
      <dl className="grid gap-2 text-sm">
        <dt className="text-xs font-semibold text-muted-foreground">Runtime</dt>
        <Row label="Build" value={golem.buildVersion || 'n/a'} />
        <Row label="Model tier" value={golem.lastHeartbeat?.modelTier || 'n/a'} />
        <Row label="Channels" value={golem.supportedChannels.length ? golem.supportedChannels.join(', ') : 'n/a'} />
        <Row label="Providers" value={golem.capabilities?.providers.length ? golem.capabilities.providers.join(', ') : 'n/a'} />
      </dl>
    </div>
  );
}

function GolemRolesSection({
  roleSlugs,
  roles,
  isBusy,
  onToggleRole,
}: {
  roleSlugs: string[];
  roles: GolemRole[];
  isBusy: boolean;
  onToggleRole: (roleSlug: string, nextAssigned: boolean) => Promise<void>;
}) {
  return (
    <div className="mt-4">
      <p className="text-xs font-semibold text-muted-foreground">Roles ({roleSlugs.length})</p>
      <div className="mt-2 grid gap-2">
        {roles.length ? (
          roles.map((role) => {
            const checked = roleSlugs.includes(role.slug);
            return (
              <label key={role.slug} className="flex items-center gap-3 rounded-xl border border-border/70 bg-white/70 p-3">
                <input
                  type="checkbox"
                  checked={checked}
                  disabled={isBusy}
                  onChange={() => void onToggleRole(role.slug, !checked)}
                  className="h-4 w-4 rounded border-border text-primary focus:ring-primary"
                />
                <span className="text-sm font-semibold text-foreground">{role.name}</span>
                <span className="text-xs text-muted-foreground">{role.slug}</span>
              </label>
            );
          })
        ) : (
          <p className="text-sm text-muted-foreground">No roles defined.</p>
        )}
      </div>
    </div>
  );
}

function GolemLifecycleActions({
  state,
  pauseReason,
  revokeReason,
  isBusy,
  onPause,
  onResume,
  onRevoke,
}: {
  state: string;
  pauseReason: string | null;
  revokeReason: string | null;
  isBusy: boolean;
  onPause: () => void | Promise<void>;
  onResume: () => Promise<void>;
  onRevoke: () => void | Promise<void>;
}) {
  return (
    <div className="mt-4 flex flex-wrap items-center gap-2 border-t border-border/60 pt-4">
      {state === 'PAUSED' ? (
        <button
          type="button"
          onClick={() => void onResume()}
          disabled={isBusy}
          className="rounded-full bg-accent px-4 py-2 text-sm font-semibold text-accent-foreground disabled:opacity-60"
        >
          Resume
        </button>
      ) : (
        <button
          type="button"
          onClick={() => void onPause()}
          disabled={isBusy || state === 'REVOKED'}
          className="rounded-full bg-foreground px-4 py-2 text-sm font-semibold text-white disabled:opacity-60"
        >
          Pause
        </button>
      )}
      <button
        type="button"
        onClick={() => void onRevoke()}
        disabled={isBusy || state === 'REVOKED'}
        className="rounded-full border border-rose-300 bg-rose-100 px-4 py-2 text-sm font-semibold text-rose-900 disabled:opacity-60"
      >
        Revoke
      </button>
      {pauseReason ? <span className="text-sm text-sky-900">Paused: {pauseReason}</span> : null}
      {revokeReason ? <span className="text-sm text-rose-900">Revoked: {revokeReason}</span> : null}
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-3">
      <span className="text-muted-foreground">{label}</span>
      <span className="text-right font-medium text-foreground">{value}</span>
    </div>
  );
}
