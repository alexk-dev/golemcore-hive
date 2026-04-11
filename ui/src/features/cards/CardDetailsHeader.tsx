import { Link } from 'react-router-dom';
import type { GolemSummary } from '../../lib/api/golemsApi';
import type { CardDetail } from '../../lib/api/cardsApi';
import { formatGolemDisplayName } from '../../lib/format';
import { AssignmentPolicyBadge } from './AssignmentPolicyBadge';

export function CardDetailsHeader({
  card,
  allGolems,
  onClose,
}: {
  card: CardDetail;
  allGolems: GolemSummary[];
  onClose: () => void;
}) {
  return (
    <div className="flex items-start justify-between gap-4">
      <div className="min-w-0">
        <div className="flex flex-wrap items-center gap-2">
          <AssignmentPolicyBadge policy={card.assignmentPolicy} />
          <span className="border border-border bg-muted/60 px-2 py-1 text-[11px] font-semibold uppercase tracking-[0.16em] text-foreground">
            {card.kind ?? 'TASK'}
          </span>
          <span className="text-xs text-muted-foreground">{card.columnId}</span>
        </div>
        <h2 className="mt-2 text-2xl font-bold tracking-tight text-foreground">{card.title}</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          {card.assigneeGolemId ? `Assigned to ${formatGolemDisplayName(card.assigneeGolemId, allGolems)}` : 'Unassigned'}
        </p>
      </div>
      <div className="flex flex-wrap gap-2">
        <Link
          to={`/cards/${card.id}/thread`}
          className="border border-border bg-panel/85 px-3 py-1.5 text-sm font-semibold text-foreground"
        >
          Thread
        </Link>
        <button
          type="button"
          onClick={onClose}
          className="border border-border bg-panel/85 px-3 py-1.5 text-sm font-semibold text-foreground"
        >
          Close
        </button>
      </div>
    </div>
  );
}
