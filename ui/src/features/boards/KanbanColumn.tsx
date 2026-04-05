import { useDndContext, useDroppable } from '@dnd-kit/core';
import { SortableContext, useSortable, verticalListSortingStrategy } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import type { BoardColumn } from '../../lib/api/boardsApi';
import type { CardSummary } from '../../lib/api/cardsApi';
import type { GolemSummary } from '../../lib/api/golemsApi';
import { formatControlLabel, formatGolemDisplayName } from '../../lib/format';

interface KanbanColumnProps {
  column: BoardColumn;
  cards: CardSummary[];
  allGolems: GolemSummary[];
  onOpenCard: (cardId: string) => void;
}

interface SortableCardProps {
  card: CardSummary;
  allGolems: GolemSummary[];
  onOpenCard: (cardId: string) => void;
}

function SortableCard({ card, allGolems, onOpenCard }: SortableCardProps) {
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
        'relative border border-border/70 bg-white/95 px-2 py-1.5 text-left transition hover:bg-white',
        isDragging ? 'opacity-50' : 'opacity-100',
        isOver ? 'ring-1 ring-primary/30' : '',
      ].join(' ')}
    >
      {isOver ? <span aria-hidden className="absolute inset-x-2 -top-px h-px bg-primary" /> : null}
      <div className="flex items-center justify-between gap-1.5">
        <p className="min-w-0 truncate text-sm font-semibold text-foreground">{card.title}</p>
        {card.controlState ? (
          <span className={`shrink-0 px-1.5 py-0.5 text-[10px] font-semibold ${controlTone}`}>
            {formatControlLabel(card.controlState)}
          </span>
        ) : null}
      </div>
      <p className="truncate text-xs text-muted-foreground">{formatGolemDisplayName(card.assigneeGolemId, allGolems)}</p>
      {card.controlState?.cancelRequestedPending && card.controlState.cancelRequestedByActorName ? (
        <p className="text-xs text-rose-900">stop by {card.controlState.cancelRequestedByActorName}</p>
      ) : null}
    </button>
  );
}

export function KanbanColumn({ column, cards, allGolems, onOpenCard }: KanbanColumnProps) {
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
        'flex min-h-[200px] flex-col border p-2 transition',
        hasCards ? 'min-w-[240px]' : 'min-w-[200px]',
        isPrimaryLane ? 'border-border bg-white/88' : 'border-border/70 bg-white/55',
        isLaneActive ? 'border-primary/40 bg-primary/5' : '',
      ].join(' ')}
    >
      <div className="flex items-center justify-between gap-2 px-1">
        <h3 className="text-xs font-semibold text-foreground">{column.name}</h3>
        <div className="flex items-center gap-1.5">
          <span className="text-xs text-muted-foreground">{cards.length}</span>
          {column.wipLimit ? (
            <span className="text-xs text-muted-foreground">/ {column.wipLimit}</span>
          ) : null}
        </div>
      </div>
      <div
        ref={laneDrop.setNodeRef}
        className={[
          'mt-1 flex flex-1 flex-col gap-1 border border-dashed p-0.5 transition',
          isLaneActive ? 'border-primary/35 bg-primary/5' : 'border-transparent',
        ].join(' ')}
      >
        <SortableContext items={cards.map((card) => card.id)} strategy={verticalListSortingStrategy}>
          {cards.map((card) => (
            <SortableCard key={card.id} card={card} allGolems={allGolems} onOpenCard={onOpenCard} />
          ))}
        </SortableContext>
        {hasCards ? (
          <div
            ref={endDrop.setNodeRef}
            className={[
              'flex min-h-6 items-center justify-center border border-dashed px-2 py-1 text-xs transition',
              endDrop.isOver ? 'border-primary/45 bg-primary/10 text-foreground' : 'border-border/60 bg-white/60 text-muted-foreground',
            ].join(' ')}
          >
            Drop to end
          </div>
        ) : (
          <div className="flex flex-1 items-center justify-center border border-dashed border-border/70 bg-white/45 px-3 py-4 text-xs text-muted-foreground">
            Drop here
          </div>
        )}
      </div>
    </section>
  );
}
