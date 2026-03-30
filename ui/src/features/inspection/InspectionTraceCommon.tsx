import type { ReactNode } from 'react';
import type {
  InspectionTraceSnapshot,
  InspectionTraceSummary,
  InspectionTraceSummaryItem,
} from '../../lib/api/inspectionApi';
import {
  formatTraceBytes,
  formatTraceDuration,
  formatTraceTimestamp,
  getTraceStatusTone,
  type TraceTagTone,
} from './inspectionTraceFormat';

export type TraceViewMode = 'waterfall' | 'feed' | 'timeline';
export type DetailTab = 'attributes' | 'events' | 'snapshots';
export type SnapshotExportHandler = (
  snapshotId: string,
  role: string | null,
  spanName: string | null,
) => Promise<void>;

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

export function TracePill({ children, tone = 'muted' }: { children: ReactNode; tone?: TraceTagTone }) {
  return (
    <span className={`inline-flex items-center border px-2 py-0.5 text-[10px] font-semibold uppercase tracking-[0.16em] ${toneClasses(tone)}`}>
      {children}
    </span>
  );
}

export function TraceSummaryRow({
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

export function TraceSummaryCard({
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

export function TraceTabs({ mode, onChange }: { mode: TraceViewMode; onChange: (mode: TraceViewMode) => void }) {
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

export function SnapshotPreview({
  snapshot,
  spanName,
  isExportingSnapshot,
  onExportSnapshotPayload,
}: {
  snapshot: InspectionTraceSnapshot;
  spanName: string | null;
  isExportingSnapshot: boolean;
  onExportSnapshotPayload: SnapshotExportHandler;
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

export function TraceOverview({
  traceCount,
  storageStats,
  messageCount,
  mode,
  isExporting,
  onModeChange,
  onExportTrace,
}: {
  traceCount: number;
  storageStats: InspectionTraceSummary['storageStats'];
  messageCount: number;
  mode: TraceViewMode;
  isExporting: boolean;
  onModeChange: (mode: TraceViewMode) => void;
  onExportTrace: () => void;
}) {
  return (
    <section className="panel p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="text-sm font-bold text-foreground">Conversation + trace</h3>
          <p className="text-xs text-muted-foreground">
            {traceCount} trace{traceCount === 1 ? '' : 's'} mapped into feed, waterfall, and timeline views
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <TraceTabs mode={mode} onChange={onModeChange} />
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
        <TracePill>{formatTraceBytes(storageStats.compressedSnapshotBytes)} compressed</TracePill>
        <TracePill>{formatTraceBytes(storageStats.uncompressedSnapshotBytes)} raw</TracePill>
        <TracePill>{messageCount} messages</TracePill>
        {storageStats.truncatedTraces > 0 ? (
          <TracePill tone="warning">{storageStats.truncatedTraces} truncated</TracePill>
        ) : null}
      </div>
    </section>
  );
}
