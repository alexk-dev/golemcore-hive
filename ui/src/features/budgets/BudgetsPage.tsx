import { useQuery } from '@tanstack/react-query';
import { useDeferredValue, useState } from 'react';
import { listBudgetSnapshots } from '../../lib/api/budgetsApi';

export function BudgetsPage() {
  const [scopeType, setScopeType] = useState('');
  const [query, setQuery] = useState('');
  const deferredScopeType = useDeferredValue(scopeType);
  const deferredQuery = useDeferredValue(query);

  const budgetsQuery = useQuery({
    queryKey: ['budgets', deferredScopeType, deferredQuery],
    queryFn: () => listBudgetSnapshots(deferredScopeType || undefined, deferredQuery || undefined),
  });

  return (
    <div className="grid gap-5">
      <div className="grid gap-3 md:grid-cols-[220px_1fr]">
        <label className="grid gap-1.5 text-sm text-muted-foreground">
          Scope
          <select
            value={scopeType}
            onChange={(event) => setScopeType(event.target.value)}
            className="rounded-xl border border-border bg-white/90 px-4 py-2.5 text-sm text-foreground outline-none transition focus:border-primary"
          >
            <option value="">All scopes</option>
            <option value="SYSTEM">System</option>
            <option value="BOARD">Board</option>
            <option value="GOLEM">Golem</option>
            <option value="CARD">Card</option>
          </select>
        </label>
        <label className="grid gap-1.5 text-sm text-muted-foreground">
          Search
          <input
            type="text"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Filter by label or scope id"
            className="rounded-xl border border-border bg-white/90 px-4 py-2.5 text-sm text-foreground outline-none transition focus:border-primary"
          />
        </label>
      </div>

      <div className="grid gap-4">
        {(budgetsQuery.data ?? []).length ? (
          budgetsQuery.data?.map((snapshot) => (
            <article key={snapshot.id} className="panel p-5">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <span className="pill">{snapshot.scopeType}</span>
                  <h3 className="mt-2 text-base font-bold tracking-tight text-foreground">{snapshot.scopeLabel}</h3>
                  <p className="mt-1 text-xs text-muted-foreground">{snapshot.scopeId}</p>
                </div>
                <span className="text-xs text-muted-foreground">
                  {new Date(snapshot.updatedAt).toLocaleString()}
                </span>
              </div>
              <div className="mt-3 grid gap-3 md:grid-cols-4">
                <Metric label="Commands" value={snapshot.commandCount} />
                <Metric label="Runs" value={snapshot.runCount} />
                <Metric label="Actual cost" value={snapshot.actualCostMicros} />
                <Metric label="Pending est." value={snapshot.estimatedPendingCostMicros} />
              </div>
              <p className="mt-3 text-xs text-muted-foreground">
                Tokens {snapshot.inputTokens}/{snapshot.outputTokens}
              </p>
            </article>
          ))
        ) : (
          <div className="panel p-6 text-sm text-muted-foreground">
            No budget snapshots for this filter.
          </div>
        )}
      </div>
    </div>
  );
}

function Metric({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-xl border border-border/70 bg-white/70 px-4 py-3">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="mt-1 text-lg font-bold tracking-tight text-foreground">{value}</p>
    </div>
  );
}
