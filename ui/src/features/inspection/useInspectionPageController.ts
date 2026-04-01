import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import {
  buildActionDialogConfig,
  runConfirmedAction,
  useInspectionActions,
  type InspectionAction,
} from './inspectionPageActions';
import {
  canLoadSelectedSession,
  canLoadSessions,
  canLoadTrace,
  hasGolemId,
  useArtifactCompareSelectionSync,
  useSelectedSession,
  useSelectionState,
} from './inspectionPageSelection';
import { useArtifactCompareQueries } from './useInspectionArtifactCompareQueries';
import {
  useInspectionArtifactLineageQuery,
  useInspectionRuntimeQueries,
  useInspectionSelfEvolvingQueries,
} from './useInspectionQueries';
import type { FeedbackState } from './inspectionPageUtils';

export function useInspectionPageController(golemId: string) {
  const resolvedGolemId = golemId;
  const hasResolvedGolemId = hasGolemId(resolvedGolemId);
  const queryClient = useQueryClient();
  const [channelFilter, setChannelFilter] = useState('');
  const [selectedSessionId, setSelectedSessionId] = useState<string | null>(null);
  const [keepLast, setKeepLast] = useState(20);
  const [detailsRequested, setDetailsRequested] = useState(false);
  const [feedback, setFeedback] = useState<FeedbackState | null>(null);
  const [actionDialog, setActionDialog] = useState<InspectionAction>(null);

  const golemPreviewQuery = useInspectionRuntimeQueries({
    resolvedGolemId,
    hasResolvedGolemId,
    channelFilter,
    selectedSessionId,
    sessionsEnabled: false,
    selectedSessionEnabled: false,
    traceEnabled: false,
  });
  const isOnline = golemPreviewQuery.golemQuery.data?.state === 'ONLINE';
  const sessionsEnabled = canLoadSessions(resolvedGolemId, isOnline);
  const selectedSessionEnabled = canLoadSelectedSession(resolvedGolemId, selectedSessionId, isOnline);
  const traceEnabled = canLoadTrace(resolvedGolemId, selectedSessionId, isOnline, detailsRequested);

  const runtimeQueries = useInspectionRuntimeQueries({
    resolvedGolemId,
    hasResolvedGolemId,
    channelFilter,
    selectedSessionId,
    sessionsEnabled,
    selectedSessionEnabled,
    traceEnabled,
  });

  useSelectedSession(runtimeQueries.sessionsQuery.data ?? [], selectedSessionId, setSelectedSessionId);

  useEffect(() => {
    setDetailsRequested(false);
  }, [selectedSessionId]);

  const selfEvolvingQueries = useInspectionSelfEvolvingQueries({
    resolvedGolemId,
    sessionsEnabled,
  });

  const effectiveRuns = useMemo(
    () => selfEvolvingQueries.selfEvolvingRunsQuery.data ?? [],
    [selfEvolvingQueries.selfEvolvingRunsQuery.data],
  );
  const effectiveArtifacts = useMemo(
    () => selfEvolvingQueries.selfEvolvingArtifactsQuery.data ?? [],
    [selfEvolvingQueries.selfEvolvingArtifactsQuery.data],
  );

  const selections = useSelectionState(effectiveRuns, effectiveArtifacts);
  const artifactLineageQuery = useInspectionArtifactLineageQuery({
    resolvedGolemId,
    sessionsEnabled,
    selectedArtifactStreamId: selections.selectedArtifactStreamId,
  });

  useArtifactCompareSelectionSync({
    artifactCompareMode: selections.artifactCompareMode,
    artifactLineage: artifactLineageQuery.data ?? null,
    selectedArtifactRevisionPair: selections.selectedArtifactRevisionPair,
    selectedArtifactTransitionPair: selections.selectedArtifactTransitionPair,
    setArtifactCompareMode: selections.setArtifactCompareMode,
    setSelectedArtifactRevisionPair: selections.setSelectedArtifactRevisionPair,
    setSelectedArtifactTransitionPair: selections.setSelectedArtifactTransitionPair,
  });

  const artifactQueries = useArtifactCompareQueries({
    sessionsEnabled,
    resolvedGolemId,
    selectedArtifactStreamId: selections.selectedArtifactStreamId,
    artifactCompareMode: selections.artifactCompareMode,
    selectedArtifactRevisionPair: selections.selectedArtifactRevisionPair,
    selectedArtifactTransitionPair: selections.selectedArtifactTransitionPair,
    artifactLineage: artifactLineageQuery.data ?? null,
  });

  const {
    clearMutation,
    compactMutation,
    deleteMutation,
    snapshotExportMutation,
    traceExportMutation,
  } = useInspectionActions({
    golemId: resolvedGolemId,
    selectedSessionId,
    keepLast,
    queryClient,
    setFeedback,
    setDetailsRequested,
    setSelectedSessionId,
  });

  const selectedSessionSummary = useMemo(
    () => (runtimeQueries.sessionsQuery.data ?? []).find((session) => session.id === selectedSessionId) ?? null,
    [runtimeQueries.sessionsQuery.data, selectedSessionId],
  );
  const promotionApprovals = useMemo(
    () => (selfEvolvingQueries.approvalsQuery.data ?? []).filter((approval) => approval.subjectType === 'SELF_EVOLVING_PROMOTION'),
    [selfEvolvingQueries.approvalsQuery.data],
  );
  const selectedSelfEvolvingRun = useMemo(
    () => effectiveRuns.find((run) => run.id === selections.selectedRunId) ?? effectiveRuns[0] ?? null,
    [effectiveRuns, selections.selectedRunId],
  );
  const channelOptions = useMemo(() => {
    const values = new Set(['']);
    (runtimeQueries.sessionsQuery.data ?? []).forEach((session) => values.add(session.channelType));
    return Array.from(values);
  }, [runtimeQueries.sessionsQuery.data]);

  const actionDialogConfig = buildActionDialogConfig(
    actionDialog,
    clearMutation.isPending,
    deleteMutation.isPending,
  );
  const selectedSession = runtimeQueries.sessionQuery.data;
  const isMutating = compactMutation.isPending || clearMutation.isPending || deleteMutation.isPending;

  async function handleActionConfirm() {
    try {
      await runConfirmedAction(actionDialog, clearMutation, deleteMutation);
      setActionDialog(null);
    } catch {
      // mutation handlers already surface feedback
    }
  }

  return {
    actionDialogConfig,
    artifactCompareMode: selections.artifactCompareMode,
    artifactEvidenceQuery: artifactQueries.artifactEvidenceQuery,
    artifactLineageQuery,
    artifactRevisionDiffQuery: artifactQueries.artifactRevisionDiffQuery,
    artifactTransitionDiffQuery: artifactQueries.artifactTransitionDiffQuery,
    channelFilter,
    channelOptions,
    compactMutation,
    feedback,
    golemQuery: runtimeQueries.golemQuery,
    handleActionConfirm,
    hasResolvedGolemId,
    isMutating,
    isOnline,
    keepLast,
    promotionApprovals,
    selectedArtifactStreamId: selections.selectedArtifactStreamId,
    selectedSelfEvolvingRun,
    selectedSelfEvolvingRunId: selections.selectedRunId,
    selectedSession,
    selectedSessionId,
    selectedSessionSummary,
    selfEvolvingArtifacts: effectiveArtifacts,
    selfEvolvingArtifactsLoading: selfEvolvingQueries.selfEvolvingArtifactsQuery.isLoading,
    selfEvolvingCampaignsQuery: selfEvolvingQueries.selfEvolvingCampaignsQuery,
    selfEvolvingCandidatesQuery: selfEvolvingQueries.selfEvolvingCandidatesQuery,
    selfEvolvingLineageQuery: selfEvolvingQueries.selfEvolvingLineageQuery,
    selfEvolvingRuns: effectiveRuns,
    sessionQuery: runtimeQueries.sessionQuery,
    sessionsQuery: runtimeQueries.sessionsQuery,
    setActionDialog,
    setArtifactCompareMode: selections.setArtifactCompareMode,
    setChannelFilter,
    setDetailsRequested,
    setFeedback,
    setKeepLast,
    setSelectedArtifactRevisionPair: selections.setSelectedArtifactRevisionPair,
    setSelectedArtifactStreamId: selections.setSelectedArtifactStreamId,
    setSelectedArtifactTransitionPair: selections.setSelectedArtifactTransitionPair,
    setSelectedSelfEvolvingRunId: selections.setSelectedRunId,
    setSelectedSessionId,
    snapshotExportMutation,
    traceExportMutation,
    traceQuery: runtimeQueries.traceQuery,
    traceSummaryQuery: runtimeQueries.traceSummaryQuery,
  };
}
