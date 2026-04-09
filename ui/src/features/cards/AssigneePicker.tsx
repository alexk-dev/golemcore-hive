import { useMemo, useState } from 'react';
import type { CardAssigneeOptions } from '../../lib/api/cardsApi';
import type { GolemSummary } from '../../lib/api/golemsApi';
import { GolemStatusBadge } from '../golems/GolemStatusBadge';

interface AssigneePickerProps {
  options: CardAssigneeOptions | null;
  allGolems: GolemSummary[];
  currentAssigneeId: string | null;
  isPending: boolean;
  onAssign: (assigneeGolemId: string | null) => void | Promise<void>;
}

type PickerTab = 'team' | 'all';

export function AssigneePicker({ options, allGolems, currentAssigneeId, isPending, onAssign }: AssigneePickerProps) {
  const [activeTab, setActiveTab] = useState<PickerTab>('team');
  const [query, setQuery] = useState('');

  const visibleTeam = useMemo(() => {
    return (options?.teamCandidates ?? []).filter((candidate) =>
      candidate.displayName.toLowerCase().includes(query.toLowerCase()) || candidate.golemId.toLowerCase().includes(query.toLowerCase()),
    );
  }, [options?.teamCandidates, query]);

  const visibleAll = useMemo(() => {
    if (options?.allCandidates) {
      return options.allCandidates.filter((candidate) =>
        candidate.displayName.toLowerCase().includes(query.toLowerCase()) || candidate.golemId.toLowerCase().includes(query.toLowerCase()),
      );
    }
    return allGolems
      .filter((golem) => golem.displayName.toLowerCase().includes(query.toLowerCase()) || golem.id.toLowerCase().includes(query.toLowerCase()))
      .map((golem) => ({
        golemId: golem.id,
        displayName: golem.displayName,
        state: golem.state,
        score: 0,
        reasons: ['Available from global fleet'],
        roleSlugs: golem.roleSlugs,
        inBoardTeam: false,
      }));
  }, [allGolems, options?.allCandidates, query]);

  const items = activeTab === 'team' ? visibleTeam : visibleAll;

  return (
    <div className="grid gap-2">
      <div className="flex flex-wrap items-center gap-2">
        <button
          type="button"
          onClick={() => setActiveTab('team')}
          className={[
            ' px-3 py-1.5 text-sm font-semibold transition',
            activeTab === 'team' ? 'bg-primary text-primary-foreground' : 'border border-border bg-panel text-foreground',
          ].join(' ')}
        >
          Team
        </button>
        <button
          type="button"
          onClick={() => setActiveTab('all')}
          className={[
            ' px-3 py-1.5 text-sm font-semibold transition',
            activeTab === 'all' ? 'bg-primary text-primary-foreground' : 'border border-border bg-panel text-foreground',
          ].join(' ')}
        >
          All
        </button>
        <input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Filter golems"
          className="ml-auto min-w-[180px] border border-border bg-panel px-3 py-1.5 text-sm outline-none focus:border-primary"
        />
      </div>

      <div className="grid gap-2">
        <button
          type="button"
          disabled={isPending}
          onClick={() => void onAssign(null)}
          className={[
            ' border p-3 text-left transition',
            currentAssigneeId === null ? 'border-primary/40 bg-primary/5' : 'border-border/70 bg-muted/70',
          ].join(' ')}
        >
          <span className="text-sm font-semibold text-foreground">Unassigned</span>
        </button>

        {items.length ? (
          items.map((candidate) => (
            <button
              key={`${activeTab}-${candidate.golemId}`}
              type="button"
              disabled={isPending}
              onClick={() => void onAssign(candidate.golemId)}
              className={[
                ' border p-3 text-left transition',
                currentAssigneeId === candidate.golemId ? 'border-primary/40 bg-primary/5' : 'border-border/70 bg-muted/70',
              ].join(' ')}
            >
              <div className="flex items-center justify-between gap-2">
                <span className="text-sm font-semibold text-foreground">{candidate.displayName}</span>
                <GolemStatusBadge state={candidate.state} />
              </div>
              <p className="mt-1 text-xs text-muted-foreground">
                {candidate.roleSlugs.length ? candidate.roleSlugs.join(', ') : 'No roles'}
                {' · '}
                {candidate.reasons.join(' · ')}
              </p>
            </button>
          ))
        ) : (
          <p className="py-4 text-sm text-muted-foreground">No golems match the filter.</p>
        )}
      </div>
    </div>
  );
}
