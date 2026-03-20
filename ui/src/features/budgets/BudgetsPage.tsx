import { useQuery } from '@tanstack/react-query';
import { useDeferredValue, useState } from 'react';
import { listBudgetSnapshots } from '../../lib/api/budgetsApi';
import { PageHeader } from '../layout/PageHeader';

export function BudgetsPage() {
  const [scopeType, setScopeType] = useState('');
  const [query, setQuery] = useState('');
  const deferredScopeType = useDeferredValue(scopeType);
  const deferredQuery = useDeferredValue(query);

  const budgetsQuery = useQuery({
    queryKey: ['budgets', deferredScopeType, deferredQuery],
    queryFn: () => listBudgetSnapshots(deferredScopeType || undefined, deferredQuery || undefined),
  });
  const snapshots = budgetsQuery.data ?? [];

  return (
    <div className="grid gap-6">
      <PageHeader
        eyebrow="Budgets"
        title="Budget snapshots"
        description="Compact cost and token pressure by scope."
        meta={<span>{snapshots.length} snapshots</span>}
      />

      <section className="section-surface p-4">
        <div className="dense-row px-0 pt-0">
          <span className="text-sm font-semibold text-foreground">Filters</span>
          <span className="text-sm text-muted-foreground">{snapshots.length} snapshots</span>
        </div>
        <div className="mt-3 grid gap-3 md:grid-cols-[200px_minmax(0,1fr)]">
          <label className="grid gap-1 text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground">
            Scope
            <select
              value={scopeType}
              onChange={(event) => setScopeType(event.target.value)}
              className="rounded-[16px] border border-border bg-white/90 px-3 py-2.5 text-sm font-normal tracking-normal text-foreground outline-none transition focus:border-primary"
            >
              <option value="">All scopes</option>
              <option value="SYSTEM">System</option>
              <option value="BOARD">Board</option>
              <option value="GOLEM">Golem</option>
              <option value="CARD">Card</option>
            </select>
          </label>
          <label className="grid gap-1 text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground">
            Search
            <input
              type="text"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Filter by label or scope id"
              className="rounded-[16px] border border-border bg-white/90 px-3 py-2.5 text-sm font-normal tracking-normal text-foreground outline-none transition focus:border-primary"
            />
          </label>
        </div>
      </section>

      <section className="section-surface overflow-hidden">
        {snapshots.length ? (
          <table className="w-full border-collapse">
            <thead>
              <tr className="border-b border-border/70 text-left text-xs uppercase tracking-[0.18em] text-muted-foreground">
                <th className="px-4 py-3 font-medium">Scope</th>
                <th className="px-4 py-3 font-medium">Counts</th>
                <th className="px-4 py-3 font-medium">Tokens</th>
                <th className="px-4 py-3 font-medium">Cost</th>
                <th className="px-4 py-3 font-medium">Updated</th>
              </tr>
            </thead>
            <tbody>
              {snapshots.map((snapshot) => (
                <tr key={snapshot.id} className="border-b border-border/60 last:border-b-0 align-top">
                  <td className="px-4 py-3">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="pill">{snapshot.scopeType}</span>
                      <span className="text-sm font-medium text-foreground">{snapshot.scopeLabel}</span>
                    </div>
                    <div className="mt-1 text-sm text-muted-foreground">{snapshot.scopeId}</div>
                  </td>
                  <td className="px-4 py-3 text-sm text-foreground">
                    <div>{snapshot.commandCount} commands</div>
                    <div className="mt-1 text-muted-foreground">{snapshot.runCount} runs</div>
                  </td>
                  <td className="px-4 py-3 text-sm text-foreground">
                    <div>{snapshot.inputTokens} in</div>
                    <div className="mt-1 text-muted-foreground">{snapshot.outputTokens} out</div>
                  </td>
                  <td className="px-4 py-3 text-sm text-foreground">
                    <div>{formatMicros(snapshot.actualCostMicros)} actual</div>
                    <div className="mt-1 text-muted-foreground">{formatMicros(snapshot.estimatedPendingCostMicros)} pending</div>
                  </td>
                  <td className="px-4 py-3 text-sm text-muted-foreground">{formatTimestamp(snapshot.updatedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <div className="px-4 py-4 text-sm text-muted-foreground">No budget snapshots match the current filter.</div>
        )}
      </section>
    </div>
  );
}

function formatMicros(value: number) {
  return new Intl.NumberFormat('en-US').format(value);
}

function formatTimestamp(value: string) {
  return new Date(value).toLocaleString();
}
