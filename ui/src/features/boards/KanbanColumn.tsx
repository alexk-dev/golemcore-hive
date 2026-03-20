import { useDndContext, useDroppable } from '@dnd-kit/core';
import { SortableContext, useSortable, verticalListSortingStrategy } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import type { BoardColumn } from '../../lib/api/boardsApi';
import type { CardControlState, CardSummary } from '../../lib/api/cardsApi';

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
        'relative rounded-[16px] border border-border/70 bg-white/95 px-3 py-2.5 text-left shadow-[0_6px_18px_rgba(24,18,14,0.04)] transition hover:bg-white',
        isDragging ? 'opacity-50' : 'opacity-100',
        isOver ? 'ring-1 ring-primary/30' : '',
      ].join(' ')}
    >
      {isOver ? <span aria-hidden className="absolute inset-x-3 -top-1 h-0.5 rounded-full bg-primary" /> : null}
      <div className="flex items-start justify-between gap-3">
        <p className="line-clamp-2 text-sm font-semibold tracking-[-0.02em] text-foreground">{card.title}</p>
        {card.controlState ? (
          <span className={`shrink-0 rounded-full px-2 py-0.5 text-[10px] font-semibold uppercase tracking-[0.14em] ${controlTone}`}>
            {formatControlLabel(card.controlState)}
          </span>
        ) : null}
      </div>
      <p className="mt-1.5 truncate text-xs text-muted-foreground">{card.assigneeGolemId || 'Unassigned'}</p>
      {card.controlState ? (
        <div className="mt-1 flex flex-wrap items-center gap-2">
          {card.controlState.cancelRequestedPending && card.controlState.cancelRequestedByActorName ? (
            <span className="text-[11px] text-rose-900">stop requested by {card.controlState.cancelRequestedByActorName}</span>
          ) : null}
        </div>
      ) : null}
    </button>
  );
}

export function KanbanColumn({ column, cards, onOpenCard }: KanbanColumnProps) {
  const { over } = useDndContext();
  const laneDrop = useDroppable({
    id: `column:${column.id}`,
  });
  const endDrop = useDroppable({
    id: `column:${column.id}:end`,
  });
  const overId = over?.id ? String(over.id) : null;
  const isLaneActive = Boolean(
    overId && (overId === `column:${column.id}` || overId === `column:${column.id}:end` || cards.some((card) => card.id === overId)),
  );
  const isPrimaryLane = column.id === 'in_progress';
  const hasCards = cards.length > 0;

  return (
    <section
      className={[
        'flex min-h-[210px] flex-col border p-3 transition',
        hasCards ? 'min-w-[292px]' : 'min-w-[228px]',
        isPrimaryLane ? 'border-border bg-white/88 shadow-[0_10px_26px_rgba(24,18,14,0.05)]' : 'border-border/70 bg-white/55',
        isLaneActive ? 'border-primary/40 bg-primary/5 shadow-[0_12px_30px_rgba(234,88,12,0.08)]' : '',
      ].join(' ')}
    >
      <div className="flex items-center justify-between gap-3">
        <div>
          <h3 className="text-sm font-semibold tracking-[-0.02em] text-foreground">{column.name}</h3>
          <p className="mt-1 text-[11px] uppercase tracking-[0.14em] text-muted-foreground">{cards.length} cards</p>
        </div>
        {column.wipLimit ? (
          <span className="rounded-full bg-muted px-2.5 py-1 text-[10px] font-semibold uppercase tracking-[0.14em] text-muted-foreground">
            WIP {column.wipLimit}
          </span>
        ) : null}
      </div>
      {column.description ? <p className="mt-1.5 text-xs leading-5 text-muted-foreground">{column.description}</p> : null}
      <div
        ref={laneDrop.setNodeRef}
        className={[
          'mt-3 flex flex-1 flex-col gap-2 border border-dashed p-1 transition',
          isLaneActive ? 'border-primary/35 bg-primary/5' : 'border-transparent',
        ].join(' ')}
      >
        <SortableContext items={cards.map((card) => card.id)} strategy={verticalListSortingStrategy}>
          {cards.map((card) => (
            <SortableCard key={card.id} card={card} onOpenCard={onOpenCard} />
          ))}
        </SortableContext>
        <ColumnDropZone
          endDropIsOver={endDrop.isOver}
          hasCards={hasCards}
          isLaneActive={isLaneActive}
          setEndDropNodeRef={endDrop.setNodeRef}
        />
      </div>
    </section>
  );
}

function ColumnDropZone({
  endDropIsOver,
  hasCards,
  isLaneActive,
  setEndDropNodeRef,
}: {
  endDropIsOver: boolean;
  hasCards: boolean;
  isLaneActive: boolean;
  setEndDropNodeRef: (element: HTMLElement | null) => void;
}) {
  if (hasCards) {
    return (
      <div
        ref={setEndDropNodeRef}
        className={[
          'flex min-h-8 items-center justify-center border border-dashed px-3 py-2 text-[11px] font-medium uppercase tracking-[0.14em] transition',
          endDropIsOver ? 'border-primary/45 bg-primary/10 text-foreground' : 'border-border/60 bg-white/60 text-muted-foreground',
        ].join(' ')}
      >
        Drop to add at end
      </div>
    );
  }

  return (
    <div
      className={[
        'flex items-center justify-center border border-dashed px-4 py-4 text-center text-xs transition',
        isLaneActive ? 'border-primary/45 bg-primary/10 text-foreground' : 'border-border/60 bg-white/45 text-muted-foreground',
      ].join(' ')}
    >
      {isLaneActive ? 'Drop here' : 'No cards yet'}
    </div>
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
