import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useDeferredValue, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  assignGolemRoles,
  createEnrollmentToken,
  getGolem,
  listEnrollmentTokens,
  listGolemRoles,
  listGolems,
  pauseGolem,
  revokeEnrollmentToken,
  revokeGolem,
  resumeGolem,
  unassignGolemRoles,
} from '../../lib/api/golemsApi';
import { EnrollmentTokenDialog } from './EnrollmentTokenDialog';
import { GolemDetailsPanel } from './GolemDetailsPanel';
import { GolemStatusBadge } from './GolemStatusBadge';

const fleetStates = ['', 'ONLINE', 'DEGRADED', 'OFFLINE', 'PAUSED', 'REVOKED', 'PENDING_ENROLLMENT'];

function formatTimestamp(value: string | null) {
  if (!value) {
    return 'Never';
  }
  return new Date(value).toLocaleString();
}

export function GolemsPage() {
  const queryClient = useQueryClient();
  const [query, setQuery] = useState('');
  const [stateFilter, setStateFilter] = useState('');
  const [roleFilter, setRoleFilter] = useState('');
  const [selectedGolemId, setSelectedGolemId] = useState<string | null>(null);
  const [isEnrollmentDialogOpen, setIsEnrollmentDialogOpen] = useState(false);
  const deferredQuery = useDeferredValue(query);

  const golemsQuery = useQuery({
    queryKey: ['golems', deferredQuery, stateFilter, roleFilter],
    queryFn: () => listGolems({ query: deferredQuery, state: stateFilter || undefined, role: roleFilter || undefined }),
  });

  const golemDetailsQuery = useQuery({
    queryKey: ['golem', selectedGolemId],
    queryFn: () => getGolem(selectedGolemId ?? ''),
    enabled: Boolean(selectedGolemId),
  });

  const rolesQuery = useQuery({
    queryKey: ['golem-roles'],
    queryFn: listGolemRoles,
  });

  const tokensQuery = useQuery({
    queryKey: ['enrollment-tokens'],
    queryFn: listEnrollmentTokens,
  });

  useEffect(() => {
    if (!golemsQuery.data?.length) {
      setSelectedGolemId(null);
      return;
    }
    const exists = golemsQuery.data.some((golem) => golem.id === selectedGolemId);
    if (!selectedGolemId || !exists) {
      setSelectedGolemId(golemsQuery.data[0].id);
    }
  }, [golemsQuery.data, selectedGolemId]);

  const enrollmentMutation = useMutation({
    mutationFn: async (input: { note: string; expiresInMinutes: number | null }) =>
      createEnrollmentToken(input.note, input.expiresInMinutes),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['enrollment-tokens'] });
    },
  });

  const roleBindingMutation = useMutation({
    mutationFn: async (input: { roleSlug: string; nextAssigned: boolean }) => {
      if (!selectedGolemId) {
        return;
      }
      if (input.nextAssigned) {
        await assignGolemRoles(selectedGolemId, [input.roleSlug]);
      } else {
        await unassignGolemRoles(selectedGolemId, [input.roleSlug]);
      }
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['golem', selectedGolemId] }),
        queryClient.invalidateQueries({ queryKey: ['golems'] }),
      ]);
    },
  });

  const golemActionMutation = useMutation({
    mutationFn: async (input: { action: 'pause' | 'resume' | 'revoke'; reason?: string }) => {
      if (!selectedGolemId) {
        return;
      }
      if (input.action === 'pause') {
        await pauseGolem(selectedGolemId, input.reason);
      } else if (input.action === 'resume') {
        await resumeGolem(selectedGolemId);
      } else {
        await revokeGolem(selectedGolemId, input.reason);
      }
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['golem', selectedGolemId] }),
        queryClient.invalidateQueries({ queryKey: ['golems'] }),
      ]);
    },
  });

  const revokeTokenMutation = useMutation({
    mutationFn: async (tokenId: string) => revokeEnrollmentToken(tokenId, 'Revoked by operator'),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['enrollment-tokens'] });
    },
  });

  async function handlePause() {
    const reason = window.prompt('Pause reason (optional):') ?? undefined;
    await golemActionMutation.mutateAsync({ action: 'pause', reason });
  }

  async function handleRevoke() {
    const reason = window.prompt('Revoke reason (recommended):') ?? undefined;
    await golemActionMutation.mutateAsync({ action: 'revoke', reason });
  }

  return (
    <div className="grid gap-6">
      <section className="panel p-6 md:p-8">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <span className="pill">Phase 2 live</span>
            <h2 className="mt-4 text-3xl font-bold tracking-[-0.04em] text-foreground">
              Register runtimes, bind roles, and watch fleet presence in one place.
            </h2>
            <p className="mt-3 max-w-3xl text-sm leading-7 text-muted-foreground">
              Hive now exposes operator-issued enrollment tokens, machine JWT onboarding, heartbeat-aware status, and role assignment.
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
              onClick={() => setIsEnrollmentDialogOpen(true)}
              className="rounded-full bg-foreground px-4 py-2 text-sm font-semibold text-white"
            >
              Create enrollment token
            </button>
          </div>
        </div>
      </section>

      <section className="panel p-5 md:p-6">
        <div className="grid gap-3 md:grid-cols-[minmax(0,1fr)_220px_220px]">
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search by golem name, host, or id"
            className="rounded-[20px] border border-border bg-white/90 px-4 py-3 text-sm outline-none transition focus:border-primary"
          />
          <select
            value={stateFilter}
            onChange={(event) => setStateFilter(event.target.value)}
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
            onChange={(event) => setRoleFilter(event.target.value)}
            className="rounded-[20px] border border-border bg-white/90 px-4 py-3 text-sm outline-none transition focus:border-primary"
          >
            <option value="">All roles</option>
            {rolesQuery.data?.map((role) => (
              <option key={role.slug} value={role.slug}>
                {role.slug}
              </option>
            ))}
          </select>
        </div>
      </section>

      <section className="grid gap-6 xl:grid-cols-[0.9fr_1.1fr]">
        <article className="panel p-5 md:p-6">
          <div className="flex items-center justify-between gap-3">
            <div>
              <span className="pill">Fleet registry</span>
              <h3 className="mt-3 text-2xl font-bold tracking-[-0.04em] text-foreground">
                {golemsQuery.data?.length ?? 0} registered golems
              </h3>
            </div>
          </div>
          <div className="mt-5 grid gap-3">
            {golemsQuery.data?.length ? (
              golemsQuery.data.map((golem) => {
                const selected = golem.id === selectedGolemId;
                return (
                  <button
                    key={golem.id}
                    type="button"
                    onClick={() => setSelectedGolemId(golem.id)}
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
                No golems have enrolled yet. Create an enrollment token, pass it to `golemcore-bot`, then let the bot call
                the registration endpoint.
              </div>
            )}
          </div>
        </article>

        <GolemDetailsPanel
          golem={golemDetailsQuery.data ?? null}
          roles={rolesQuery.data ?? []}
          isBusy={roleBindingMutation.isPending || golemActionMutation.isPending}
          onToggleRole={async (roleSlug, nextAssigned) => {
            await roleBindingMutation.mutateAsync({ roleSlug, nextAssigned });
          }}
          onPause={handlePause}
          onResume={async () => {
            await golemActionMutation.mutateAsync({ action: 'resume' });
          }}
          onRevoke={handleRevoke}
        />
      </section>

      <section className="panel p-6 md:p-8">
        <div className="flex items-start justify-between gap-4">
          <div>
            <span className="pill">Enrollment tokens</span>
            <h3 className="mt-4 text-2xl font-bold tracking-[-0.04em] text-foreground">Recent bootstrap secrets</h3>
          </div>
        </div>

        <div className="mt-5 grid gap-3">
          {tokensQuery.data?.length ? (
            tokensQuery.data.map((token) => (
              <div key={token.id} className="rounded-[22px] border border-border/70 bg-white/70 p-4">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <p className="text-sm font-semibold uppercase tracking-[0.16em] text-muted-foreground">{token.id}</p>
                    <p className="mt-2 text-sm leading-6 text-foreground">{token.note || token.preview}</p>
                    <p className="mt-2 text-sm text-muted-foreground">
                      Created by {token.createdByUsername || 'operator'} · expires {formatTimestamp(token.expiresAt)}
                    </p>
                    <p className="mt-1 text-sm text-muted-foreground">
                      {token.usedAt ? `Used ${formatTimestamp(token.usedAt)}` : token.revoked ? 'Revoked' : 'Unused'}
                    </p>
                  </div>
                  {!token.usedAt && !token.revoked ? (
                    <button
                      type="button"
                      disabled={revokeTokenMutation.isPending}
                      onClick={() => revokeTokenMutation.mutate(token.id)}
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

      <EnrollmentTokenDialog
        open={isEnrollmentDialogOpen}
        isPending={enrollmentMutation.isPending}
        createdToken={enrollmentMutation.data ?? null}
        onClose={() => {
          setIsEnrollmentDialogOpen(false);
          enrollmentMutation.reset();
        }}
        onCreate={async (input) => {
          await enrollmentMutation.mutateAsync(input);
        }}
      />
    </div>
  );
}
