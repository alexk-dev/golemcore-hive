import { Link } from 'react-router-dom';
import type { GolemDetails, GolemRole } from '../../lib/api/golemsApi';
import type { PolicyGroup } from '../../lib/api/policiesApi';
import { GolemStatusBadge } from './GolemStatusBadge';
import { formatTimestamp } from '../../lib/format';

interface GolemDetailsModalProps {
  golem: GolemDetails | null;
  roles: GolemRole[];
  policies: PolicyGroup[];
  isBusy: boolean;
  onClose: () => void;
  onToggleRole: (roleSlug: string, nextAssigned: boolean) => Promise<void>;
  onPause: () => void | Promise<void>;
  onResume: () => Promise<void>;
  onRevoke: () => void | Promise<void>;
}

export function GolemDetailsModal({
  golem,
  roles,
  policies,
  isBusy,
  onClose,
  onToggleRole,
  onPause,
  onResume,
  onRevoke,
}: GolemDetailsModalProps) {
  if (!golem) {
    return null;
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-foreground/20 px-4 py-6 backdrop-blur-sm">
      <div className="panel w-full max-w-2xl p-5">
        <div className="flex items-center justify-between gap-3">
          <div>
            <h2 className="text-sm font-bold text-foreground">{golem.displayName}</h2>
            <p className="text-xs text-muted-foreground">
              {golem.hostLabel || golem.id} · {golem.runtimeVersion || 'n/a'}
            </p>
          </div>
          <div className="flex items-center gap-3">
            <GolemStatusBadge state={golem.state} />
            <button
              type="button"
              onClick={onClose}
              className="border border-border bg-white/70 px-3 py-1.5 text-sm font-semibold text-foreground"
            >
              Close
            </button>
          </div>
        </div>

        <GolemStatsGrid golem={golem} />

        <GolemPolicySection golem={golem} policies={policies} />

        <GolemRolesSection
          roleSlugs={golem.roleSlugs}
          roles={roles}
          isBusy={isBusy}
          onToggleRole={onToggleRole}
        />

        <GolemLifecycleActions
          golemId={golem.id}
          state={golem.state}
          pauseReason={golem.pauseReason}
          revokeReason={golem.revokeReason}
          isBusy={isBusy}
          onPause={onPause}
          onResume={onResume}
          onRevoke={onRevoke}
        />
      </div>
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

function GolemPolicySection({ golem, policies }: { golem: GolemDetails; policies: PolicyGroup[] }) {
  const binding = golem.policyBinding;
  const policyGroupId = binding?.policyGroupId ?? golem.lastHeartbeat?.policyGroupId ?? null;
  const policyLabel = policies.find((policy) => policy.id === policyGroupId)?.slug || policyGroupId;

  return (
    <div className="mt-3 border border-border/60 bg-white/70 p-3">
      <div className="flex items-center justify-between gap-3">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">Policy control</p>
          {policyGroupId ? (
            <Link to={`/policies/${policyGroupId}`} className="mt-1 inline-flex text-sm font-semibold text-foreground hover:underline">
              {policyLabel}
            </Link>
          ) : (
            <p className="mt-1 text-sm text-muted-foreground">No policy group bound.</p>
          )}
        </div>
        {binding?.syncStatus ? (
          <span className="pill">{binding.syncStatus}</span>
        ) : null}
      </div>

      {policyGroupId ? (
        <div className="mt-3 grid gap-1 text-xs md:grid-cols-2">
          <Row label="Target" value={binding?.targetVersion ? `Target v${binding.targetVersion}` : '—'} />
          <Row
            label="Applied"
            value={binding?.appliedVersion ? `Applied v${binding.appliedVersion}` : 'Applied —'}
          />
          <Row label="Drift since" value={formatTimestamp(binding?.driftSince || null)} />
          <Row label="Last apply" value={formatTimestamp(binding?.lastAppliedAt || null)} />
          <Row label="Heartbeat sync" value={golem.lastHeartbeat?.syncStatus || 'n/a'} />
          <Row label="Last error" value={binding?.lastErrorDigest || golem.lastHeartbeat?.lastPolicyErrorDigest || '—'} />
        </div>
      ) : null}
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
  golemId,
  state,
  pauseReason,
  revokeReason,
  isBusy,
  onPause,
  onResume,
  onRevoke,
}: {
  golemId: string;
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
      {state === 'ONLINE' ? (
        <Link
          to={`/fleet/inspection/${golemId}`}
          className="border border-border bg-white/80 px-3 py-1 text-xs font-semibold text-foreground transition hover:bg-white"
        >
          Inspect
        </Link>
      ) : null}
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
