import { useMemo, useState, type ReactNode } from 'react';
import type {
  InspectionMessage,
  InspectionTrace,
  InspectionTraceRecord,
  InspectionTraceSnapshot,
  InspectionTraceSpan,
  InspectionTraceSummary,
  InspectionTraceSummaryItem,
} from '../../lib/api/inspectionApi';
import { buildInspectionTraceFeed, type InspectionTraceFeedItem } from './inspectionTraceFeed';
import {
  buildTraceTree,
  flattenTraceTree,
  formatTraceBytes,
  formatTraceDuration,
  formatTraceTime,
  formatTraceTimestamp,
  getInitialTrace,
  getTraceStatusTone,
  type TraceTagTone,
} from './inspectionTraceFormat';

type TraceViewMode = 'waterfall' | 'feed' | 'timeline';
type DetailTab = 'attributes' | 'events' | 'snapshots';

interface InspectionTraceExplorerProps {
  summary: InspectionTraceSummary | null;
  trace: InspectionTrace | null;
  messages: InspectionMessage[];
  isLoadingSummary: boolean;
  isLoadingTrace: boolean;
  errorMessage: string | null;
  onLoadTrace: () => void;
  onExportTrace: () => void;
  onExportSnapshotPayload: (snapshotId: string, role: string | null, spanName: string | null) => Promise<void>;
  isExporting?: boolean;
  isExportingSnapshot?: boolean;
}

function toneClasses(tone: TraceTagTone): string {
  switch (tone) {
    case 'info':
      return 'border-sky-200 bg-sky-100 text-sky-900';
    case 'success':
      return 'border-emerald-200 bg-emerald-100 text-emerald-900';
    case 'warning':
      return 'border-amber-200 bg-amber-100 text-amber-900';
    case 'danger':
      return 'border-rose-200 bg-rose-100 text-rose-900';
    case 'muted':
    default:
      return 'border-border bg-muted/70 text-muted-foreground';
  }
}

function bubbleClasses(kind: 'system' | 'llm' | 'tool' | 'outbound' | 'user' | 'assistant' | 'other'): string {
  switch (kind) {
    case 'user':
      return 'border-sky-200 bg-sky-50';
    case 'assistant':
      return 'border-emerald-200 bg-emerald-50';
    case 'tool':
      return 'border-amber-200 bg-amber-50';
    case 'llm':
      return 'border-accent/20 bg-accent/5';
    case 'outbound':
      return 'border-primary/25 bg-primary/5';
    case 'system':
      return 'border-border bg-white';
    case 'other':
    default:
      return 'border-border bg-muted/40';
  }
}

function TracePill({ children, tone = 'muted' }: { children: ReactNode; tone?: TraceTagTone }) {
  return (
    <span className={`inline-flex items-center border px-2 py-0.5 text-[10px] font-semibold uppercase tracking-[0.16em] ${toneClasses(tone)}`}>
      {children}
    </span>
  );
}

function TraceSummaryRow({
  item,
  isLoadingTrace,
  onLoadTrace,
}: {
  item: InspectionTraceSummaryItem;
  isLoadingTrace: boolean;
  onLoadTrace: () => void;
}) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 border border-border/70 bg-white/70 px-3 py-3">
      <div className="min-w-0 flex-1">
        <p className="font-semibold text-foreground">{item.traceName ?? item.traceId}</p>
        <div className="mt-1 flex flex-wrap gap-2 text-xs text-muted-foreground">
          {item.startedAt ? <span>{formatTraceTimestamp(item.startedAt)}</span> : null}
          <span>{item.spanCount} spans</span>
          <span>{item.snapshotCount} snapshots</span>
          <span>{formatTraceDuration(item.durationMs)}</span>
        </div>
      </div>
      <div className="flex items-center gap-2">
        {item.rootStatusCode ? <TracePill tone={getTraceStatusTone(item.rootStatusCode)}>{item.rootStatusCode}</TracePill> : null}
        <button
          type="button"
          onClick={onLoadTrace}
          disabled={isLoadingTrace}
          className="bg-foreground px-3 py-1.5 text-xs font-semibold text-white disabled:opacity-60"
        >
          {isLoadingTrace ? 'Loading...' : 'Load details'}
        </button>
      </div>
    </div>
  );
}

