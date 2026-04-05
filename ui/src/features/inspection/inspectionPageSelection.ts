import { useEffect, useState } from 'react';
import type {
  SelfEvolvingArtifactCatalogEntry,
  SelfEvolvingArtifactLineage,
  SelfEvolvingRun,
} from '../../lib/api/selfEvolvingApi';
import {
  buildArtifactRevisionPairs,
  buildArtifactTransitionPairs,
  type ArtifactRevisionPair,
  type ArtifactTransitionPair,
} from './inspectionArtifactCompareUtils';

export function hasGolemId(golemId: string): boolean {
  return golemId.length > 0;
}

export function canLoadSessions(golemId: string, isOnline: boolean): boolean {
  return hasGolemId(golemId) && isOnline;
}

export function canLoadSelectedSession(golemId: string, selectedSessionId: string | null, isOnline: boolean): boolean {
  return hasGolemId(golemId) && selectedSessionId != null && isOnline;
}

export function canLoadTrace(
  golemId: string,
  selectedSessionId: string | null,
  isOnline: boolean,
  detailsRequested: boolean,
): boolean {
  return canLoadSelectedSession(golemId, selectedSessionId, isOnline) && detailsRequested;
}

export function useSelectedSession(
  sessions: Array<{ id: string }>,
  selectedSessionId: string | null,
  setSelectedSessionId: (sessionId: string | null) => void,
) {
  useEffect(() => {
    if (sessions.length === 0) {
      setSelectedSessionId(null);
      return;
    }
    if (selectedSessionId == null) {
      setSelectedSessionId(sessions[0].id);
      return;
    }
    if (!sessions.some((session) => session.id === selectedSessionId)) {
      setSelectedSessionId(sessions[0].id);
    }
  }, [selectedSessionId, sessions, setSelectedSessionId]);
}

export function useSelectionState(
  runs: SelfEvolvingRun[],
  artifacts: SelfEvolvingArtifactCatalogEntry[],
) {
  const [selectedRunId, setSelectedRunId] = useState<string | null>(null);
  const [selectedArtifactStreamId, setSelectedArtifactStreamId] = useState<string | null>(null);
  const [artifactCompareMode, setArtifactCompareMode] = useState<'revision' | 'transition'>('transition');
  const [selectedArtifactRevisionPair, setSelectedArtifactRevisionPair] = useState<ArtifactRevisionPair | null>(null);
  const [selectedArtifactTransitionPair, setSelectedArtifactTransitionPair] = useState<ArtifactTransitionPair | null>(null);

  useEffect(() => {
    if (runs.length === 0) {
      setSelectedRunId(null);
      return;
    }
    if (selectedRunId == null || !runs.some((run) => run.id === selectedRunId)) {
      setSelectedRunId(runs[0].id);
    }
  }, [runs, selectedRunId]);

  useEffect(() => {
    if (artifacts.length === 0) {
      setSelectedArtifactStreamId(null);
      return;
    }
    if (selectedArtifactStreamId == null
      || !artifacts.some((artifact) => artifact.artifactStreamId === selectedArtifactStreamId)) {
      setSelectedArtifactStreamId(artifacts[0].artifactStreamId);
    }
  }, [artifacts, selectedArtifactStreamId]);

  return {
    artifactCompareMode,
    selectedArtifactRevisionPair,
    selectedArtifactStreamId,
    selectedArtifactTransitionPair,
    selectedRunId,
    setArtifactCompareMode,
    setSelectedArtifactRevisionPair,
    setSelectedArtifactStreamId,
    setSelectedArtifactTransitionPair,
    setSelectedRunId,
  };
}

export function useArtifactCompareSelectionSync({
  artifactCompareMode,
  artifactLineage,
  selectedArtifactRevisionPair,
  selectedArtifactTransitionPair,
  setArtifactCompareMode,
  setSelectedArtifactRevisionPair,
  setSelectedArtifactTransitionPair,
}: {
  artifactCompareMode: 'revision' | 'transition';
  artifactLineage: SelfEvolvingArtifactLineage | null;
  selectedArtifactRevisionPair: ArtifactRevisionPair | null;
  selectedArtifactTransitionPair: ArtifactTransitionPair | null;
  setArtifactCompareMode: (value: 'revision' | 'transition') => void;
  setSelectedArtifactRevisionPair: (value: ArtifactRevisionPair | null) => void;
  setSelectedArtifactTransitionPair: (value: ArtifactTransitionPair | null) => void;
}) {
  useEffect(() => {
    if (artifactLineage == null) {
      setSelectedArtifactRevisionPair(null);
      setSelectedArtifactTransitionPair(null);
      return;
    }

    const transitionPairs = buildArtifactTransitionPairs(artifactLineage);
    setSelectedArtifactTransitionPair(selectTransitionPair(transitionPairs, selectedArtifactTransitionPair));

    const revisionPairs = buildArtifactRevisionPairs(artifactLineage);
    setSelectedArtifactRevisionPair(selectRevisionPair(revisionPairs, selectedArtifactRevisionPair));
  }, [
    artifactLineage,
    selectedArtifactRevisionPair,
    selectedArtifactTransitionPair,
    setSelectedArtifactRevisionPair,
    setSelectedArtifactTransitionPair,
  ]);

  useEffect(() => {
    if (artifactCompareMode === 'transition' && selectedArtifactTransitionPair == null && selectedArtifactRevisionPair != null) {
      setArtifactCompareMode('revision');
    }
  }, [
    artifactCompareMode,
    selectedArtifactRevisionPair,
    selectedArtifactTransitionPair,
    setArtifactCompareMode,
  ]);
}

function selectTransitionPair(
  pairs: ArtifactTransitionPair[],
  current: ArtifactTransitionPair | null,
): ArtifactTransitionPair | null {
  if (pairs.length === 0) {
    return null;
  }
  if (current != null && pairs.some((pair) => pair.fromNodeId === current.fromNodeId && pair.toNodeId === current.toNodeId)) {
    return current;
  }
  return pairs[0];
}

function selectRevisionPair(
  pairs: ArtifactRevisionPair[],
  current: ArtifactRevisionPair | null,
): ArtifactRevisionPair | null {
  if (pairs.length === 0) {
    return null;
  }
  if (current != null && pairs.some((pair) => pair.fromRevisionId === current.fromRevisionId && pair.toRevisionId === current.toRevisionId)) {
    return current;
  }
  return pairs[0];
}
