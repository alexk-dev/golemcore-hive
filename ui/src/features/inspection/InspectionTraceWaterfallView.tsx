import { useMemo, useState } from 'react';
import type { InspectionTrace, InspectionTraceRecord, InspectionTraceSpan } from '../../lib/api/inspectionApi';
import { buildTraceTree, flattenTraceTree, formatTraceDuration, formatTraceTime, getInitialTrace } from './inspectionTraceFormat';
import { InspectionTraceSpanDetail } from './InspectionTraceSpanDetail';
import {
  TracePill,
  type SnapshotExportHandler,
} from './InspectionTraceCommon';

interface WaterfallRow {
  span: InspectionTraceSpan;
  depth: number;
  offsetPct: number;
  widthPct: number;
}

function parseTime(value: string | null): number | null {
  if (value == null || value.length === 0) {
    return null;
  }
  const ms = Date.parse(value);
  return Number.isNaN(ms) ? null : ms;
}

function computeTraceEnd(flat: Array<{ span: InspectionTraceSpan; depth: number }>, fallback: number): number {
  let max = fallback;
  for (const { span } of flat) {
    const end = parseTime(span.endedAt);
    if (end != null && end > max) {
      max = end;
    }
    const start = parseTime(span.startedAt);
    if (start != null && span.durationMs != null) {
      const computed = start + span.durationMs;
      if (computed > max) {
        max = computed;
      }
    }
  }
  return max;
}

function computeWaterfallRows(record: InspectionTraceRecord): WaterfallRow[] {
  const flat = flattenTraceTree(buildTraceTree(record.spans));
  if (flat.length === 0) {
    return [];
  }

  const traceStart = parseTime(record.startedAt) ?? parseTime(flat[0].span.startedAt) ?? 0;
  const traceEnd = parseTime(record.endedAt) ?? computeTraceEnd(flat, traceStart);
  const traceDuration = Math.max(traceEnd - traceStart, 1);

  return flat.map(({ span, depth }) => {
    const spanStart = parseTime(span.startedAt) ?? traceStart;
    const spanEnd = parseTime(span.endedAt) ?? (span.durationMs != null ? spanStart + span.durationMs : spanStart + 1);
    const offsetPct = ((spanStart - traceStart) / traceDuration) * 100;
    const widthPct = Math.max(((spanEnd - spanStart) / traceDuration) * 100, 0.5);
    return { span, depth, offsetPct, widthPct };
  });
}

function waterfallBarClasses(kind: string | null, statusCode: string | null): string {
  if (statusCode === 'ERROR') {
    return 'bg-rose-950/400';
  }
  switch (kind) {
    case 'LLM':
      return 'bg-accent';
    case 'TOOL':
      return 'bg-amber-950/400';
    case 'OUTBOUND':
      return 'bg-primary';
    case null:
    default:
      return 'bg-slate-500';
  }
}

export function InspectionTraceWaterfallView({
  trace,
  onExportSnapshotPayload,
  isExportingSnapshot,
}: {
  trace: InspectionTrace;
  onExportSnapshotPayload: SnapshotExportHandler;
  isExportingSnapshot: boolean;
}) {
  const initialTrace = getInitialTrace(trace.traces);
  const [expandedTraceId, setExpandedTraceId] = useState<string | null>(initialTrace?.traceId ?? null);

  return (
    <div className="grid gap-4">
      {trace.traces.map((record) => (
        <WaterfallRecord
          key={record.traceId}
          record={record}
          isExpanded={expandedTraceId === record.traceId}
          onToggleExpand={() => setExpandedTraceId((current) => (current === record.traceId ? null : record.traceId))}
          onExportSnapshotPayload={onExportSnapshotPayload}
          isExportingSnapshot={isExportingSnapshot}
        />
      ))}
    </div>
  );
}

