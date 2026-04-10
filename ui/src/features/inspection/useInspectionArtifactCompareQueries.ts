import { useQuery } from '@tanstack/react-query';
import {
  getSelfEvolvingArtifactCompareEvidence,
  getSelfEvolvingArtifactRevisionDiff,
  getSelfEvolvingArtifactRevisionEvidence,
  getSelfEvolvingArtifactTransitionDiff,
  getSelfEvolvingArtifactTransitionEvidence,
  type SelfEvolvingArtifactLineage,
} from '../../lib/api/selfEvolvingApi';
import type { ArtifactRevisionPair, ArtifactTransitionPair } from './inspectionArtifactCompareUtils';

export function useArtifactCompareQueries({
  sessionsEnabled,
  resolvedGolemId,
  selectedArtifactStreamId,
  artifactCompareMode,
  selectedArtifactRevisionPair,
  selectedArtifactTransitionPair,
  artifactLineage,
}: {
  sessionsEnabled: boolean;
  resolvedGolemId: string;
  selectedArtifactStreamId: string | null;
  artifactCompareMode: 'revision' | 'transition';
  selectedArtifactRevisionPair: ArtifactRevisionPair | null;
  selectedArtifactTransitionPair: ArtifactTransitionPair | null;
  artifactLineage: SelfEvolvingArtifactLineage | null;
}) {
  const artifactRevisionDiffQuery = useQuery({
    queryKey: [
      'self-evolving-artifact-revision-diff',
      resolvedGolemId,
      selectedArtifactStreamId,
      selectedArtifactRevisionPair?.fromRevisionId,
      selectedArtifactRevisionPair?.toRevisionId,
    ],
    queryFn: () => getSelfEvolvingArtifactRevisionDiff(
      resolvedGolemId,
      selectedArtifactStreamId ?? '',
      selectedArtifactRevisionPair?.fromRevisionId ?? '',
      selectedArtifactRevisionPair?.toRevisionId ?? '',
    ),
    enabled: sessionsEnabled && selectedArtifactStreamId != null && selectedArtifactRevisionPair != null,
  });

  const artifactTransitionDiffQuery = useQuery({
    queryKey: [
      'self-evolving-artifact-transition-diff',
      resolvedGolemId,
      selectedArtifactStreamId,
      selectedArtifactTransitionPair?.fromNodeId,
      selectedArtifactTransitionPair?.toNodeId,
    ],
    queryFn: () => getSelfEvolvingArtifactTransitionDiff(
      resolvedGolemId,
      selectedArtifactStreamId ?? '',
      selectedArtifactTransitionPair?.fromNodeId ?? '',
      selectedArtifactTransitionPair?.toNodeId ?? '',
    ),
    enabled: sessionsEnabled && selectedArtifactStreamId != null && selectedArtifactTransitionPair != null,
  });

  const artifactEvidenceQuery = useQuery({
    queryKey: buildArtifactEvidenceQueryKey(
      resolvedGolemId,
      selectedArtifactStreamId,
      artifactCompareMode,
      selectedArtifactRevisionPair,
      selectedArtifactTransitionPair,
    ),
    queryFn: () => loadArtifactEvidence({
      artifactCompareMode,
      artifactLineage,
      resolvedGolemId,
      selectedArtifactRevisionPair,
      selectedArtifactStreamId,
      selectedArtifactTransitionPair,
    }),
    enabled: isArtifactEvidenceEnabled(
      sessionsEnabled,
      selectedArtifactStreamId,
      selectedArtifactRevisionPair,
      selectedArtifactTransitionPair,
      artifactLineage,
    ),
  });

  return {
    artifactEvidenceQuery,
    artifactRevisionDiffQuery,
    artifactTransitionDiffQuery,
  };
}

function buildArtifactEvidenceQueryKey(
  resolvedGolemId: string,
  selectedArtifactStreamId: string | null,
  artifactCompareMode: 'revision' | 'transition',
  selectedArtifactRevisionPair: ArtifactRevisionPair | null,
  selectedArtifactTransitionPair: ArtifactTransitionPair | null,
) {
  return [
    'self-evolving-artifact-evidence',
    resolvedGolemId,
    selectedArtifactStreamId,
    artifactCompareMode,
    selectedArtifactRevisionPair?.fromRevisionId,
    selectedArtifactRevisionPair?.toRevisionId,
    selectedArtifactTransitionPair?.fromNodeId,
    selectedArtifactTransitionPair?.toNodeId,
  ];
}

function isArtifactEvidenceEnabled(
  sessionsEnabled: boolean,
  selectedArtifactStreamId: string | null,
  selectedArtifactRevisionPair: ArtifactRevisionPair | null,
  selectedArtifactTransitionPair: ArtifactTransitionPair | null,
  artifactLineage: SelfEvolvingArtifactLineage | null,
) {
  return sessionsEnabled
    && selectedArtifactStreamId != null
    && (
      selectedArtifactTransitionPair != null
      || selectedArtifactRevisionPair != null
      || artifactLineage?.defaultSelectedRevisionId != null
    );
}

async function loadArtifactEvidence({
  artifactCompareMode,
  artifactLineage,
  resolvedGolemId,
  selectedArtifactRevisionPair,
  selectedArtifactStreamId,
  selectedArtifactTransitionPair,
}: {
  artifactCompareMode: 'revision' | 'transition';
  artifactLineage: SelfEvolvingArtifactLineage | null;
  resolvedGolemId: string;
  selectedArtifactRevisionPair: ArtifactRevisionPair | null;
  selectedArtifactStreamId: string | null;
  selectedArtifactTransitionPair: ArtifactTransitionPair | null;
}) {
  if (artifactCompareMode === 'transition' && selectedArtifactTransitionPair != null) {
    return getSelfEvolvingArtifactTransitionEvidence(
      resolvedGolemId,
      selectedArtifactStreamId ?? '',
      selectedArtifactTransitionPair.fromNodeId,
      selectedArtifactTransitionPair.toNodeId,
    );
  }

  if (selectedArtifactRevisionPair != null) {
    return getSelfEvolvingArtifactCompareEvidence(
      resolvedGolemId,
      selectedArtifactStreamId ?? '',
      selectedArtifactRevisionPair.fromRevisionId,
      selectedArtifactRevisionPair.toRevisionId,
    );
  }

  return getSelfEvolvingArtifactRevisionEvidence(
    resolvedGolemId,
    selectedArtifactStreamId ?? '',
    artifactLineage?.defaultSelectedRevisionId ?? '',
  );
}
