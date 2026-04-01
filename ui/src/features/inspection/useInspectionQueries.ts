import { useQuery } from '@tanstack/react-query';
import { listApprovals } from '../../lib/api/approvalsApi';
import { getGolem } from '../../lib/api/golemsApi';
import {
  getInspectionSession,
  getInspectionSessionTrace,
  getInspectionSessionTraceSummary,
  listInspectionSessions,
} from '../../lib/api/inspectionApi';
import {
  getSelfEvolvingArtifactLineage,
  getSelfEvolvingLineage,
  listSelfEvolvingArtifacts,
  listSelfEvolvingCampaigns,
  listSelfEvolvingCandidates,
  listSelfEvolvingRuns,
} from '../../lib/api/selfEvolvingApi';

export function useInspectionRuntimeQueries({
  resolvedGolemId,
  hasResolvedGolemId,
  channelFilter,
  selectedSessionId,
  sessionsEnabled,
  selectedSessionEnabled,
  traceEnabled,
}: {
  resolvedGolemId: string;
  hasResolvedGolemId: boolean;
  channelFilter: string;
  selectedSessionId: string | null;
  sessionsEnabled: boolean;
  selectedSessionEnabled: boolean;
  traceEnabled: boolean;
}) {
  const golemQuery = useQuery({
    queryKey: ['golem', resolvedGolemId],
    queryFn: () => getGolem(resolvedGolemId),
    enabled: hasResolvedGolemId,
    refetchInterval: 10_000,
  });

  const sessionsQuery = useQuery({
    queryKey: ['inspection-sessions', resolvedGolemId, channelFilter],
    queryFn: () => listInspectionSessions(resolvedGolemId, channelFilter || undefined),
    enabled: sessionsEnabled,
    refetchInterval: 10_000,
  });

  const sessionQuery = useQuery({
    queryKey: ['inspection-session', resolvedGolemId, selectedSessionId],
    queryFn: () => getInspectionSession(resolvedGolemId, selectedSessionId ?? ''),
    enabled: selectedSessionEnabled,
    refetchInterval: 10_000,
  });

  const traceSummaryQuery = useQuery({
    queryKey: ['inspection-trace-summary', resolvedGolemId, selectedSessionId],
    queryFn: () => getInspectionSessionTraceSummary(resolvedGolemId, selectedSessionId ?? ''),
    enabled: selectedSessionEnabled,
    refetchInterval: 10_000,
  });

  const traceQuery = useQuery({
    queryKey: ['inspection-trace', resolvedGolemId, selectedSessionId],
    queryFn: () => getInspectionSessionTrace(resolvedGolemId, selectedSessionId ?? ''),
    enabled: traceEnabled,
  });

  return {
    golemQuery,
    sessionQuery,
    sessionsQuery,
    traceQuery,
    traceSummaryQuery,
  };
}

export function useInspectionSelfEvolvingQueries({
  resolvedGolemId,
  sessionsEnabled,
}: {
  resolvedGolemId: string;
  sessionsEnabled: boolean;
}) {
  const selfEvolvingRunsQuery = useQuery({
    queryKey: ['self-evolving-runs', resolvedGolemId],
    queryFn: () => listSelfEvolvingRuns(resolvedGolemId),
    enabled: sessionsEnabled,
    refetchInterval: 10_000,
  });
  const selfEvolvingArtifactsQuery = useQuery({
    queryKey: ['self-evolving-artifacts', resolvedGolemId],
    queryFn: () => listSelfEvolvingArtifacts(resolvedGolemId),
    enabled: sessionsEnabled,
    refetchInterval: 10_000,
  });
  const selfEvolvingCandidatesQuery = useQuery({
    queryKey: ['self-evolving-candidates', resolvedGolemId],
    queryFn: () => listSelfEvolvingCandidates(resolvedGolemId),
    enabled: sessionsEnabled,
    refetchInterval: 10_000,
  });
  const selfEvolvingCampaignsQuery = useQuery({
    queryKey: ['self-evolving-campaigns', resolvedGolemId],
    queryFn: () => listSelfEvolvingCampaigns(resolvedGolemId),
    enabled: sessionsEnabled,
    refetchInterval: 10_000,
  });
  const selfEvolvingLineageQuery = useQuery({
    queryKey: ['self-evolving-lineage', resolvedGolemId],
    queryFn: () => getSelfEvolvingLineage(resolvedGolemId),
    enabled: sessionsEnabled,
    refetchInterval: 10_000,
  });
  const approvalsQuery = useQuery({
    queryKey: ['approvals', resolvedGolemId, 'SELF_EVOLVING_PROMOTION'],
    queryFn: () => listApprovals({ golemId: resolvedGolemId }),
    enabled: sessionsEnabled,
    refetchInterval: 10_000,
  });

  return {
    approvalsQuery,
    selfEvolvingArtifactsQuery,
    selfEvolvingCampaignsQuery,
    selfEvolvingCandidatesQuery,
    selfEvolvingLineageQuery,
    selfEvolvingRunsQuery,
  };
}

export function useInspectionArtifactLineageQuery({
  resolvedGolemId,
  sessionsEnabled,
  selectedArtifactStreamId,
}: {
  resolvedGolemId: string;
  sessionsEnabled: boolean;
  selectedArtifactStreamId: string | null;
}) {
  return useQuery({
    queryKey: ['self-evolving-artifact-lineage', resolvedGolemId, selectedArtifactStreamId],
    queryFn: () => getSelfEvolvingArtifactLineage(resolvedGolemId, selectedArtifactStreamId ?? ''),
    enabled: sessionsEnabled && selectedArtifactStreamId != null,
    refetchInterval: 10_000,
  });
}
