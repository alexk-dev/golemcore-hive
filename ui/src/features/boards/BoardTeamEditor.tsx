import { useEffect, useMemo, useState } from 'react';
import { BoardDetail, BoardTeamResolved } from '../../lib/api/boardsApi';
import { GolemRole, GolemSummary } from '../../lib/api/golemsApi';

type BoardTeamEditorProps = {
  board: BoardDetail;
  golems: GolemSummary[];
  roles: GolemRole[];
  resolvedTeam: BoardTeamResolved | null;
  isPending: boolean;
  onSave: (input: { explicitGolemIds: string[]; filters: { type: string; value: string }[] }) => Promise<void>;
};

export function BoardTeamEditor({ board, golems, roles, resolvedTeam, isPending, onSave }: BoardTeamEditorProps) {
  const [explicitIds, setExplicitIds] = useState<string[]>([]);
  const [roleSlugs, setRoleSlugs] = useState<string[]>([]);
  const [golemQuery, setGolemQuery] = useState('');

  useEffect(() => {
    setExplicitIds(board.team.explicitGolemIds || []);
    setRoleSlugs((board.team.filters || []).filter((filter) => filter.type === 'ROLE_SLUG').map((filter) => filter.value));
  }, [board]);

  const resolvedIds = useMemo(() => new Set(resolvedTeam?.candidates.map((candidate) => candidate.golemId) ?? []), [resolvedTeam]);
  const visibleGolems = useMemo(() => {
    const query = golemQuery.trim().toLowerCase();
    if (!query) {
      return golems;
    }
    return golems.filter((golem) => {
      return golem.displayName.toLowerCase().includes(query) || golem.id.toLowerCase().includes(query);
    });
  }, [golemQuery, golems]);

  return (
    <section className="panel p-6 md:p-8">
      <div className="flex items-start justify-between gap-4">
        <div>
          <span className="pill">Board team</span>
          <h3 className="mt-4 text-2xl font-bold tracking-[-0.04em] text-foreground">Mix explicit members with role-based visibility</h3>
        </div>
        <button
          type="button"
          disabled={isPending}
          onClick={() =>
            void onSave({
              explicitGolemIds: explicitIds,
              filters: roleSlugs.map((value) => ({ type: 'ROLE_SLUG', value })),
            })
          }
          className="rounded-full bg-foreground px-4 py-2 text-sm font-semibold text-white disabled:opacity-60"
        >
          Save team
        </button>
      </div>

      <div className="mt-6 grid gap-6 xl:grid-cols-2">
        <div className="grid gap-3">
          <div className="flex items-center justify-between gap-3">
            <p className="text-sm font-semibold text-foreground">Explicit golems</p>
            <input
              value={golemQuery}
              onChange={(event) => setGolemQuery(event.target.value)}
              placeholder="Filter by name or id"
              className="min-w-[220px] rounded-full border border-border bg-white px-4 py-2 text-sm outline-none focus:border-primary"
            />
          </div>
          {visibleGolems.map((golem) => {
            const checked = explicitIds.includes(golem.id);
            return (
              <label key={golem.id} className="flex items-start gap-3 rounded-[18px] border border-border/70 bg-white/70 p-3">
                <input
                  type="checkbox"
                  checked={checked}
                  onChange={() =>
                    setExplicitIds((current) =>
                      checked ? current.filter((value) => value !== golem.id) : [...current, golem.id],
                    )
                  }
                  className="mt-1 h-4 w-4 rounded border-border text-primary focus:ring-primary"
                />
                <span className="flex-1">
                  <span className="block text-sm font-semibold text-foreground">{golem.displayName}</span>
                  <span className="block text-xs uppercase tracking-[0.16em] text-muted-foreground">{golem.id}</span>
                </span>
              </label>
            );
          })}
          {!visibleGolems.length ? (
            <div className="rounded-[18px] border border-dashed border-border px-4 py-6 text-sm text-muted-foreground">
              No golems match the current filter.
            </div>
          ) : null}
        </div>

        <div className="grid gap-3">
          <p className="text-sm font-semibold text-foreground">Role filters</p>
          {roles.length ? (
            roles.map((role) => {
              const checked = roleSlugs.includes(role.slug);
              return (
                <label key={role.slug} className="flex items-start gap-3 rounded-[18px] border border-border/70 bg-white/70 p-3">
                  <input
                    type="checkbox"
                    checked={checked}
                    onChange={() =>
                      setRoleSlugs((current) =>
                        checked ? current.filter((value) => value !== role.slug) : [...current, role.slug],
                      )
                    }
                    className="mt-1 h-4 w-4 rounded border-border text-primary focus:ring-primary"
                  />
                  <span className="flex-1">
                    <span className="block text-sm font-semibold text-foreground">{role.name}</span>
                    <span className="block text-xs uppercase tracking-[0.16em] text-muted-foreground">{role.slug}</span>
                    {role.description ? <span className="mt-2 block text-sm leading-6 text-muted-foreground">{role.description}</span> : null}
                  </span>
                </label>
              );
            })
          ) : (
            <div className="rounded-[18px] border border-dashed border-border px-4 py-6 text-sm text-muted-foreground">
              Create golem roles first in Fleet → Roles.
            </div>
          )}
        </div>
      </div>

      <div className="mt-6 rounded-[24px] border border-border bg-muted/40 p-4">
        <p className="text-sm font-semibold text-foreground">Resolved team preview</p>
        <div className="mt-3 grid gap-3">
          {resolvedTeam?.candidates.length ? (
            resolvedTeam.candidates.map((candidate) => (
              <article
                key={candidate.golemId}
                className={[
                  'rounded-[18px] border px-4 py-3',
                  resolvedIds.has(candidate.golemId) ? 'bg-primary/10 text-foreground' : 'bg-muted text-muted-foreground',
                ].join(' ')}
              >
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <span className="text-sm font-semibold text-foreground">{candidate.displayName}</span>
                  <span className="text-xs uppercase tracking-[0.16em] text-muted-foreground">{candidate.golemId}</span>
                </div>
                <p className="mt-2 text-sm text-muted-foreground">{candidate.reasons.join(' • ')}</p>
              </article>
            ))
          ) : (
            <span className="text-sm text-muted-foreground">No resolved candidates yet.</span>
          )}
        </div>
      </div>
    </section>
  );
}