function TraceSummaryCard({
  summary,
  isLoadingTrace,
  isExporting,
  onExportTrace,
  onLoadTrace,
}: {
  summary: InspectionTraceSummary;
  isLoadingTrace: boolean;
  isExporting: boolean;
  onExportTrace: () => void;
  onLoadTrace: () => void;
}) {
  return (
    <section className="panel p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="text-sm font-bold text-foreground">Trace summary</h3>
          <p className="text-xs text-muted-foreground">
            {summary.traceCount} trace{summary.traceCount === 1 ? '' : 's'} ready to inspect
          </p>
        </div>
        <button
          type="button"
          onClick={onExportTrace}
          disabled={isExporting}
          className="border border-border bg-white px-3 py-1.5 text-xs font-semibold text-foreground disabled:opacity-60"
        >
          {isExporting ? 'Exporting...' : 'Export JSON'}
        </button>
      </div>

      <div className="mt-3 flex flex-wrap gap-2">
        <TracePill>{formatTraceBytes(summary.storageStats.compressedSnapshotBytes)} compressed</TracePill>
        <TracePill>{formatTraceBytes(summary.storageStats.uncompressedSnapshotBytes)} raw</TracePill>
        {summary.storageStats.truncatedTraces > 0 ? (
          <TracePill tone="warning">{summary.storageStats.truncatedTraces} truncated</TracePill>
        ) : null}
      </div>

      <div className="mt-4 grid gap-3">
        {summary.traces.map((item) => (
          <TraceSummaryRow
            key={item.traceId}
            item={item}
            isLoadingTrace={isLoadingTrace}
            onLoadTrace={onLoadTrace}
          />
        ))}
      </div>
    </section>
  );
}

function TraceTabs({ mode, onChange }: { mode: TraceViewMode; onChange: (mode: TraceViewMode) => void }) {
  const tabs: Array<{ key: TraceViewMode; label: string }> = [
    { key: 'waterfall', label: 'Waterfall' },
    { key: 'feed', label: 'Feed' },
    { key: 'timeline', label: 'Timeline' },
  ];

  return (
    <div className="flex flex-wrap gap-2">
      {tabs.map((tab) => (
        <button
          key={tab.key}
          type="button"
          onClick={() => onChange(tab.key)}
          className={
            mode === tab.key
              ? 'bg-foreground px-3 py-1.5 text-xs font-semibold text-white'
              : 'border border-border bg-white px-3 py-1.5 text-xs font-semibold text-foreground'
          }
        >
          {tab.label}
        </button>
      ))}
    </div>
  );
}

function SnapshotPreview({
  snapshot,
  spanName,
  isExportingSnapshot,
  onExportSnapshotPayload,
}: {
  snapshot: InspectionTraceSnapshot;
  spanName: string | null;
  isExportingSnapshot: boolean;
  onExportSnapshotPayload: (snapshotId: string, role: string | null, spanName: string | null) => Promise<void>;
}) {
  return (
    <div className="border border-border/70 bg-white/70 p-3">
      <div className="flex flex-wrap items-center gap-2">
        <TracePill>{snapshot.role ?? 'snapshot'}</TracePill>
        {snapshot.contentType ? <TracePill tone="info">{snapshot.contentType}</TracePill> : null}
        <span className="text-xs text-muted-foreground">
          {formatTraceBytes(snapshot.compressedSize)} / {formatTraceBytes(snapshot.originalSize)}
        </span>
        {snapshot.payloadPreviewTruncated ? <TracePill tone="warning">Preview truncated</TracePill> : null}
        {snapshot.payloadAvailable ? (
          <button
            type="button"
            disabled={isExportingSnapshot}
            onClick={() => {
              void onExportSnapshotPayload(snapshot.snapshotId, snapshot.role, spanName);
            }}
            className="border border-border bg-white px-2 py-1 text-[11px] font-semibold text-foreground disabled:opacity-60"
          >
            {isExportingSnapshot ? 'Exporting...' : 'Export payload'}
          </button>
        ) : null}
      </div>
      <div className="mt-2">
        {snapshot.payloadAvailable && snapshot.payloadPreview ? (
          <pre className="max-h-56 overflow-auto whitespace-pre-wrap break-words bg-foreground p-3 text-xs text-white">
            {snapshot.payloadPreview}
          </pre>
        ) : (
          <p className="text-xs text-muted-foreground">Payload preview unavailable for this snapshot.</p>
        )}
      </div>
    </div>
  );
}

