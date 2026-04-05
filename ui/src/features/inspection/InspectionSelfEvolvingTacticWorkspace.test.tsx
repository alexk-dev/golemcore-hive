import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { InspectionSelfEvolvingTacticWorkspace } from './InspectionSelfEvolvingTacticWorkspace';

describe('InspectionSelfEvolvingTacticWorkspace', () => {
  it('shows degraded search state and explanation breakdown', () => {
    const onQueryChange = vi.fn();
    const onSelectTacticId = vi.fn();

    render(
      <InspectionSelfEvolvingTacticWorkspace
        query="planner fallback"
        onQueryChange={onQueryChange}
        response={{
          query: 'planner fallback',
          status: {
            mode: 'bm25',
            reason: 'Embeddings provider unavailable',
            degraded: true,
            updatedAt: '2026-04-01T12:00:00Z',
          },
          results: [
            {
              tacticId: 'tactic-1',
              artifactStreamId: 'stream-1',
              originArtifactStreamId: 'stream-1',
              artifactKey: 'tactic:planner-fallback',
              artifactType: 'tactic',
              title: 'Planner fallback recovery',
              aliases: ['planner fallback'],
              contentRevisionId: 'rev-2',
              intentSummary: 'Recover failed planner runs',
              behaviorSummary: 'Escalates to safer routing',
              toolSummary: 'planner, router',
              outcomeSummary: 'Reduced recovery failures',
              benchmarkSummary: 'Won 3 of 4 regression benchmarks',
              approvalNotes: 'Approved after canary',
              evidenceSnippets: ['Recovered planner dead-end'],
              taskFamilies: ['planning'],
              tags: ['recovery'],
              promotionState: 'active',
              rolloutStage: 'active',
              successRate: 0.92,
              benchmarkWinRate: 0.75,
              regressionFlags: ['none'],
              recencyScore: 0.83,
              golemLocalUsageSuccess: 0.88,
              embeddingStatus: 'degraded',
              updatedAt: '2026-04-01T12:00:00Z',
              score: 1.37,
              explanation: {
                searchMode: 'bm25',
                degradedReason: 'Embeddings provider unavailable',
                bm25Score: 0.91,
                vectorScore: null,
                rrfScore: 0.77,
                qualityPrior: 0.8,
                mmrDiversityAdjustment: -0.05,
                negativeMemoryPenalty: -0.1,
                personalizationBoost: 0.22,
                rerankerVerdict: 'preferred',
                matchedQueryViews: ['intent', 'recovery'],
                matchedTerms: ['planner', 'fallback'],
                eligible: true,
                gatingReason: null,
                finalScore: 1.37,
              },
            },
          ],
        }}
        selectedTacticId="tactic-1"
        onSelectTacticId={onSelectTacticId}
      />,
    );

    expect(screen.getByText(/embeddings degraded/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /planner fallback recovery/i })).toBeInTheDocument();
    expect(screen.getByText(/personalization boost/i)).toBeInTheDocument();
    expect(screen.getByText(/mmr diversity adjustment/i)).toBeInTheDocument();
    expect(screen.getByText(/negative memory penalty/i)).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /planner fallback recovery/i }));
    expect(onSelectTacticId).toHaveBeenCalledWith('tactic-1');
  });
});
