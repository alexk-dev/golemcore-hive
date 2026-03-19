import { useDroppable } from '@dnd-kit/core';
import { SortableContext, useSortable, verticalListSortingStrategy } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { BoardColumn } from '../../lib/api/boardsApi';
import { CardControlState, CardSummary } from '../../lib/api/cardsApi';
import { AssignmentPolicyBadge } from '../cards/AssignmentPolicyBadge';

type KanbanColumnProps = {
  column: BoardColumn;
  cards: CardSummary[];
  onOpenCard: (cardId: string) => void;
};

type SortableCardProps = {
  card: CardSummary;
  onOpenCard: (cardId: string) => void;
};

function SortableCard({ card, onOpenCard }: SortableCardProps) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id: card.id });
  const controlTone = card.controlState?.cancelRequestedPending
    ? 'border-rose-200 bg-rose-50 text-rose-900'
    : card.controlState?.runStatus === 'BLOCKED'
      ? 'border-amber-200 bg-amber-50 text-amber-900'
      : card.controlState?.runStatus === 'RUNNING'
        ? 'border-primary/20 bg-primary/10 text-foreground'
        : 'border-border bg-muted text-muted-foreground';

  return (
    <button
      ref={setNodeRef}
      type="button"
      {...attributes}
      {...listeners}
      onClick={() => onOpenCard(card.id)}
      style={{
        transform: CSS.Transform.toString(transform),
        transition,
      }}
      className={[
        'rounded-[22px] border border-border/80 bg-white/85 p-4 text-left shadow-[0_12px_30px_rgba(24,18,14,0.06)] transition hover:bg-white',
        isDragging ? 'opacity-50' : '',
      ].join(' ')}
    >
      <div className="flex items-start justify-between gap-3">
        <p className="text-base font-bold tracking-[-0.03em] text-foreground">{card.title}</p>
        <AssignmentPolicyBadge policy={card.assignmentPolicy} />
      </div>
      <p className="mt-3 text-xs uppercase tracking-[0.16em] text-muted-foreground">{card.id}</p>
      <p className="mt-3 text-sm text-muted-foreground">{card.assigneeGolemId || 'Unassigned'}</p>
      {card.controlState ? (
        <div className="mt-3 flex flex-wrap items-center gap-2">
          <span className={`rounded-full border px-3 py-1 text-xs font-semibold uppercase tracking-[0.14em] ${controlTone}`}>
            {formatControlLabel(card.controlState)}
          </span>
          {card.controlState.cancelRequestedPending && card.controlState.cancelRequestedByActorName ? (
            <span className="text-xs text-rose-900">by {card.controlState.cancelRequestedByActorName}</span>
          ) : null}
        </div>
      ) : null}
    </button>
  );
}

export function KanbanColumn({ column, cards, onOpenCard }: KanbanColumnProps) {
  const { setNodeRef, isOver } = useDroppable({
    id: `column:${column.id}`,
  });

  return (
    <section className="flex min-h-[320px] min-w-[290px] flex-col rounded-[28px] border border-border/80 bg-white/55 p-4 backdrop-blur">
      <div className="flex items-center justify-between gap-3">
        <div>
          <h3 className="text-lg font-bold tracking-[-0.03em] text-foreground">{column.name}</h3>
          <p className="mt-1 text-xs uppercase tracking-[0.16em] text-muted-foreground">{cards.length} cards</p>
        </div>
        {column.wipLimit ? (
          <span className="rounded-full bg-muted px-3 py-1 text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
            WIP {column.wipLimit}
          </span>
        ) : null}
      </div>
      {column.description ? <p className="mt-3 text-sm leading-6 text-muted-foreground">{column.description}</p> : null}
      <div
        ref={setNodeRef}
        className={[
          'mt-4 flex flex-1 flex-col gap-3 rounded-[24px] border border-dashed border-transparent p-1 transition',
          isOver ? 'border-primary/40 bg-primary/5' : '',
        ].join(' ')}
      >
        <SortableContext items={cards.map((card) => card.id)} strategy={verticalListSortingStrategy}>
          {cards.map((card) => (
            <SortableCard key={card.id} card={card} onOpenCard={onOpenCard} />
          ))}
        </SortableContext>
        {!cards.length ? (
          <div className="flex flex-1 items-center justify-center rounded-[22px] border border-dashed border-border/80 bg-white/50 px-4 py-10 text-center text-sm text-muted-foreground">
            Drop a card here
          </div>
        ) : null}
      </div>
    </section>
  );
}

function formatControlLabel(controlState: CardControlState) {
  if (controlState.cancelRequestedPending) {
    return 'Stop requested';
  }
  if (controlState.runStatus === 'PENDING_APPROVAL') {
    return 'Awaiting approval';
  }
  if (controlState.runStatus === 'QUEUED' && controlState.commandStatus === 'QUEUED') {
    return 'Queued';
  }
  if (controlState.runStatus === 'BLOCKED') {
    return 'Blocked';
  }
  if (controlState.runStatus === 'RUNNING') {
    return 'Running';
  }
  return controlState.runStatus.replace(/_/g, ' ');
}
