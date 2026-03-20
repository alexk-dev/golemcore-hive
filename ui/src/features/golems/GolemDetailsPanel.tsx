import type { GolemDetails, GolemRole } from '../../lib/api/golemsApi';
import { GolemStatusBadge } from './GolemStatusBadge';

interface GolemDetailsPanelProps {
  golem: GolemDetails | null;
  roles: GolemRole[];
  isBusy: boolean;
  onToggleRole: (roleSlug: string, nextAssigned: boolean) => Promise<void>;
  onPause: () => void | Promise<void>;
  onResume: () => Promise<void>;
  onRevoke: () => void | Promise<void>;
}

function formatTimestamp(value: string | null) {
  if (!value) {
    return 'Never';
  }
  return new Date(value).toLocaleString();
}

function PresenceCard({ golem }: { golem: GolemDetails }) {
  return (
    <div className="section-surface p-4">
      <p className="text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground">Presence</p>
      <dl className="mt-4 grid gap-3 text-sm">
        <div className="flex items-center justify-between gap-3">
          <dt className="text-muted-foreground">Last heartbeat</dt>
          <dd className="font-medium text-foreground">{formatTimestamp(golem.lastHeartbeatAt)}</dd>
        </div>
        <div className="flex items-center justify-between gap-3">
          <dt className="text-muted-foreground">Last seen</dt>
          <dd className="font-medium text-foreground">{formatTimestamp(golem.lastSeenAt)}</dd>
        </div>
        <div className="flex items-center justify-between gap-3">
          <dt className="text-muted-foreground">Missed heartbeats</dt>
          <dd className="font-medium text-foreground">{golem.missedHeartbeatCount}</dd>
        </div>
        <div className="flex items-center justify-between gap-3">
          <dt className="text-muted-foreground">Heartbeat interval</dt>
          <dd className="font-medium text-foreground">{golem.heartbeatIntervalSeconds}s</dd>
        </div>
      </dl>
    </div>
  );
}

function RuntimeCard({ golem }: { golem: GolemDetails }) {
  return (
    <div className="section-surface p-4">
      <p className="text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground">Runtime</p>
      <dl className="mt-4 grid gap-3 text-sm">
        <div className="flex items-center justify-between gap-3">
          <dt className="text-muted-foreground">Build</dt>
          <dd className="font-medium text-foreground">{golem.buildVersion || 'n/a'}</dd>
        </div>
        <div className="flex items-center justify-between gap-3">
          <dt className="text-muted-foreground">Control URL</dt>
          <dd className="truncate font-medium text-foreground">{golem.controlChannelUrl || 'n/a'}</dd>
        </div>
        <div className="flex items-center justify-between gap-3">
          <dt className="text-muted-foreground">Channels</dt>
          <dd className="text-right font-medium text-foreground">
            {golem.supportedChannels.length ? golem.supportedChannels.join(', ') : 'n/a'}
          </dd>
        </div>
        <div className="flex items-center justify-between gap-3">
          <dt className="text-muted-foreground">Model tier</dt>
          <dd className="font-medium text-foreground">{golem.lastHeartbeat?.modelTier || 'n/a'}</dd>
        </div>
      </dl>
    </div>
  );
}

function RolesCard({
  golem,
  roles,
  isBusy,
  onToggleRole,
}: {
  golem: GolemDetails;
  roles: GolemRole[];
  isBusy: boolean;
  onToggleRole: (roleSlug: string, nextAssigned: boolean) => Promise<void>;
}) {
  return (
    <section className="section-surface p-4">
      <div className="flex items-center justify-between gap-3">
        <p className="text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground">Assigned roles</p>
        <span className="text-sm font-medium text-muted-foreground">{golem.roleSlugs.length} bound</span>
      </div>
      <div className="mt-4 grid gap-3">
        {roles.length ? (
          roles.map((role) => {
            const checked = golem.roleSlugs.includes(role.slug);
            return (
              <label
                key={role.slug}
                className="flex items-start gap-3 border border-border/70 bg-white/70 p-3"
              >
                <input
                  type="checkbox"
                  checked={checked}
                  disabled={isBusy}
                  onChange={() => void onToggleRole(role.slug, !checked)}
                  className="mt-1 h-4 w-4 rounded border-border text-primary focus:ring-primary"
                />
                <span className="flex-1">
                  <span className="block text-sm font-semibold text-foreground">{role.name}</span>
                  <span className="block text-xs uppercase tracking-[0.16em] text-muted-foreground">{role.slug}</span>
                  {role.description ? (
                    <span className="mt-2 block text-sm leading-6 text-muted-foreground">{role.description}</span>
                  ) : null}
                </span>
              </label>
            );
          })
        ) : (
          <p className="text-sm leading-6 text-muted-foreground">No golem roles exist yet. Create them in the role catalog.</p>
        )}
      </div>
    </section>
  );
}

