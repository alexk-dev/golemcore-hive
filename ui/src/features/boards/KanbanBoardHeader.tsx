import { Link } from 'react-router-dom';

export function KanbanBoardHeader({
  boardId,
  boardName,
  boardDescription,
  templateKey,
  columnCount,
  totalCards,
  activeCards,
  onNewCard,
}: {
  boardId: string;
  boardName: string;
  boardDescription: string | null;
  templateKey: string;
  columnCount: number;
  totalCards: number;
  activeCards: number;
  onNewCard: () => void;
}) {
  return (
    <section className="panel px-5 py-4 md:px-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <span className="pill">{templateKey}</span>
          <h2 className="mt-3 text-2xl font-bold tracking-[-0.04em] text-foreground">{boardName}</h2>
          <p className="mt-2 max-w-3xl text-sm text-muted-foreground">
            {boardDescription || 'Board-specific columns, transitions, and team routing live here.'}
          </p>
          <div className="mt-3 flex flex-wrap gap-2">
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
    <span className="rounded-full border border-border bg-white/80 px-3 py-1.5 text-[11px] font-semibold uppercase tracking-[0.14em] text-muted-foreground">
      {label}
    </span>
  );
}
