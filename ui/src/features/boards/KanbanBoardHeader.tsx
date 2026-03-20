import { Link } from 'react-router-dom';

export function KanbanBoardHeader({
  boardId,
  boardName,
  templateKey,
  columnCount,
  totalCards,
  activeCards,
  onNewCard,
}: {
  boardId: string;
  boardName: string;
  templateKey: string;
  columnCount: number;
  totalCards: number;
  activeCards: number;
  onNewCard: () => void;
}) {
  return (
    <section className="panel px-4 py-3 md:px-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="min-w-0">
          <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-muted-foreground">{templateKey}</p>
          <div className="mt-1 flex flex-wrap items-center gap-2">
            <h2 className="text-xl font-bold tracking-[-0.04em] text-foreground">{boardName}</h2>
            <MetricPill label={`${columnCount} columns`} />
            <MetricPill label={`${totalCards} cards`} />
            <MetricPill label={`${activeCards} active`} />
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          <Link to="/boards" className="rounded-full border border-border bg-white/80 px-4 py-2 text-sm font-semibold text-foreground">
            All boards
          </Link>
          <Link to={`/boards/${boardId}/settings`} className="rounded-full border border-border bg-white/80 px-4 py-2 text-sm font-semibold text-foreground">
            Edit flow
          </Link>
          <button
            type="button"
            onClick={onNewCard}
            className="rounded-full bg-foreground px-4 py-2 text-sm font-semibold text-white"
          >
            New card
          </button>
        </div>
      </div>
    </section>
  );
}

function MetricPill({ label }: { label: string }) {
  return (
    <span className="rounded-full border border-border/80 bg-white/80 px-2.5 py-1 text-[10px] font-semibold uppercase tracking-[0.14em] text-muted-foreground">
      {label}
    </span>
  );
}
