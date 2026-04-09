import { useVirtualizer } from '@tanstack/react-virtual';
import { useQuery } from '@tanstack/react-query';
import { useDeferredValue, useRef, useState } from 'react';
import { listBudgetSnapshots } from '../../lib/api/budgetsApi';

const ROW_HEIGHT = 32;

export function BudgetsPage() {
  const [scopeType, setScopeType] = useState('');
  const [query, setQuery] = useState('');
  const deferredScopeType = useDeferredValue(scopeType);
  const deferredQuery = useDeferredValue(query);
  const parentRef = useRef<HTMLDivElement>(null);

  const budgetsQuery = useQuery({
    queryKey: ['budgets', deferredScopeType, deferredQuery],
    queryFn: () => listBudgetSnapshots(deferredScopeType || undefined, deferredQuery || undefined),
  });

  const snapshots = budgetsQuery.data ?? [];

  const virtualizer = useVirtualizer({
    count: snapshots.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => ROW_HEIGHT,
    overscan: 20,
  });

  return (
    <div className="grid gap-4">
      <div className="grid gap-2 md:grid-cols-[180px_1fr]">
        <select
          value={scopeType}
          onChange={(event) => setScopeType(event.target.value)}
          className="border border-border bg-panel/90 px-3 py-1.5 text-sm text-foreground outline-none transition focus:border-primary focus:ring-1 focus:ring-primary/50"
        >
          <option value="">All scopes</option>
          <option value="SYSTEM">System</option>
          <option value="BOARD">Board</option>
          <option value="GOLEM">Golem</option>
          <option value="CARD">Card</option>
        </select>
        <input
          type="text"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Filter by label or scope id"
          className="border border-border bg-panel/90 px-3 py-1.5 text-sm text-foreground outline-none transition focus:border-primary focus:ring-1 focus:ring-primary/50"
        />
      </div>

      {snapshots.length ? (
        <div className="border border-border/70">
          <div className="flex items-center gap-3 border-b border-border/50 bg-muted/50 px-3 py-1.5 text-xs font-semibold text-muted-foreground">
            <span className="w-20 shrink-0">Scope</span>
            <span className="min-w-0 flex-1">Label</span>
            <span className="hidden w-20 shrink-0 text-right sm:inline">Commands</span>
            <span className="hidden w-16 shrink-0 text-right sm:inline">Runs</span>
            <span className="w-24 shrink-0 text-right">Cost</span>
            <span className="hidden w-24 shrink-0 text-right md:inline">Pending</span>
            <span className="hidden w-32 shrink-0 text-right lg:inline">Tokens (in/out)</span>
            <span className="hidden w-36 shrink-0 text-right md:inline">Updated</span>
          </div>
          <div ref={parentRef} className="max-h-[70vh] overflow-auto">
            <div style={{ height: virtualizer.getTotalSize(), position: 'relative' }}>
              {virtualizer.getVirtualItems().map((virtualRow) => {
                const snapshot = snapshots[virtualRow.index];
                return (
                  <div
                    key={snapshot.id}
                    className="absolute left-0 flex w-full items-center gap-3 px-3 text-sm hover:bg-muted/60"
                    style={{ height: ROW_HEIGHT, top: virtualRow.start }}
                  >
                    <span className="w-20 shrink-0 text-xs text-muted-foreground">{snapshot.scopeType}</span>
                    <span className="min-w-0 flex-1 truncate font-medium text-foreground">{snapshot.scopeLabel}</span>
                    <span className="hidden w-20 shrink-0 text-right tabular-nums text-foreground sm:inline">{snapshot.commandCount}</span>
                    <span className="hidden w-16 shrink-0 text-right tabular-nums text-foreground sm:inline">{snapshot.runCount}</span>
                    <span className="w-24 shrink-0 text-right tabular-nums text-foreground">{snapshot.actualCostMicros}</span>
                    <span className="hidden w-24 shrink-0 text-right tabular-nums text-muted-foreground md:inline">{snapshot.estimatedPendingCostMicros}</span>
                    <span className="hidden w-32 shrink-0 text-right tabular-nums text-xs text-muted-foreground lg:inline">
                      {snapshot.inputTokens}/{snapshot.outputTokens}
                    </span>
                    <span className="hidden w-36 shrink-0 text-right text-xs text-muted-foreground md:inline">
                      {new Date(snapshot.updatedAt).toLocaleString()}
                    </span>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      ) : (
        <p className="text-sm text-muted-foreground">No budget snapshots for this filter.</p>
      )}
    </div>
  );
}
