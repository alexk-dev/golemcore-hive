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
      <div className="panel p-4 text-sm text-muted-foreground">
        Select a golem.
      </div>
    );
  }

  return (
    <div className="panel p-4">
      <div className="flex items-center justify-between gap-3">
        <div>
          <h2 className="text-sm font-bold text-foreground">{golem.displayName}</h2>
          <p className="text-xs text-muted-foreground">
            {golem.hostLabel || golem.id} · {golem.runtimeVersion || 'n/a'}
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
    <div className="mt-3 grid gap-3 text-xs md:grid-cols-2">
      <dl className="grid gap-1">
        <dt className="font-semibold text-muted-foreground">Presence</dt>
        <Row label="Heartbeat" value={formatTimestamp(golem.lastHeartbeatAt)} />
        <Row label="Last seen" value={formatTimestamp(golem.lastSeenAt)} />
        <Row label="Missed" value={String(golem.missedHeartbeatCount)} />
        <Row label="Interval" value={`${golem.heartbeatIntervalSeconds}s`} />
      </dl>
      <dl className="grid gap-1">
        <dt className="font-semibold text-muted-foreground">Runtime</dt>
        <Row label="Build" value={golem.buildVersion || 'n/a'} />
        <Row label="Model" value={golem.lastHeartbeat?.modelTier || 'n/a'} />
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
    <div className="mt-3">
      <p className="text-xs font-semibold text-muted-foreground">Roles ({roleSlugs.length})</p>
      <div className="mt-1 flex flex-wrap gap-x-4 gap-y-1">
        {roles.length ? (
          roles.map((role) => {
            const checked = roleSlugs.includes(role.slug);
            return (
              <label key={role.slug} className="flex items-center gap-1.5 text-xs">
                <input
                  type="checkbox"
                  checked={checked}
                  disabled={isBusy}
                  onChange={() => void onToggleRole(role.slug, !checked)}
                  className="h-3 w-3 border-border text-primary focus:ring-primary"
                />
                <span className="text-foreground">{role.name}</span>
              </label>
            );
          })
        ) : (
          <p className="text-xs text-muted-foreground">No roles defined.</p>
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
    <div className="mt-3 flex flex-wrap items-center gap-2 border-t border-border/60 pt-3">
      {state === 'PAUSED' ? (
        <button
          type="button"
          onClick={() => void onResume()}
          disabled={isBusy}
          className="bg-accent px-3 py-1 text-xs font-semibold text-accent-foreground disabled:opacity-60"
        >
          Resume
        </button>
      ) : (
        <button
          type="button"
          onClick={() => void onPause()}
          disabled={isBusy || state === 'REVOKED'}
          className="bg-foreground px-3 py-1 text-xs font-semibold text-white disabled:opacity-60"
        >
          Pause
        </button>
      )}
      <button
        type="button"
        onClick={() => void onRevoke()}
        disabled={isBusy || state === 'REVOKED'}
        className="border border-rose-300 bg-rose-100 px-3 py-1 text-xs font-semibold text-rose-900 disabled:opacity-60"
      >
        Revoke
      </button>
      {pauseReason ? <span className="text-xs text-sky-900">Paused: {pauseReason}</span> : null}
      {revokeReason ? <span className="text-xs text-rose-900">Revoked: {revokeReason}</span> : null}
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-2">
      <span className="text-muted-foreground">{label}</span>
      <span className="text-right text-foreground">{value}</span>
    </div>
  );
}
