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
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 px-4 py-6 backdrop-blur-sm">
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
              className="border border-border bg-muted/70 px-3 py-1.5 text-sm font-semibold text-foreground"
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
  const summary = buildGolemPolicySummary(golem, policies);

  return (
    <div className="mt-3 border border-border/60 bg-muted/70 p-3">
      <GolemPolicyHeader
        policyGroupId={summary.policyGroupId}
        policyLabel={summary.policyLabel}
        syncStatus={summary.syncStatus}
      />
      {summary.rows.length ? <GolemPolicyRows rows={summary.rows} /> : null}
    </div>
  );
}

function GolemPolicyHeader({
  policyGroupId,
  policyLabel,
  syncStatus,
}: {
  policyGroupId: string | null;
  policyLabel: string | null;
  syncStatus: string | null;
}) {
  return (
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
      {syncStatus ? <span className="pill">{syncStatus}</span> : null}
    </div>
  );
}

function GolemPolicyRows({ rows }: { rows: Array<{ label: string; value: string }> }) {
  return (
    <div className="mt-3 grid gap-1 text-xs md:grid-cols-2">
      {rows.map((row) => (
        <Row key={row.label} label={row.label} value={row.value} />
      ))}
    </div>
  );
}

function buildGolemPolicySummary(golem: GolemDetails, policies: PolicyGroup[]) {
  const binding = golem.policyBinding;
  const policyGroupId = binding?.policyGroupId ?? golem.lastHeartbeat?.policyGroupId ?? null;
  const policyLabel = policyGroupId
    ? policies.find((policy) => policy.id === policyGroupId)?.slug || policyGroupId
    : null;
  const rows = policyGroupId ? buildGolemPolicyRows(golem) : [];

  return {
    policyGroupId,
    policyLabel,
    syncStatus: binding?.syncStatus ?? null,
    rows,
  };
}

function buildGolemPolicyRows(golem: GolemDetails) {
  return [
    { label: 'Target', value: formatPolicyTargetVersion(golem.policyBinding?.targetVersion ?? null) },
    { label: 'Applied', value: formatPolicyAppliedVersion(golem.policyBinding?.appliedVersion ?? null) },
    { label: 'Drift since', value: formatTimestamp(golem.policyBinding?.driftSince || null) },
    { label: 'Last apply', value: formatTimestamp(golem.policyBinding?.lastAppliedAt || null) },
    { label: 'Heartbeat sync', value: golem.lastHeartbeat?.syncStatus || 'n/a' },
    { label: 'Last error', value: resolvePolicyErrorDigest(golem) },
  ];
}

function formatPolicyTargetVersion(targetVersion: number | null) {
  if (!targetVersion) {
    return '—';
  }
  return `Target v${targetVersion}`;
}

function formatPolicyAppliedVersion(appliedVersion: number | null) {
  if (!appliedVersion) {
    return 'Applied —';
  }
  return `Applied v${appliedVersion}`;
}

function resolvePolicyErrorDigest(golem: GolemDetails) {
  return golem.policyBinding?.lastErrorDigest || golem.lastHeartbeat?.lastPolicyErrorDigest || '—';
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
          className="border border-border bg-panel/80 px-3 py-1 text-xs font-semibold text-foreground transition hover:bg-muted"
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
          className="bg-primary px-3 py-1 text-xs font-semibold text-primary-foreground disabled:opacity-60"
        >
          Pause
        </button>
      )}
      <button
        type="button"
        onClick={() => void onRevoke()}
        disabled={isBusy || state === 'REVOKED'}
        className="border border-rose-700 bg-rose-900/40 px-3 py-1 text-xs font-semibold text-rose-300 disabled:opacity-60"
      >
        Revoke
      </button>
      {pauseReason ? <span className="text-xs text-sky-900">Paused: {pauseReason}</span> : null}
      {revokeReason ? <span className="text-xs text-rose-300">Revoked: {revokeReason}</span> : null}
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
