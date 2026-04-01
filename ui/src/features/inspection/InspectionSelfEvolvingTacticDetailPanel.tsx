import type { SelfEvolvingTacticSearchResult } from '../../lib/api/selfEvolvingApi';

export function InspectionSelfEvolvingTacticDetailPanel({
  tactic,
  onOpenArtifactStream,
}: {
  tactic: SelfEvolvingTacticSearchResult | null;
  onOpenArtifactStream: (artifactStreamId: string) => void;
}) {
  if (tactic == null) {
    return (
      <section className="panel p-4">
        <h2 className="text-sm font-bold text-foreground">Tactic detail</h2>
        <p className="mt-2 text-sm text-muted-foreground">Select a tactic to inspect summaries and evidence anchors.</p>
      </section>
    );
  }

  return (
    <section className="panel p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-sm font-bold text-foreground">{tactic.title ?? tactic.tacticId}</h2>
          <p className="mt-1 text-xs text-muted-foreground">
            {tactic.artifactKey ?? tactic.tacticId}
          </p>
        </div>
        {tactic.artifactStreamId ? (
          <button
            type="button"
            onClick={() => onOpenArtifactStream(tactic.artifactStreamId!)}
            className="border border-border bg-white/80 px-3 py-1.5 text-xs font-semibold text-foreground"
          >
            Open artifact workspace
          </button>
        ) : null}
      </div>

      <dl className="mt-4 grid gap-3 text-sm">
        <DetailRow label="Intent summary" value={tactic.intentSummary} />
        <DetailRow label="Behavior summary" value={tactic.behaviorSummary} />
        <DetailRow label="Tool summary" value={tactic.toolSummary} />
        <DetailRow label="Outcome summary" value={tactic.outcomeSummary} />
        <DetailRow label="Benchmark summary" value={tactic.benchmarkSummary} />
        <DetailRow label="Approval notes" value={tactic.approvalNotes} />
        <DetailRow label="Task families" value={formatList(tactic.taskFamilies)} />
        <DetailRow label="Tags" value={formatList(tactic.tags)} />
        <DetailRow label="Evidence snippets" value={formatList(tactic.evidenceSnippets)} />
      </dl>
    </section>
  );
}

function DetailRow({ label, value }: { label: string; value: string | null }) {
  return (
    <div className="grid gap-1">
      <dt className="text-xs font-semibold uppercase tracking-[0.14em] text-muted-foreground">{label}</dt>
      <dd className="text-sm text-foreground">{value ?? 'n/a'}</dd>
    </div>
  );
}

function formatList(values: string[]): string {
  return values.length === 0 ? 'n/a' : values.join(', ');
}