function InspectionTraceSpanDetail({
  traceId,
  span,
  onExportSnapshotPayload,
  isExportingSnapshot,
  onClose,
}: {
  traceId: string;
  span: InspectionTraceSpan;
  onExportSnapshotPayload: (snapshotId: string, role: string | null, spanName: string | null) => Promise<void>;
  isExportingSnapshot: boolean;
  onClose: () => void;
}) {
  const [activeTab, setActiveTab] = useState<DetailTab>('attributes');
  const attributes = Object.entries(span.attributes).filter(([, value]) => value != null && String(value).length > 0);

  const tabs: Array<{ key: DetailTab; label: string; count: number }> = [
    { key: 'attributes', label: 'Attributes', count: attributes.length },
    { key: 'events', label: 'Events', count: span.events.length },
    { key: 'snapshots', label: 'Snapshots', count: span.snapshots.length },
  ];

  return (
    <section className="border border-border/70 bg-muted/30 p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <p className="font-semibold text-foreground">{span.name ?? span.spanId}</p>
            <TracePill tone={getTraceStatusTone(span.statusCode)}>{span.statusCode ?? 'UNKNOWN'}</TracePill>
            <TracePill>{span.kind ?? 'UNKNOWN'}</TracePill>
            <TracePill>{formatTraceDuration(span.durationMs)}</TracePill>
          </div>
          <div className="mt-1 flex flex-wrap gap-3 text-xs text-muted-foreground">
            <span>span: {span.spanId}</span>
            {span.parentSpanId ? <span>parent: {span.parentSpanId}</span> : null}
            <span>trace: {traceId}</span>
            <span>{formatTraceTimestamp(span.startedAt)} - {formatTraceTimestamp(span.endedAt)}</span>
          </div>
        </div>
        <button
          type="button"
          onClick={onClose}
          className="border border-border bg-white px-3 py-1 text-xs font-semibold text-foreground"
        >
          Close
        </button>
      </div>

      <div className="mt-4 flex flex-wrap gap-2">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            type="button"
            onClick={() => setActiveTab(tab.key)}
            className={
              activeTab === tab.key
                ? 'bg-foreground px-3 py-1.5 text-xs font-semibold text-white'
                : 'border border-border bg-white px-3 py-1.5 text-xs font-semibold text-foreground'
            }
          >
            {tab.label} ({tab.count})
          </button>
        ))}
      </div>

      <div className="mt-4">
        {activeTab === 'attributes' ? (
          attributes.length > 0 ? (
            <div className="overflow-auto">
              <table className="min-w-full border-collapse text-left text-xs">
                <thead>
                  <tr className="border-b border-border">
                    <th className="py-2 pr-4 font-semibold text-muted-foreground">Key</th>
                    <th className="py-2 font-semibold text-muted-foreground">Value</th>
                  </tr>
                </thead>
                <tbody>
                  {attributes.map(([key, value]) => (
                    <tr key={key} className="border-b border-border/60">
                      <td className="py-2 pr-4 align-top font-medium text-foreground">{key}</td>
                      <td className="py-2 align-top whitespace-pre-wrap break-words text-muted-foreground">{String(value)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <p className="text-xs text-muted-foreground">No attributes recorded.</p>
          )
        ) : null}

        {activeTab === 'events' ? (
          span.events.length > 0 ? (
            <div className="grid gap-2">
              {span.events.map((event, index) => (
                <div key={`${event.name ?? 'event'}-${index}`} className="border border-border/70 bg-white/70 p-3">
                  <div className="flex flex-wrap items-center gap-2">
                    <TracePill>{event.name ?? 'event'}</TracePill>
                    {event.timestamp ? <span className="text-xs text-muted-foreground">{formatTraceTimestamp(event.timestamp)}</span> : null}
                  </div>
                  {Object.entries(event.attributes).length > 0 ? (
                    <p className="mt-2 whitespace-pre-wrap break-words text-xs text-muted-foreground">
                      {Object.entries(event.attributes)
                        .filter(([, value]) => value != null)
                        .map(([key, value]) => `${key}=${String(value)}`)
                        .join(', ')}
                    </p>
                  ) : null}
                </div>
              ))}
            </div>
          ) : (
            <p className="text-xs text-muted-foreground">No events recorded.</p>
          )
        ) : null}

        {activeTab === 'snapshots' ? (
          span.snapshots.length > 0 ? (
            <div className="grid gap-3">
              {span.snapshots.map((snapshot) => (
                <SnapshotPreview
                  key={snapshot.snapshotId}
                  snapshot={snapshot}
                  spanName={span.name}
                  isExportingSnapshot={isExportingSnapshot}
                  onExportSnapshotPayload={onExportSnapshotPayload}
                />
              ))}
            </div>
          ) : (
            <p className="text-xs text-muted-foreground">No snapshots stored.</p>
          )
        ) : null}
      </div>
    </section>
  );
}

function InspectionTraceFeedView({
  messages,
  trace,
  onExportSnapshotPayload,
  isExportingSnapshot,
}: {
  messages: InspectionMessage[];
  trace: InspectionTrace;
  onExportSnapshotPayload: (snapshotId: string, role: string | null, spanName: string | null) => Promise<void>;
  isExportingSnapshot: boolean;
}) {
  const [selectedSpanId, setSelectedSpanId] = useState<string | null>(null);
  const feed = useMemo(() => buildInspectionTraceFeed(messages, trace), [messages, trace]);
  const spansById = useMemo(
    () => new Map(trace.traces.flatMap((record) => record.spans.map((span) => [span.spanId, { span, traceId: record.traceId }] as const))),
    [trace.traces],
  );
  const selected = selectedSpanId ? spansById.get(selectedSpanId) ?? null : null;

  if (feed.turns.length === 0) {
    return <p className="text-sm text-muted-foreground">No messages or trace spans were available for this session.</p>;
  }

  return (
    <div className="grid gap-4">
      {feed.turns.map((turn, index) => (
        <section key={turn.id} className="panel p-4">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <h4 className="text-sm font-bold text-foreground">
                {turn.title} {index + 1}
              </h4>
              {turn.timestamp ? <p className="text-xs text-muted-foreground">{formatTraceTimestamp(turn.timestamp)}</p> : null}
            </div>
            <div className="flex flex-wrap gap-2">
              {turn.traceNames.map((traceName) => (
                <TracePill key={`${turn.id}:${traceName}`}>{traceName}</TracePill>
              ))}
            </div>
          </div>

          <div className="mt-4 grid gap-3">
            {turn.items.map((item) => (
              <TraceBubble
                key={item.id}
                item={item}
                onSelectSpan={(spanId) => setSelectedSpanId(spanId)}
              />
            ))}
          </div>
        </section>
      ))}

      {selected ? (
        <InspectionTraceSpanDetail
          traceId={selected.traceId}
          span={selected.span}
          onExportSnapshotPayload={onExportSnapshotPayload}
          isExportingSnapshot={isExportingSnapshot}
          onClose={() => setSelectedSpanId(null)}
        />
      ) : null}
    </div>
  );
}

function TraceBubble({
  item,
  onSelectSpan,
}: {
  item: InspectionTraceFeedItem;
  onSelectSpan: (spanId: string) => void;
}) {
  const bubbleKind =
    item.type === 'message'
      ? item.role === 'user'
        ? 'user'
        : item.role === 'assistant'
          ? 'assistant'
          : 'other'
      : item.bubbleKind;

  return (
    <article className={`border p-4 ${bubbleClasses(bubbleKind)}`}>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="font-semibold text-foreground">{item.title}</p>
          {item.timestamp ? <p className="text-xs text-muted-foreground">{formatTraceTimestamp(item.timestamp)}</p> : null}
        </div>
      </div>

      <div className="mt-2 flex flex-wrap gap-2">
        {item.tags.map((tag) => (
          <TracePill key={`${item.id}:${tag.label}`} tone={tag.tone}>
            {tag.label}
          </TracePill>
        ))}
      </div>

      <div className="mt-3">
        {item.type === 'message' ? (
          <div className="whitespace-pre-wrap break-words text-sm text-foreground">{item.content}</div>
        ) : (
          <div className="grid gap-3">
            {item.content ? <div className="whitespace-pre-wrap break-words text-sm text-foreground">{item.content}</div> : null}
            {item.eventNotes.length > 0 ? (
              <div className="grid gap-1 text-xs text-muted-foreground">
                {item.eventNotes.map((note) => (
                  <p key={note}>{note}</p>
                ))}
              </div>
            ) : null}
            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                onClick={() => onSelectSpan(item.spanId)}
                className="border border-border bg-white px-3 py-1.5 text-xs font-semibold text-foreground"
              >
                Span detail
              </button>
              {item.hasPayloadInspect ? <TracePill tone="info">Payload captured</TracePill> : null}
            </div>
          </div>
        )}
      </div>
    </article>
  );
}

function InspectionTraceTimelineView({
  trace,
  onExportSnapshotPayload,
  isExportingSnapshot,
}: {
  trace: InspectionTrace;
  onExportSnapshotPayload: (snapshotId: string, role: string | null, spanName: string | null) => Promise<void>;
  isExportingSnapshot: boolean;
}) {
  const [selectedSpanId, setSelectedSpanId] = useState<string | null>(null);
  const spans = useMemo(
    () => trace.traces.flatMap((record) => record.spans.map((span) => ({ span, traceId: record.traceId }))),
    [trace.traces],
  );
  const selected = selectedSpanId ? spans.find((item) => item.span.spanId === selectedSpanId) ?? null : null;

  return (
    <div className="grid gap-4">
      <section className="panel p-4">
        <h4 className="text-sm font-bold text-foreground">Timeline</h4>
        <div className="mt-3 overflow-auto">
          <table className="min-w-full border-collapse text-left text-xs">
            <thead>
              <tr className="border-b border-border">
                <th className="py-2 pr-4 font-semibold text-muted-foreground">Span</th>
                <th className="py-2 pr-4 font-semibold text-muted-foreground">Kind</th>
                <th className="py-2 pr-4 font-semibold text-muted-foreground">Started</th>
                <th className="py-2 pr-4 font-semibold text-muted-foreground">Duration</th>
                <th className="py-2 font-semibold text-muted-foreground">Status</th>
              </tr>
            </thead>
            <tbody>
              {spans.length > 0 ? (
                spans.map(({ span }) => (
                  <tr
                    key={span.spanId}
                    className={span.spanId === selectedSpanId ? 'bg-primary/5' : 'cursor-pointer border-b border-border/60 hover:bg-white'}
                    onClick={() => setSelectedSpanId(span.spanId)}
                  >
                    <td className="py-2 pr-4 text-foreground">{span.name ?? span.spanId}</td>
                    <td className="py-2 pr-4 text-muted-foreground">{span.kind ?? '-'}</td>
                    <td className="py-2 pr-4 text-muted-foreground">{formatTraceTime(span.startedAt)}</td>
                    <td className="py-2 pr-4 text-muted-foreground">{formatTraceDuration(span.durationMs)}</td>
                    <td className="py-2">
                      <TracePill tone={getTraceStatusTone(span.statusCode)}>{span.statusCode ?? 'UNKNOWN'}</TracePill>
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={5} className="py-4 text-muted-foreground">No spans captured for this trace.</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      {selected ? (
        <InspectionTraceSpanDetail
          traceId={selected.traceId}
          span={selected.span}
          onExportSnapshotPayload={onExportSnapshotPayload}
          isExportingSnapshot={isExportingSnapshot}
          onClose={() => setSelectedSpanId(null)}
        />
      ) : null}
    </div>
  );
}

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
    return 'bg-rose-500';
  }
  switch (kind) {
    case 'LLM':
      return 'bg-accent';
    case 'TOOL':
      return 'bg-amber-500';
    case 'OUTBOUND':
      return 'bg-primary';
    case null:
    default:
      return 'bg-slate-500';
  }
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
  onExportSnapshotPayload: (snapshotId: string, role: string | null, spanName: string | null) => Promise<void>;
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
            {visibleRows.map((row) => {
              const isParent = hasChildren.has(row.span.spanId);
              const isCollapsed = collapsedSpans.has(row.span.spanId);
              const isSelected = row.span.spanId === selectedSpanId;

              return (
                <div
                  key={row.span.spanId}
                  className={isSelected ? 'border border-primary/40 bg-primary/5 p-2' : 'border border-border/70 bg-white/70 p-2'}
                >
                  <div className="grid gap-2 md:grid-cols-[minmax(220px,320px)_minmax(0,1fr)]">
                    <div className="flex items-center gap-2" style={{ paddingLeft: `${row.depth * 16}px` }}>
                      {isParent ? (
                        <button
                          type="button"
                          onClick={() => {
                            setCollapsedSpans((previous) => {
                              const next = new Set(previous);
                              if (next.has(row.span.spanId)) {
                                next.delete(row.span.spanId);
                              } else {
                                next.add(row.span.spanId);
                              }
                              return next;
                            });
                          }}
                          className="text-xs text-muted-foreground"
                        >
                          {isCollapsed ? '▸' : '▾'}
                        </button>
                      ) : (
                        <span className="w-3 text-xs text-muted-foreground">·</span>
                      )}
                      <button
                        type="button"
                        onClick={() => setSelectedSpanId(row.span.spanId)}
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
            })}
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

function InspectionTraceWaterfallView({
  trace,
  onExportSnapshotPayload,
  isExportingSnapshot,
}: {
  trace: InspectionTrace;
  onExportSnapshotPayload: (snapshotId: string, role: string | null, spanName: string | null) => Promise<void>;
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

export function InspectionTraceExplorer({
  summary,
  trace,
  messages,
  isLoadingSummary,
  isLoadingTrace,
  errorMessage,
  onLoadTrace,
  onExportTrace,
  onExportSnapshotPayload,
  isExporting = false,
  isExportingSnapshot = false,
}: InspectionTraceExplorerProps) {
  const [mode, setMode] = useState<TraceViewMode>('waterfall');

  if (errorMessage != null) {
    return (
      <section className="panel p-4">
        <p className="text-sm font-semibold text-rose-900">{errorMessage}</p>
      </section>
    );
  }

  if (isLoadingSummary) {
    return (
      <section className="panel p-4">
        <p className="text-sm text-muted-foreground">Loading trace summary...</p>
      </section>
    );
  }

  if (summary == null) {
    return (
      <section className="panel p-4">
        <p className="text-sm text-muted-foreground">No traces captured for this session.</p>
      </section>
    );
  }

  if (trace == null) {
    return (
      <TraceSummaryCard
        summary={summary}
        isLoadingTrace={isLoadingTrace}
        isExporting={isExporting}
        onExportTrace={onExportTrace}
        onLoadTrace={onLoadTrace}
      />
    );
  }

  return (
    <div className="grid gap-4">
      <section className="panel p-4">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h3 className="text-sm font-bold text-foreground">Conversation + trace</h3>
            <p className="text-xs text-muted-foreground">
              {trace.traces.length} trace{trace.traces.length === 1 ? '' : 's'} mapped into feed, waterfall, and timeline views
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <TraceTabs mode={mode} onChange={setMode} />
            <button
              type="button"
              onClick={onExportTrace}
              disabled={isExporting}
              className="border border-border bg-white px-3 py-1.5 text-xs font-semibold text-foreground disabled:opacity-60"
            >
              {isExporting ? 'Exporting...' : 'Export JSON'}
            </button>
          </div>
        </div>

        <div className="mt-3 flex flex-wrap gap-2">
          <TracePill>{formatTraceBytes(trace.storageStats.compressedSnapshotBytes)} compressed</TracePill>
          <TracePill>{formatTraceBytes(trace.storageStats.uncompressedSnapshotBytes)} raw</TracePill>
          <TracePill>{messages.length} messages</TracePill>
          {trace.storageStats.truncatedTraces > 0 ? (
            <TracePill tone="warning">{trace.storageStats.truncatedTraces} truncated</TracePill>
          ) : null}
        </div>
      </section>

      {mode === 'feed' ? (
        <InspectionTraceFeedView
          messages={messages}
          trace={trace}
          onExportSnapshotPayload={onExportSnapshotPayload}
          isExportingSnapshot={isExportingSnapshot}
        />
      ) : null}

      {mode === 'timeline' ? (
        <InspectionTraceTimelineView
          trace={trace}
          onExportSnapshotPayload={onExportSnapshotPayload}
          isExportingSnapshot={isExportingSnapshot}
        />
      ) : null}

      {mode === 'waterfall' ? (
        <InspectionTraceWaterfallView
          trace={trace}
          onExportSnapshotPayload={onExportSnapshotPayload}
          isExportingSnapshot={isExportingSnapshot}
        />
      ) : null}
    </div>
  );
}
