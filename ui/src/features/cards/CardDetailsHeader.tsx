import { Link } from 'react-router-dom';
import type { CardDetail } from '../../lib/api/cardsApi';
import { AssignmentPolicyBadge } from './AssignmentPolicyBadge';

export function CardDetailsHeader({
  card,
  onClose,
}: {
  card: CardDetail;
  onClose: () => void;
}) {
  return (
    <div className="flex items-start justify-between gap-4">
      <div>
        <div className="flex flex-wrap items-center gap-2">
          <span className="pill">Card detail</span>
          <AssignmentPolicyBadge policy={card.assignmentPolicy} />
          <span className="rounded-full border border-border bg-white/80 px-2.5 py-1 text-[10px] font-semibold uppercase tracking-[0.16em] text-muted-foreground">
            {card.columnId}
          </span>
        </div>
        <h2 className="mt-3 text-3xl font-bold tracking-[-0.04em] text-foreground">{card.title}</h2>
        <p className="mt-2 text-sm text-muted-foreground">
          {card.id} · thread {card.threadId} · column {card.columnId}
        </p>
        <p className="mt-1 text-sm text-muted-foreground">
          {card.assigneeGolemId ? `Assigned to ${card.assigneeGolemId}` : 'No assignee selected yet.'}
        </p>
      </div>
      <div className="flex flex-wrap gap-2">
        <Link
          to={`/cards/${card.id}/thread`}
          className="rounded-full border border-border bg-white/85 px-4 py-2 text-sm font-semibold text-foreground"
        >
          Open thread
        </Link>
        <button
          type="button"
          onClick={onClose}
          className="rounded-full border border-border bg-white/85 px-3 py-2 text-sm font-semibold text-foreground"
        >
          Close
        </button>
      </div>
    </div>
  );
}
