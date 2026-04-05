import type {
  InspectionMessage,
  InspectionSessionDetail,
  InspectionTrace,
  InspectionTraceSummary,
} from '../../lib/api/inspectionApi';
import { InspectionMessagesPanel, InspectionSessionHeader } from './InspectionPageSections';
import { InspectionTraceExplorer } from './InspectionTraceExplorer';

interface SelectedSessionView {
  channelType: string;
  conversationKey: string;
  preview: string | null;
  title: string;
  updatedAt: string | null;
}

export function EmptySelectionPanel() {
  return (
    <section className="panel p-6">
      <p className="text-sm text-muted-foreground">Select a session to read messages and inspect traces.</p>
    </section>
  );
}

export function InspectionSelectedSessionContent({
  selectedSessionId,
  selectedSession,
  sessionLoading,
  sessionError,
  selectedSessionView,
  keepLast,
  isMutating,
  isExportingTrace,
  canExportTrace,
  traceSummary,
  trace,
  messages,
  isLoadingTraceSummary,
  isLoadingTrace,
  traceErrorMessage,
  isExportingSnapshot,
  onKeepLastChange,
  onCompact,
  onClear,
  onExportTrace,
  onDelete,
  onLoadTrace,
  onExportSnapshotPayload,
}: {
  selectedSessionId: string | null;
  selectedSession: InspectionSessionDetail | undefined;
  sessionLoading: boolean;
  sessionError: unknown;
  selectedSessionView: SelectedSessionView;
  keepLast: number;
  isMutating: boolean;
  isExportingTrace: boolean;
  canExportTrace: boolean;
  traceSummary: InspectionTraceSummary | null;
  trace: InspectionTrace | null;
  messages: InspectionMessage[];
  isLoadingTraceSummary: boolean;
  isLoadingTrace: boolean;
  traceErrorMessage: string | null;
  isExportingSnapshot: boolean;
  onKeepLastChange: (value: number) => void;
  onCompact: () => void;
  onClear: () => void;
  onExportTrace: () => void;
  onDelete: () => void;
  onLoadTrace: () => void;
  onExportSnapshotPayload: (snapshotId: string, role: string | null, spanName: string | null) => Promise<void>;
}) {
  if (selectedSessionId == null) {
    return <EmptySelectionPanel />;
  }

  return (
    <>
      <InspectionSessionHeader
        title={selectedSessionView.title}
        channelType={selectedSessionView.channelType}
        conversationKey={selectedSessionView.conversationKey}
        updatedAt={selectedSessionView.updatedAt}
        preview={selectedSessionView.preview}
        keepLast={keepLast}
        isMutating={isMutating}
        isExportingTrace={isExportingTrace}
        canExportTrace={canExportTrace}
        onKeepLastChange={onKeepLastChange}
        onCompact={onCompact}
        onClear={onClear}
        onExportTrace={onExportTrace}
        onDelete={onDelete}
      />

      <div className="grid gap-4 2xl:grid-cols-[minmax(0,0.85fr)_minmax(0,1.15fr)]">
        <InspectionMessagesPanel
          session={selectedSession}
          isLoading={sessionLoading}
          error={sessionError}
        />

        <InspectionTraceExplorer
          summary={traceSummary}
          trace={trace}
          messages={messages}
          isLoadingSummary={isLoadingTraceSummary}
          isLoadingTrace={isLoadingTrace}
          errorMessage={traceErrorMessage}
          onLoadTrace={onLoadTrace}
          onExportTrace={onExportTrace}
          onExportSnapshotPayload={onExportSnapshotPayload}
          isExporting={isExportingTrace}
          isExportingSnapshot={isExportingSnapshot}
        />
      </div>
    </>
  );
}
