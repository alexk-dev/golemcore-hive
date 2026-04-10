import type { CardDetail } from '../../lib/api/cardsApi';

interface CardWorkGraphPanelProps {
  card: CardDetail;
}

function formatList(items?: string[] | null) {
  if (!items?.length) {
    return 'None';
  }
  return items.join(', ');
}

export function CardWorkGraphPanel({ card }: CardWorkGraphPanelProps) {
  const kind = card.kind ?? 'TASK';

  return (
    <section className="panel p-4">
      <div className="flex items-center justify-between gap-3">
        <h3 className="text-base font-bold tracking-tight text-foreground">Work graph</h3>
        <span className="text-xs font-semibold uppercase tracking-[0.14em] text-muted-foreground">{kind}</span>
      </div>
      <div className="mt-3 grid gap-3 text-sm">
        <KeyValue label="Epic" value={card.epicCardId ?? 'None'} />
        <KeyValue label="Parent" value={card.parentCardId ?? 'None'} />
        <KeyValue label="Review of" value={card.reviewOfCardId ?? 'None'} />
        <KeyValue label="Review status" value={card.reviewStatus ?? 'NOT_REQUIRED'} />
        <KeyValue label="Reviewers" value={formatList(card.reviewerGolemIds)} />
        <KeyValue label="Dependencies" value={formatList(card.dependsOnCardIds)} />
      </div>
    </section>
  );
}

function KeyValue({ label, value }: { label: string; value: string }) {
  return (
    <div className="grid gap-0.5">
      <span className="text-xs font-semibold uppercase tracking-[0.14em] text-muted-foreground">{label}</span>
      <span className="text-sm text-foreground">{value}</span>
    </div>
  );
}
