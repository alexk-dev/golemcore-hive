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
    <section className="panel px-4 py-3">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <h2 className="text-lg font-bold tracking-tight text-foreground">{boardName}</h2>
          <span className="text-xs text-muted-foreground">
            {templateKey} · {columnCount} cols · {totalCards} cards · {activeCards} active
          </span>
        </div>
        <div className="flex flex-wrap gap-2">
          <Link to="/boards" className="rounded-full border border-border bg-white/80 px-3 py-1.5 text-sm font-semibold text-foreground">
            All boards
          </Link>
          <Link to={`/boards/${boardId}/settings`} className="rounded-full border border-border bg-white/80 px-3 py-1.5 text-sm font-semibold text-foreground">
            Settings
          </Link>
          <button
            type="button"
            onClick={onNewCard}
            className="rounded-full bg-foreground px-3 py-1.5 text-sm font-semibold text-white"
          >
            New card
          </button>
        </div>
      </div>
    </section>
  );
}
