import { useEffect, useMemo, useState } from 'react';
import type { BoardDetail, BoardTeamResolved } from '../../lib/api/boardsApi';
import type { GolemRole, GolemSummary } from '../../lib/api/golemsApi';

interface BoardTeamEditorProps {
  board: BoardDetail;
  golems: GolemSummary[];
  roles: GolemRole[];
  resolvedTeam: BoardTeamResolved | null;
  isPending: boolean;
  onSave: (input: { explicitGolemIds: string[]; filters: { type: string; value: string }[] }) => Promise<void>;
}

export function BoardTeamEditor({ board, golems, roles, resolvedTeam, isPending, onSave }: BoardTeamEditorProps) {
  const [explicitIds, setExplicitIds] = useState<string[]>([]);
  const [roleSlugs, setRoleSlugs] = useState<string[]>([]);
  const [golemQuery, setGolemQuery] = useState('');

  useEffect(() => {
    setExplicitIds(board.team.explicitGolemIds || []);
    setRoleSlugs((board.team.filters || []).filter((filter) => filter.type === 'ROLE_SLUG').map((filter) => filter.value));
  }, [board]);

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
    <section className="panel p-5">
      <div className="flex items-center justify-between gap-3">
        <h3 className="text-base font-bold tracking-tight text-foreground">Team</h3>
        <button
          type="button"
          disabled={isPending}
          onClick={() =>
            void onSave({
              explicitGolemIds: explicitIds,
              filters: roleSlugs.map((value) => ({ type: 'ROLE_SLUG', value })),
            })
          }
          className="bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground disabled:opacity-60"
        >
          Save team
        </button>
      </div>

      <div className="mt-4 grid gap-5 xl:grid-cols-2">
        <div className="grid gap-3">
          <div className="flex items-center justify-between gap-3">
            <p className="text-sm font-semibold text-foreground">Explicit golems</p>
            <input
              value={golemQuery}
              onChange={(event) => setGolemQuery(event.target.value)}
              placeholder="Filter"
              className="min-w-[180px] border border-border bg-panel px-3 py-1.5 text-sm outline-none focus:border-primary focus:ring-1 focus:ring-primary/50"
            />
          </div>
          {visibleGolems.map((golem) => {
            const checked = explicitIds.includes(golem.id);
            return (
              <label key={golem.id} className="flex items-center gap-3 border border-border/70 bg-muted/70 p-3">
                <input
                  type="checkbox"
                  checked={checked}
                  onChange={() =>
                    setExplicitIds((current) =>
                      checked ? current.filter((value) => value !== golem.id) : [...current, golem.id],
                    )
                  }
                  className="h-4 w-4 border-border text-primary focus:ring-primary"
                />
                <span className="min-w-0 flex-1">
                  <span className="block text-sm font-semibold text-foreground">{golem.displayName}</span>
                  <span className="block text-xs text-muted-foreground">{golem.id}</span>
                </span>
              </label>
            );
          })}
          {!visibleGolems.length ? (
            <p className="text-sm text-muted-foreground">No golems match.</p>
          ) : null}
        </div>

        <div className="grid gap-3">
          <p className="text-sm font-semibold text-foreground">Role filters</p>
          {roles.length ? (
            roles.map((role) => {
              const checked = roleSlugs.includes(role.slug);
              return (
                <label key={role.slug} className="flex items-center gap-3 border border-border/70 bg-muted/70 p-3">
                  <input
                    type="checkbox"
                    checked={checked}
                    onChange={() =>
                      setRoleSlugs((current) =>
                        checked ? current.filter((value) => value !== role.slug) : [...current, role.slug],
                      )
                    }
                    className="h-4 w-4 border-border text-primary focus:ring-primary"
                  />
                  <span className="min-w-0 flex-1">
                    <span className="block text-sm font-semibold text-foreground">{role.name}</span>
                    <span className="block text-xs text-muted-foreground">{role.slug}</span>
                  </span>
                </label>
              );
            })
          ) : (
            <p className="text-sm text-muted-foreground">Create roles in Fleet first.</p>
          )}
        </div>
      </div>

      {resolvedTeam?.candidates.length ? (
        <div className="mt-5 border border-border bg-muted/40 p-4">
          <p className="text-sm font-semibold text-foreground">Resolved team ({resolvedTeam.candidates.length})</p>
          <div className="mt-3 grid gap-2">
            {resolvedTeam.candidates.map((candidate) => (
              <div key={candidate.golemId} className="flex flex-wrap items-center justify-between gap-2 text-sm">
                <span className="font-medium text-foreground">{candidate.displayName}</span>
                <span className="text-xs text-muted-foreground">{candidate.reasons.join(' · ')}</span>
              </div>
            ))}
          </div>
        </div>
      ) : null}
    </section>
  );
}
