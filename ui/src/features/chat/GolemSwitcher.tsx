import type { ThreadTargetGolem } from '../../lib/api/threadsApi';
import { GolemStatusBadge } from '../golems/GolemStatusBadge';

interface GolemSwitcherProps {
  targetGolem: ThreadTargetGolem | null;
}

export function GolemSwitcher({ targetGolem }: GolemSwitcherProps) {
  return (
    <section className="panel p-4">
      <h3 className="text-base font-bold tracking-tight text-foreground">Target golem</h3>
      {targetGolem ? (
        <div className="mt-3 rounded-lg border border-border bg-white/70 p-3">
          <div className="flex items-center justify-between gap-3">
            <p className="text-sm font-semibold text-foreground">{targetGolem.displayName}</p>
            <GolemStatusBadge state={targetGolem.state} />
          </div>
          <p className="mt-1 text-xs text-muted-foreground">
            {targetGolem.roleSlugs.length ? targetGolem.roleSlugs.join(', ') : 'No roles'}
          </p>
        </div>
      ) : (
        <p className="mt-3 text-sm text-muted-foreground">Unassigned — assign on the board to dispatch.</p>
      )}
    </section>
  );
}