function CapabilitiesCard({ golem }: { golem: GolemDetails }) {
  return (
    <section className="section-surface p-4">
      <p className="text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground">Capabilities</p>
      <div className="mt-4 space-y-4 text-sm">
        <div>
          <p className="font-semibold text-foreground">Providers</p>
          <p className="mt-1 leading-6 text-muted-foreground">
            {golem.capabilities?.providers.length ? golem.capabilities.providers.join(', ') : 'n/a'}
          </p>
        </div>
        <div>
          <p className="font-semibold text-foreground">Tools</p>
          <p className="mt-1 leading-6 text-muted-foreground">
            {golem.capabilities?.enabledTools.length ? golem.capabilities.enabledTools.join(', ') : 'n/a'}
          </p>
        </div>
        <div>
          <p className="font-semibold text-foreground">Autonomy</p>
          <p className="mt-1 leading-6 text-muted-foreground">
            {golem.capabilities?.enabledAutonomyFeatures.length
              ? golem.capabilities.enabledAutonomyFeatures.join(', ')
              : 'n/a'}
          </p>
        </div>
        <div>
          <p className="font-semibold text-foreground">Snapshot hash</p>
          <p className="mt-1 break-all leading-6 text-muted-foreground">{golem.capabilities?.snapshotHash || 'n/a'}</p>
        </div>
      </div>
    </section>
  );
}

function hasVisibleCapabilities(golem: GolemDetails) {
  const capabilities = golem.capabilities;
  if (!capabilities) {
    return false;
  }

  return Boolean(
    capabilities.providers.length ||
      capabilities.modelFamilies.length ||
      capabilities.enabledTools.length ||
      capabilities.enabledAutonomyFeatures.length ||
      capabilities.capabilityTags.length ||
      capabilities.supportedChannels.length ||
      capabilities.snapshotHash ||
      capabilities.defaultModel,
  );
}

function LifecycleActions({
  golem,
  isBusy,
  onPause,
  onResume,
  onRevoke,
}: {
  golem: GolemDetails;
  isBusy: boolean;
  onPause: () => void | Promise<void>;
  onResume: () => Promise<void>;
  onRevoke: () => void | Promise<void>;
}) {
  return (
    <section className="mt-6 border border-border/80 bg-muted/40 p-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground">Lifecycle actions</p>
          <p className="mt-2 text-sm leading-6 text-muted-foreground">
            Pause keeps the golem registered but unavailable. Revoke cuts the instance out of the fleet.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          {golem.state === 'PAUSED' ? (
            <button
              type="button"
              onClick={() => void onResume()}
              disabled={isBusy}
              className="bg-accent px-4 py-2 text-sm font-semibold text-accent-foreground disabled:opacity-60"
            >
              Resume
            </button>
          ) : (
            <button
              type="button"
              onClick={() => void onPause()}
              disabled={isBusy || golem.state === 'REVOKED'}
              className="bg-foreground px-4 py-2 text-sm font-semibold text-white disabled:opacity-60"
            >
              Pause
            </button>
          )}
          <button
            type="button"
            onClick={() => void onRevoke()}
            disabled={isBusy || golem.state === 'REVOKED'}
            className="border border-rose-300 bg-rose-100 px-4 py-2 text-sm font-semibold text-rose-900 disabled:opacity-60"
          >
            Revoke
          </button>
        </div>
      </div>
      {golem.pauseReason ? <p className="mt-3 text-sm text-sky-900">Pause reason: {golem.pauseReason}</p> : null}
      {golem.revokeReason ? <p className="mt-3 text-sm text-rose-900">Revoke reason: {golem.revokeReason}</p> : null}
    </section>
  );
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
      <article className="panel p-6 md:p-8">
        <p className="text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground">Fleet detail</p>
        <h2 className="mt-2 text-2xl font-semibold tracking-[-0.03em] text-foreground">No golem selected</h2>
        <p className="mt-2 text-sm text-muted-foreground">
          Pick a registered runtime from the fleet list or mint an enrollment token to bootstrap a new one.
        </p>
      </article>
    );
  }

  return (
    <article className="panel p-6 md:p-8">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground">Fleet detail</p>
          <h2 className="mt-2 text-3xl font-bold tracking-[-0.04em] text-foreground">{golem.displayName}</h2>
          <p className="mt-2 text-sm text-muted-foreground">
            {golem.hostLabel || 'No host label'} · {golem.runtimeVersion || 'runtime n/a'}
          </p>
        </div>
        <GolemStatusBadge state={golem.state} />
      </div>

      <div className="mt-6 grid gap-4 md:grid-cols-2">
        <PresenceCard golem={golem} />
        <RuntimeCard golem={golem} />
      </div>

      <div className="mt-6 grid gap-4 lg:grid-cols-[1.1fr_0.9fr]">
        <RolesCard golem={golem} roles={roles} isBusy={isBusy} onToggleRole={onToggleRole} />
        {hasVisibleCapabilities(golem) ? <CapabilitiesCard golem={golem} /> : null}
      </div>

      <LifecycleActions golem={golem} isBusy={isBusy} onPause={onPause} onResume={onResume} onRevoke={onRevoke} />
    </article>
  );
}
