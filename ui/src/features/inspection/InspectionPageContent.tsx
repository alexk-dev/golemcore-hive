import type { ApprovalRequest } from '../../lib/api/approvalsApi';
import { readErrorMessage } from '../../lib/format';
import type {
  InspectionMessage,
  InspectionSessionDetail,
  InspectionSessionSummary,
  InspectionTrace,
  InspectionTraceSummary,
} from '../../lib/api/inspectionApi';
import type {
  SelfEvolvingCandidate,
  SelfEvolvingLineageResponse,
  SelfEvolvingRun,
} from '../../lib/api/selfEvolvingApi';
import { InspectionSelfEvolvingApprovalPanel } from './InspectionSelfEvolvingApprovalPanel';
import { InspectionSelfEvolvingCandidateQueue } from './InspectionSelfEvolvingCandidateQueue';
import { InspectionSelfEvolvingLineageGraph } from './InspectionSelfEvolvingLineageGraph';
import { InspectionSelfEvolvingOverview } from './InspectionSelfEvolvingOverview';
import { InspectionSelfEvolvingRunTable } from './InspectionSelfEvolvingRunTable';
import { InspectionSelfEvolvingVerdictPanel } from './InspectionSelfEvolvingVerdictPanel';
import { InspectionMessagesPanel, InspectionSessionHeader, InspectionSessionsSidebar } from './InspectionPageSections';
import { InspectionTraceExplorer } from './InspectionTraceExplorer';
import { hasTraceSummaryData } from './inspectionPageUtils';

function NoticePanel({ children }: { children: string }) {
  return (
    <section className="panel p-4">
      <p className="text-sm text-muted-foreground">{children}</p>
    </section>
  );
}

function ErrorPanel({ children }: { children: string }) {
  return (
    <section className="border border-rose-200 bg-rose-100 p-4 text-sm text-rose-900">
      {children}
    </section>
  );
}

function OfflineInspectionPanel() {
  return (
    <section className="panel p-6">
      <h2 className="text-sm font-bold text-foreground">Inspection unavailable</h2>
      <p className="mt-2 max-w-2xl text-sm text-muted-foreground">
        Inspection is only available while the golem is online. Reconnect this golem and open the page again to browse sessions, read messages, and inspect traces.
      </p>
    </section>
  );
}

function EmptySelectionPanel() {
  return (
    <section className="panel p-6">
      <p className="text-sm text-muted-foreground">Select a session to read messages and inspect traces.</p>
    </section>
  );
}

export function MissingGolemIdPanel() {
  return <NoticePanel>Missing golem id.</NoticePanel>;
}

export function InspectionStatusPanels({
  isLoading,
  error,
  showOffline,
}: {
  isLoading: boolean;
  error: unknown;
  showOffline: boolean;
}) {
  return (
    <>
      {isLoading ? <NoticePanel>Loading golem profile...</NoticePanel> : null}
      {error ? <ErrorPanel>{readErrorMessage(error)}</ErrorPanel> : null}
      {showOffline ? <OfflineInspectionPanel /> : null}
    </>
  );
}

