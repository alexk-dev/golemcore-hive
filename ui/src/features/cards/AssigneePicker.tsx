import { useMemo, useState } from 'react';
import { CardAssigneeOptions } from '../../lib/api/cardsApi';
import { GolemSummary } from '../../lib/api/golemsApi';
import { GolemStatusBadge } from '../golems/GolemStatusBadge';

type AssigneePickerProps = {
  options: CardAssigneeOptions | null;
  allGolems: GolemSummary[];
  currentAssigneeId: string | null;
  isPending: boolean;
  onAssign: (assigneeGolemId: string | null) => void | Promise<void>;
};

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
    <div className="grid gap-3">
      <div className="flex flex-wrap items-center gap-2">
        <button
          type="button"
          onClick={() => setActiveTab('team')}
          className={[
            'rounded-full px-4 py-2 text-sm font-semibold transition',
            activeTab === 'team' ? 'bg-foreground text-white' : 'border border-border bg-white text-foreground',
          ].join(' ')}
        >
          Team
        </button>
        <button
          type="button"
          onClick={() => setActiveTab('all')}
          className={[
            'rounded-full px-4 py-2 text-sm font-semibold transition',
            activeTab === 'all' ? 'bg-foreground text-white' : 'border border-border bg-white text-foreground',
          ].join(' ')}
        >
          All
        </button>
        <input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Filter golems"
          className="ml-auto min-w-[220px] rounded-full border border-border bg-white px-4 py-2 text-sm outline-none focus:border-primary"
        />
      </div>

      <div className="grid gap-3">
        <button
          type="button"
          disabled={isPending}
          onClick={() => void onAssign(null)}
          className={[
            'rounded-[18px] border p-3 text-left transition',
            currentAssigneeId === null ? 'border-primary/40 bg-primary/5' : 'border-border/70 bg-white/70',
          ].join(' ')}
        >
          <span className="block text-sm font-semibold text-foreground">Unassigned</span>
          <span className="mt-1 block text-sm text-muted-foreground">Leave the card without a specific executor.</span>
        </button>

        {items.length ? (
          items.map((candidate) => (
            <button
              key={`${activeTab}-${candidate.golemId}`}
              type="button"
              disabled={isPending}
              onClick={() => void onAssign(candidate.golemId)}
              className={[
                'rounded-[18px] border p-3 text-left transition',
                currentAssigneeId === candidate.golemId ? 'border-primary/40 bg-primary/5' : 'border-border/70 bg-white/70',
              ].join(' ')}
            >
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <span className="block text-sm font-semibold text-foreground">{candidate.displayName}</span>
                  <span className="mt-1 block text-xs uppercase tracking-[0.16em] text-muted-foreground">{candidate.golemId}</span>
                </div>
                <GolemStatusBadge state={candidate.state} />
              </div>
              <p className="mt-2 text-sm text-muted-foreground">
                {candidate.roleSlugs.length ? candidate.roleSlugs.join(', ') : 'No roles'}
              </p>
              <p className="mt-2 text-sm leading-6 text-muted-foreground">{candidate.reasons.join(' • ')}</p>
            </button>
          ))
        ) : (
          <div className="rounded-[18px] border border-dashed border-border px-4 py-6 text-sm text-muted-foreground">
            No golems match the current filter.
          </div>
        )}
      </div>
    </div>
  );
}
