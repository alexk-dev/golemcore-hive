import type { SelfEvolvingArtifactLineage } from '../../lib/api/selfEvolvingApi';

export interface ArtifactRevisionPair {
  fromRevisionId: string;
  toRevisionId: string;
}

export interface ArtifactTransitionPair {
  fromNodeId: string;
  toNodeId: string;
}

export function buildArtifactTransitionPairs(lineage: SelfEvolvingArtifactLineage): ArtifactTransitionPair[] {
  const pairs: ArtifactTransitionPair[] = [];
  for (let index = 1; index < lineage.railOrder.length; index += 1) {
    pairs.push({
      fromNodeId: lineage.railOrder[index - 1],
      toNodeId: lineage.railOrder[index],
    });
  }
  return pairs;
}

export function buildArtifactRevisionPairs(lineage: SelfEvolvingArtifactLineage): ArtifactRevisionPair[] {
  const orderedRevisions = lineage.railOrder
    .map((nodeId) => lineage.nodes.find((node) => node.nodeId === nodeId)?.contentRevisionId || null)
    .filter((value): value is string => value != null && value.length > 0);
  const uniqueRevisions = Array.from(new Set(orderedRevisions));
  const pairs: ArtifactRevisionPair[] = [];
  for (let index = 1; index < uniqueRevisions.length; index += 1) {
    pairs.push({
      fromRevisionId: uniqueRevisions[index - 1],
      toRevisionId: uniqueRevisions[index],
    });
  }
  if (pairs.length === 0 && uniqueRevisions.length === 1) {
    pairs.push({
      fromRevisionId: uniqueRevisions[0],
      toRevisionId: uniqueRevisions[0],
    });
  }
  return pairs;
}