export function InspectionOnlineContent({
  sessions,
  selectedSessionId,
  sessionsLoading,
  sessionsError,
  selectedSessionSummary,
  selectedSession,
  sessionLoading,
  sessionError,
  keepLast,
  isMutating,
  isExportingTrace,
  traceSummary,
  trace,
  messages,
  isLoadingTraceSummary,
  isLoadingTrace,
  traceErrorMessage,
  isExportingSnapshot,
  selfEvolvingRuns,
  selectedSelfEvolvingRunId,
  selectedSelfEvolvingRun,
  selfEvolvingCandidates,
  selfEvolvingLineage,
  promotionApprovals,
  onSelectSession,
  onSelectSelfEvolvingRun,
  onKeepLastChange,
  onCompact,
  onClear,
  onExportTrace,
  onDelete,
  onLoadTrace,
  onExportSnapshotPayload,
}: {
  sessions: InspectionSessionSummary[];
  selectedSessionId: string | null;
  sessionsLoading: boolean;
  sessionsError: unknown;
  selectedSessionSummary: InspectionSessionSummary | null;
  selectedSession: InspectionSessionDetail | undefined;
  sessionLoading: boolean;
  sessionError: unknown;
  keepLast: number;
  isMutating: boolean;
  isExportingTrace: boolean;
  traceSummary: InspectionTraceSummary | null;
  trace: InspectionTrace | null;
  messages: InspectionMessage[];
  isLoadingTraceSummary: boolean;
  isLoadingTrace: boolean;
  traceErrorMessage: string | null;
  isExportingSnapshot: boolean;
  selfEvolvingRuns: SelfEvolvingRun[];
  selectedSelfEvolvingRunId: string | null;
  selectedSelfEvolvingRun: SelfEvolvingRun | null;
  selfEvolvingCandidates: SelfEvolvingCandidate[];
  selfEvolvingLineage: SelfEvolvingLineageResponse;
  promotionApprovals: ApprovalRequest[];
  onSelectSession: (sessionId: string) => void;
  onSelectSelfEvolvingRun: (runId: string) => void;
  onKeepLastChange: (value: number) => void;
  onCompact: () => void;
  onClear: () => void;
  onExportTrace: () => void;
  onDelete: () => void;
  onLoadTrace: () => void;
  onExportSnapshotPayload: (snapshotId: string, role: string | null, spanName: string | null) => Promise<void>;
}) {
  const selectedSessionView = buildSelectedSessionView(
    selectedSessionId ?? '',
    selectedSessionSummary,
    selectedSession,
  );
  const canExportTrace = hasTraceSummaryData(traceSummary);

  return (
    <div className="grid gap-4 xl:grid-cols-[320px_minmax(0,1fr)]">
      <InspectionSessionsSidebar
        sessions={sessions}
        selectedSessionId={selectedSessionId}
        isLoading={sessionsLoading}
        error={sessionsError}
        onSelect={onSelectSession}
      />

      <div className="grid gap-4">
        {selectedSessionId == null ? (
          <EmptySelectionPanel />
        ) : (
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
        )}

        <InspectionSelfEvolvingOverview
          runs={selfEvolvingRuns}
          candidates={selfEvolvingCandidates}
          approvals={promotionApprovals}
        />

        <div className="grid gap-4 xl:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)]">
          <InspectionSelfEvolvingRunTable
            runs={selfEvolvingRuns}
            selectedRunId={selectedSelfEvolvingRunId}
            onSelectRun={onSelectSelfEvolvingRun}
          />
          <InspectionSelfEvolvingVerdictPanel run={selectedSelfEvolvingRun} />
        </div>

        <div className="grid gap-4 xl:grid-cols-3">
          <InspectionSelfEvolvingCandidateQueue candidates={selfEvolvingCandidates} />
          <InspectionSelfEvolvingLineageGraph lineage={selfEvolvingLineage} />
          <InspectionSelfEvolvingApprovalPanel approvals={promotionApprovals} />
        </div>
      </div>
    </div>
  );
}

function buildSelectedSessionView(
  selectedSessionId: string,
  selectedSessionSummary: InspectionSessionSummary | null,
  selectedSession: InspectionSessionDetail | undefined,
) {
  return {
    channelType: resolveChannelType(selectedSessionSummary, selectedSession),
    conversationKey: resolveConversationKey(selectedSessionId, selectedSessionSummary, selectedSession),
    preview: resolvePreview(selectedSessionSummary),
    title: resolveTitle(selectedSessionId, selectedSessionSummary),
    updatedAt: resolveUpdatedAt(selectedSessionSummary, selectedSession),
  };
}

function resolveChannelType(
  selectedSessionSummary: InspectionSessionSummary | null,
  selectedSession: InspectionSessionDetail | undefined,
): string {
  if (selectedSessionSummary?.channelType) {
    return selectedSessionSummary.channelType;
  }
  if (selectedSession?.channelType) {
    return selectedSession.channelType;
  }
  return 'unknown';
}

function resolveConversationKey(
  selectedSessionId: string,
  selectedSessionSummary: InspectionSessionSummary | null,
  selectedSession: InspectionSessionDetail | undefined,
): string {
  if (selectedSessionSummary?.conversationKey) {
    return selectedSessionSummary.conversationKey;
  }
  if (selectedSession?.conversationKey) {
    return selectedSession.conversationKey;
  }
  return selectedSessionId;
}

function resolvePreview(selectedSessionSummary: InspectionSessionSummary | null): string | null {
  return selectedSessionSummary?.preview ?? null;
}

function resolveTitle(selectedSessionId: string, selectedSessionSummary: InspectionSessionSummary | null): string {
  if (selectedSessionSummary?.title) {
    return selectedSessionSummary.title;
  }
  return selectedSessionId;
}

function resolveUpdatedAt(
  selectedSessionSummary: InspectionSessionSummary | null,
  selectedSession: InspectionSessionDetail | undefined,
): string | null {
  if (selectedSessionSummary?.updatedAt) {
    return selectedSessionSummary.updatedAt;
  }
  return selectedSession?.updatedAt ?? null;
}
