import { useVirtualizer } from '@tanstack/react-virtual';
import { useMemo, useRef } from 'react';
import { Link } from 'react-router-dom';
import type { GolemRole, GolemSummary } from '../../lib/api/golemsApi';
import { GolemStatusBadge } from './GolemStatusBadge';
import { formatTimestamp } from '../../lib/format';

const fleetStates = ['', 'ONLINE', 'DEGRADED', 'OFFLINE', 'PAUSED', 'REVOKED', 'PENDING_ENROLLMENT'];
const GOLEM_ROW_HEIGHT = 32;

const STATE_ORDER: Record<string, number> = {
  OFFLINE: 0,
  DEGRADED: 1,
  PAUSED: 2,
  REVOKED: 3,
  PENDING_ENROLLMENT: 4,
  ONLINE: 5,
};

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
    <div className="grid gap-2 md:grid-cols-[minmax(0,1fr)_180px_180px]">
      <input
        value={query}
        onChange={(event) => onQueryChange(event.target.value)}
        placeholder="Search by name, host, or id"
        className="border border-border bg-white/90 px-3 py-1.5 text-sm outline-none transition focus:border-primary"
      />
      <select
        value={stateFilter}
        onChange={(event) => onStateFilterChange(event.target.value)}
        className="border border-border bg-white/90 px-3 py-1.5 text-sm outline-none transition focus:border-primary"
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
        className="border border-border bg-white/90 px-3 py-1.5 text-sm outline-none transition focus:border-primary"
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
  policyNameById,
  selectedGolemId,
  onSelect,
}: {
  golems: GolemSummary[];
  policyNameById: Record<string, string>;
  selectedGolemId: string | null;
  onSelect: (golemId: string) => void;
}) {
  const sorted = useMemo(() => {
    return [...golems].sort((a, b) => {
      const orderA = STATE_ORDER[a.state] ?? 5;
      const orderB = STATE_ORDER[b.state] ?? 5;
      if (orderA !== orderB) {
        return orderA - orderB;
      }
      return a.displayName.localeCompare(b.displayName, undefined, { sensitivity: 'base' });
    });
  }, [golems]);

  const parentRef = useRef<HTMLDivElement>(null);
  const virtualizer = useVirtualizer({
    count: sorted.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => GOLEM_ROW_HEIGHT,
    overscan: 20,
  });

  return (
    <div>
      <p className="mb-1 text-xs text-muted-foreground">{sorted.length} golems</p>
      {sorted.length ? (
        <div className="border border-border/70">
          <div ref={parentRef} className="max-h-[70vh] overflow-auto">
            <div style={{ height: virtualizer.getTotalSize(), position: 'relative' }}>
              {virtualizer.getVirtualItems().map((virtualRow) => {
                const golem = sorted[virtualRow.index];
                const selected = golem.id === selectedGolemId;
                return (
                  <button
                    key={golem.id}
                    type="button"
                    onClick={() => onSelect(golem.id)}
                    className={[
                      'absolute left-0 flex w-full items-center gap-3 px-3 text-left text-sm transition',
                      selected ? 'bg-primary/5' : 'bg-white/70 hover:bg-white',
                    ].join(' ')}
                    style={{ height: GOLEM_ROW_HEIGHT, top: virtualRow.start }}
                  >
                    <GolemStatusBadge state={golem.state} />
                    <span className="min-w-0 flex-1 truncate font-semibold text-foreground">{golem.displayName}</span>
                    <span className="hidden shrink-0 text-xs text-muted-foreground sm:inline">{golem.hostLabel || golem.id}</span>
                    <span className="hidden shrink-0 text-xs text-muted-foreground md:inline">{golem.roleSlugs.join(', ') || '—'}</span>
                    <span className="hidden shrink-0 text-xs text-muted-foreground lg:inline">
                      {formatPolicyRolloutLabel(golem, policyNameById)}
                    </span>
                    <span className="shrink-0 text-xs text-muted-foreground">{formatTimestamp(golem.lastSeenAt)}</span>
                    <Link
                      to={`/fleet/chat/${golem.id}`}
                      onClick={(event) => event.stopPropagation()}
                      className="shrink-0 text-xs font-semibold text-primary hover:underline"
                    >
                      Chat
                    </Link>
                    {golem.state === 'ONLINE' ? (
                      <Link
                        to={`/fleet/inspection/${golem.id}`}
                        onClick={(event) => event.stopPropagation()}
                        className="shrink-0 text-xs font-semibold text-foreground hover:underline"
                      >
                        Inspect
                      </Link>
                    ) : null}
                  </button>
                );
              })}
            </div>
          </div>
        </div>
      ) : (
        <p className="py-4 text-sm text-muted-foreground">
          No golems enrolled. Create an enrollment token to get started.
        </p>
      )}
    </div>
  );
}

function formatPolicyRolloutLabel(golem: GolemSummary, policyNameById: Record<string, string>) {
  if (!golem.policyBinding) {
    return 'No policy';
  }
  const policyLabel = policyNameById[golem.policyBinding.policyGroupId] || golem.policyBinding.policyGroupId;
  const appliedVersion = golem.policyBinding.appliedVersion ? `v${golem.policyBinding.appliedVersion}` : '—';
  return `${policyLabel} · ${golem.policyBinding.syncStatus || 'UNKNOWN'} · v${golem.policyBinding.targetVersion}/${appliedVersion}`;
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
          <h3 className="text-sm font-bold text-foreground">{title}</h3>
          <button
            type="button"
            onClick={onCancel}
            className="border border-border bg-white/70 px-2 py-1 text-xs font-semibold text-foreground"
          >
            Close
          </button>
        </div>

        <form
          className="mt-3 grid gap-3"
          onSubmit={(event) => {
            event.preventDefault();
            void onSubmit();
          }}
        >
          <label className="grid gap-1">
            <span className="text-sm font-semibold text-foreground">Reason</span>
            <textarea
              value={reason}
              onChange={(event) => onReasonChange(event.target.value)}
              rows={2}
              className="border border-border bg-white/90 px-3 py-2 text-sm outline-none transition focus:border-primary"
              placeholder="Optional reason"
            />
          </label>
          <div className="flex justify-end gap-2">
            <button
              type="button"
              onClick={onCancel}
              className="border border-border bg-white/80 px-3 py-1.5 text-sm font-semibold text-foreground"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isPending}
              className="bg-foreground px-3 py-1.5 text-sm font-semibold text-white disabled:opacity-60"
            >
              {submitLabel}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
