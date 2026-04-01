import type { SelfEvolvingTacticSearchResponse } from '../../lib/api/selfEvolvingApi';
import { InspectionSelfEvolvingTacticDetailPanel } from './InspectionSelfEvolvingTacticDetailPanel';
import { InspectionSelfEvolvingTacticResultsList } from './InspectionSelfEvolvingTacticResultsList';
import { InspectionSelfEvolvingTacticSearchStatusBanner } from './InspectionSelfEvolvingTacticSearchStatusBanner';
import { InspectionSelfEvolvingTacticWhyPanel } from './InspectionSelfEvolvingTacticWhyPanel';

export function InspectionSelfEvolvingTacticWorkspace({
  query,
  onQueryChange,
  response,
  selectedTacticId,
  onSelectTacticId,
  onOpenArtifactStream,
}: {
  query: string;
  onQueryChange: (value: string) => void;
  response: SelfEvolvingTacticSearchResponse | null;
  selectedTacticId: string | null;
  onSelectTacticId: (tacticId: string) => void;
  onOpenArtifactStream?: (artifactStreamId: string) => void;
}) {
  const results = response?.results ?? [];
  const selected = results.find((result) => result.tacticId === selectedTacticId) ?? results[0] ?? null;

  return (
    <section className="grid gap-4">
      <div className="panel p-4">
        <div>
          <h2 className="text-sm font-bold text-foreground">Tactic search</h2>
          <p className="mt-1 text-xs text-muted-foreground">
            Search by intent, tools, recovery patterns, and benchmark-backed behavior.
          </p>
        </div>
        <label className="mt-4 grid gap-1 text-xs font-semibold uppercase tracking-[0.14em] text-muted-foreground">
          Search query
          <input
            type="search"
            value={query}
            placeholder="planner, tool routing, failure recovery"
            onChange={(event) => onQueryChange(event.target.value)}
            className="border border-border bg-white px-3 py-2 text-sm font-normal text-foreground outline-none transition focus:border-primary"
          />
        </label>
      </div>

      <InspectionSelfEvolvingTacticSearchStatusBanner status={response?.status ?? null} />

      <div className="grid gap-4 xl:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)]">
        <InspectionSelfEvolvingTacticResultsList
          results={results}
          selectedTacticId={selectedTacticId}
          onSelectTacticId={onSelectTacticId}
        />

        <div className="grid gap-4">
          <InspectionSelfEvolvingTacticDetailPanel
            tactic={selected}
            onOpenArtifactStream={onOpenArtifactStream ?? (() => undefined)}
          />
          <InspectionSelfEvolvingTacticWhyPanel
            explanation={selected?.explanation ?? null}
            successRate={selected?.successRate ?? null}
            benchmarkWinRate={selected?.benchmarkWinRate ?? null}
            regressionFlags={selected?.regressionFlags ?? []}
            promotionState={selected?.promotionState ?? null}
            recencyScore={selected?.recencyScore ?? null}
            golemLocalUsageSuccess={selected?.golemLocalUsageSuccess ?? null}
          />
        </div>
      </div>
    </section>
  );
}
