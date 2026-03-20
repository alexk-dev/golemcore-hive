import type { ThreadTargetGolem } from '../../lib/api/threadsApi';
import { GolemStatusBadge } from '../golems/GolemStatusBadge';

interface GolemSwitcherProps {
  targetGolem: ThreadTargetGolem | null;
}

export function GolemSwitcher({ targetGolem }: GolemSwitcherProps) {
  return (
    <section className="panel p-4">
      <div>
        <span className="pill">Target golem</span>
        <h3 className="mt-3 text-lg font-bold tracking-[-0.03em] text-foreground">Routing follows card assignment</h3>
      </div>
      {targetGolem ? (
        <div className="mt-4 rounded-[18px] border border-border bg-white/70 p-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <p className="text-base font-bold tracking-[-0.02em] text-foreground">{targetGolem.displayName}</p>
              <p className="mt-1 text-[11px] uppercase tracking-[0.14em] text-muted-foreground">{targetGolem.id}</p>
            </div>
            <GolemStatusBadge state={targetGolem.state} />
          </div>
          <p className="mt-2 text-sm text-muted-foreground">
            {targetGolem.roleSlugs.length ? targetGolem.roleSlugs.join(', ') : 'No roles'}.
          </p>
          <p className="mt-2 text-sm leading-6 text-muted-foreground">
            Ad-hoc thread retargeting is disabled by design. Reassign the card on the board if execution should move elsewhere.
          </p>
        </div>
      ) : (
        <div className="mt-4 rounded-[18px] border border-dashed border-border px-4 py-6 text-sm text-muted-foreground">
          This card is currently unassigned, so the thread cannot dispatch commands yet.
        </div>
      )}
    </section>
  );
}
