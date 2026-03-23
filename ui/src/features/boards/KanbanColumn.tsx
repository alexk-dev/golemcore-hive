import { useDndContext, useDroppable } from '@dnd-kit/core';
import { SortableContext, useSortable, verticalListSortingStrategy } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import type { BoardColumn } from '../../lib/api/boardsApi';
import type { CardSummary } from '../../lib/api/cardsApi';
import { formatControlLabel } from '../../lib/format';

interface KanbanColumnProps {
  column: BoardColumn;
  cards: CardSummary[];
  onOpenCard: (cardId: string) => void;
}

interface SortableCardProps {
  card: CardSummary;
  onOpenCard: (cardId: string) => void;
}

function SortableCard({ card, onOpenCard }: SortableCardProps) {
  const { attributes, isDragging, isOver, listeners, setNodeRef, transform, transition } = useSortable({ id: card.id });
  const controlTone = card.controlState?.cancelRequestedPending
    ? 'bg-rose-100 text-rose-900'
    : card.controlState?.runStatus === 'BLOCKED'
      ? 'bg-amber-100 text-amber-900'
      : card.controlState?.runStatus === 'RUNNING'
        ? 'bg-primary/10 text-foreground'
        : 'bg-muted text-muted-foreground';

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
        'relative rounded-xl border border-border/70 bg-white/95 px-3 py-2.5 text-left shadow-sm transition hover:bg-white',
        isDragging ? 'opacity-50' : 'opacity-100',
        isOver ? 'ring-1 ring-primary/30' : '',
      ].join(' ')}
    >
      {isOver ? <span aria-hidden className="absolute inset-x-3 -top-1 h-0.5 rounded-full bg-primary" /> : null}
      <div className="flex items-start justify-between gap-2">
        <p className="line-clamp-2 text-sm font-semibold text-foreground">{card.title}</p>
        {card.controlState ? (
          <span className={`shrink-0 rounded-full px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide ${controlTone}`}>
            {formatControlLabel(card.controlState)}
          </span>
        ) : null}
      </div>
      <p className="mt-1 truncate text-xs text-muted-foreground">{card.assigneeGolemId || 'Unassigned'}</p>
      {card.controlState?.cancelRequestedPending && card.controlState.cancelRequestedByActorName ? (
        <p className="mt-1 text-xs text-rose-900">stop by {card.controlState.cancelRequestedByActorName}</p>
      ) : null}
    </button>
  );
}

export function KanbanColumn({ column, cards, onOpenCard }: KanbanColumnProps) {
  const { over } = useDndContext();
  const laneDrop = useDroppable({ id: `column:${column.id}` });
  const endDrop = useDroppable({ id: `column:${column.id}:end` });
  const overId = over?.id ? String(over.id) : null;
  const isLaneActive = Boolean(
    overId && (overId === `column:${column.id}` || overId === `column:${column.id}:end` || cards.some((card) => card.id === overId)),
  );
  const isPrimaryLane = column.id === 'in_progress';
  const hasCards = cards.length > 0;

  return (
    <section
      className={[
        'flex min-h-[250px] flex-col rounded-xl border p-3 transition',
        hasCards ? 'min-w-[280px]' : 'min-w-[220px]',
        isPrimaryLane ? 'border-border bg-white/88 shadow-sm' : 'border-border/70 bg-white/55',
        isLaneActive ? 'border-primary/40 bg-primary/5' : '',
      ].join(' ')}
    >
      <div className="flex items-center justify-between gap-2">
        <h3 className="text-sm font-semibold text-foreground">{column.name}</h3>
        <div className="flex items-center gap-2">
          <span className="text-xs text-muted-foreground">{cards.length}</span>
          {column.wipLimit ? (
            <span className="text-xs text-muted-foreground">WIP {column.wipLimit}</span>
          ) : null}
        </div>
      </div>
      {column.description ? <p className="mt-1 text-xs text-muted-foreground">{column.description}</p> : null}
      <div
        ref={laneDrop.setNodeRef}
        className={[
          'mt-2 flex flex-1 flex-col gap-2 rounded-lg border border-dashed p-1 transition',
          isLaneActive ? 'border-primary/35 bg-primary/5' : 'border-transparent',
        ].join(' ')}
      >
        <SortableContext items={cards.map((card) => card.id)} strategy={verticalListSortingStrategy}>
          {cards.map((card) => (
            <SortableCard key={card.id} card={card} onOpenCard={onOpenCard} />
          ))}
        </SortableContext>
        {hasCards ? (
          <div
            ref={endDrop.setNodeRef}
            className={[
              'flex min-h-7 items-center justify-center rounded-lg border border-dashed px-3 py-1.5 text-xs transition',
              endDrop.isOver ? 'border-primary/45 bg-primary/10 text-foreground' : 'border-border/60 bg-white/60 text-muted-foreground',
            ].join(' ')}
          >
            Drop to end
          </div>
        ) : (
          <div className="flex flex-1 items-center justify-center rounded-lg border border-dashed border-border/70 bg-white/45 px-4 py-6 text-xs text-muted-foreground">
            Drop here
          </div>
        )}
      </div>
    </section>
  );
}
