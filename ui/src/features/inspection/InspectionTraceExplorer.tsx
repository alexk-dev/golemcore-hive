import { useState } from 'react';
import type { InspectionMessage, InspectionTrace, InspectionTraceSummary } from '../../lib/api/inspectionApi';
import { InspectionTraceFeedView } from './InspectionTraceFeedView';
import { InspectionTraceTimelineView } from './InspectionTraceTimelineView';
import { InspectionTraceWaterfallView } from './InspectionTraceWaterfallView';
import {
  TraceOverview,
  TraceSummaryCard,
  type TraceViewMode,
} from './InspectionTraceCommon';
import { hasTraceSummaryData } from './inspectionPageUtils';

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

  if (!hasTraceSummaryData(summary)) {
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
      <TraceOverview
        traceCount={trace.traces.length}
        storageStats={trace.storageStats}
        messageCount={messages.length}
        mode={mode}
        isExporting={isExporting}
        onModeChange={setMode}
        onExportTrace={onExportTrace}
      />

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