function WaterfallRecord({
  record,
  isExpanded,
  onToggleExpand,
  onExportSnapshotPayload,
  isExportingSnapshot,
}: {
  record: InspectionTraceRecord;
  isExpanded: boolean;
  onToggleExpand: () => void;
  onExportSnapshotPayload: SnapshotExportHandler;
  isExportingSnapshot: boolean;
}) {
  const rows = useMemo(() => computeWaterfallRows(record), [record]);
  const [selectedSpanId, setSelectedSpanId] = useState<string | null>(null);
  const [collapsedSpans, setCollapsedSpans] = useState<Set<string>>(() => new Set());

  const selectedSpan = selectedSpanId ? record.spans.find((span) => span.spanId === selectedSpanId) ?? null : null;
  const hasChildren = useMemo(() => {
    const set = new Set<string>();
    rows.forEach(({ span }) => {
      if (span.parentSpanId != null) {
        set.add(span.parentSpanId);
      }
    });
    return set;
  }, [rows]);

  const visibleRows = useMemo(() => {
    const hidden = new Set<string>();
    const parentMap = new Map<string, string | null>();
    rows.forEach(({ span }) => {
      parentMap.set(span.spanId, span.parentSpanId);
    });

    rows.forEach(({ span }) => {
      let parentId = span.parentSpanId;
      while (parentId != null) {
        if (collapsedSpans.has(parentId)) {
          hidden.add(span.spanId);
          break;
        }
        parentId = parentMap.get(parentId) ?? null;
      }
    });

    return rows.filter((row) => !hidden.has(row.span.spanId));
  }, [collapsedSpans, rows]);

  return (
    <section className="panel p-4">
      <button
        type="button"
        onClick={onToggleExpand}
        className="flex w-full flex-wrap items-center gap-2 text-left"
      >
        <span className="text-xs text-muted-foreground">{isExpanded ? '▾' : '▸'}</span>
        <span className="font-semibold text-foreground">{record.traceName ?? record.traceId}</span>
        <TracePill>{record.spans.length} spans</TracePill>
        {record.startedAt ? <span className="text-xs text-muted-foreground">{formatTraceTime(record.startedAt)}</span> : null}
      </button>

      {isExpanded ? (
        <div className="mt-4 grid gap-4">
          <div className="grid gap-2">
            {visibleRows.map((row) => (
              <WaterfallRowCard
                key={row.span.spanId}
                row={row}
                isParent={hasChildren.has(row.span.spanId)}
                isCollapsed={collapsedSpans.has(row.span.spanId)}
                isSelected={row.span.spanId === selectedSpanId}
                onToggleCollapsed={() =>
                  setCollapsedSpans((previous) => {
                    const next = new Set(previous);
                    if (next.has(row.span.spanId)) {
                      next.delete(row.span.spanId);
                    } else {
                      next.add(row.span.spanId);
                    }
                    return next;
                  })
                }
                onSelect={() => setSelectedSpanId(row.span.spanId)}
              />
            ))}
          </div>

          {selectedSpan ? (
            <InspectionTraceSpanDetail
              traceId={record.traceId}
              span={selectedSpan}
              onExportSnapshotPayload={onExportSnapshotPayload}
              isExportingSnapshot={isExportingSnapshot}
              onClose={() => setSelectedSpanId(null)}
            />
          ) : null}
        </div>
      ) : null}
    </section>
  );
}

function WaterfallRowCard({
  row,
  isParent,
  isCollapsed,
  isSelected,
  onToggleCollapsed,
  onSelect,
}: {
  row: WaterfallRow;
  isParent: boolean;
  isCollapsed: boolean;
  isSelected: boolean;
  onToggleCollapsed: () => void;
  onSelect: () => void;
}) {
  return (
    <div className={isSelected ? 'border border-primary/40 bg-primary/5 p-2' : 'border border-border/70 bg-muted/70 p-2'}>
      <div className="grid gap-2 md:grid-cols-[minmax(220px,320px)_minmax(0,1fr)]">
        <div className="flex items-center gap-2" style={{ paddingLeft: `${row.depth * 16}px` }}>
          {isParent ? (
            <button
              type="button"
              onClick={onToggleCollapsed}
              className="text-xs text-muted-foreground"
            >
              {isCollapsed ? '▸' : '▾'}
            </button>
          ) : (
            <span className="w-3 text-xs text-muted-foreground">·</span>
          )}
          <button
            type="button"
            onClick={onSelect}
            className="min-w-0 text-left"
          >
            <span className="block truncate text-sm font-semibold text-foreground">{row.span.name ?? row.span.spanId}</span>
            <span className="block text-xs text-muted-foreground">
              {row.span.kind ?? 'UNKNOWN'} · {formatTraceDuration(row.span.durationMs)}
            </span>
          </button>
        </div>

        <div className="relative h-8 overflow-hidden bg-muted/60">
          <div
            className={`absolute top-1/2 h-4 -translate-y-1/2 ${waterfallBarClasses(row.span.kind, row.span.statusCode)}`}
            style={{ left: `${row.offsetPct}%`, width: `${row.widthPct}%` }}
          />
        </div>
      </div>
    </div>
  );
}
