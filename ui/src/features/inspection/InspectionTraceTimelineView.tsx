import { useMemo, useState } from 'react';
import type { InspectionTrace } from '../../lib/api/inspectionApi';
import { formatTraceDuration, formatTraceTime, getTraceStatusTone } from './inspectionTraceFormat';
import { InspectionTraceSpanDetail } from './InspectionTraceSpanDetail';
import {
  TracePill,
  type SnapshotExportHandler,
} from './InspectionTraceCommon';

export function InspectionTraceTimelineView({
  trace,
  onExportSnapshotPayload,
  isExportingSnapshot,
}: {
  trace: InspectionTrace;
  onExportSnapshotPayload: SnapshotExportHandler;
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
