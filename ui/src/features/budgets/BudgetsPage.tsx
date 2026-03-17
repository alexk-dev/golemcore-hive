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
    <div className="grid gap-6">
      <section className="panel p-6 md:p-8">
        <span className="pill">Budgets</span>
        <h2 className="mt-4 text-3xl font-bold tracking-[-0.04em] text-foreground">Track cost and token pressure</h2>
        <p className="mt-3 max-w-3xl text-sm leading-7 text-muted-foreground">
          Budget snapshots are derived from commands and runs. They remain JSON-backed but keep stable scope boundaries
          for future storage upgrades.
        </p>
      </section>

      <section className="panel p-6 md:p-8">
        <div className="grid gap-4 md:grid-cols-[220px_1fr]">
          <label className="grid gap-2 text-sm text-muted-foreground">
            Scope
            <select
              value={scopeType}
              onChange={(event) => setScopeType(event.target.value)}
              className="rounded-[18px] border border-border bg-white/90 px-4 py-3 text-sm text-foreground outline-none transition focus:border-primary"
            >
              <option value="">All scopes</option>
              <option value="SYSTEM">System</option>
              <option value="BOARD">Board</option>
              <option value="GOLEM">Golem</option>
              <option value="CARD">Card</option>
            </select>
          </label>
          <label className="grid gap-2 text-sm text-muted-foreground">
            Search
            <input
              type="text"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Filter by label or scope id"
              className="rounded-[18px] border border-border bg-white/90 px-4 py-3 text-sm text-foreground outline-none transition focus:border-primary"
            />
          </label>
        </div>

        <div className="mt-6 grid gap-4">
          {(budgetsQuery.data ?? []).length ? (
            budgetsQuery.data?.map((snapshot) => (
              <article key={snapshot.id} className="soft-card p-5">
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div>
                    <div className="flex flex-wrap gap-2">
                      <span className="pill">{snapshot.scopeType}</span>
                    </div>
                    <h3 className="mt-3 text-lg font-bold tracking-[-0.03em] text-foreground">{snapshot.scopeLabel}</h3>
                    <p className="mt-2 text-sm text-muted-foreground">{snapshot.scopeId}</p>
                  </div>
                  <p className="text-xs uppercase tracking-[0.16em] text-muted-foreground">
                    {new Date(snapshot.updatedAt).toLocaleString()}
                  </p>
                </div>
                <div className="mt-4 grid gap-3 md:grid-cols-4">
                  <Metric label="Commands" value={snapshot.commandCount} />
                  <Metric label="Runs" value={snapshot.runCount} />
                  <Metric label="Actual cost micros" value={snapshot.actualCostMicros} />
                  <Metric label="Pending estimate" value={snapshot.estimatedPendingCostMicros} />
                </div>
                <p className="mt-4 text-sm text-muted-foreground">
                  Tokens {snapshot.inputTokens}/{snapshot.outputTokens}
                </p>
              </article>
            ))
          ) : (
            <div className="rounded-[22px] border border-dashed border-border px-4 py-8 text-sm text-muted-foreground">
              No budget snapshots available for the selected filter.
            </div>
          )}
        </div>
      </section>
    </div>
  );
}

function Metric({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-[18px] border border-border/70 bg-white/70 px-4 py-3">
      <p className="text-xs uppercase tracking-[0.16em] text-muted-foreground">{label}</p>
      <p className="mt-2 text-lg font-bold tracking-[-0.03em] text-foreground">{value}</p>
    </div>
  );
}
