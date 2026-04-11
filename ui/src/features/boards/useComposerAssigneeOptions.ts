import { useMemo } from 'react';
import type { GolemSummary } from '../../lib/api/golemsApi';
import type { ServiceDetail, ServiceRoutingResolved } from '../../lib/api/servicesApi';

export function useComposerAssigneeOptions({
  board,
  golems,
  team,
}: {
  board?: ServiceDetail;
  golems?: GolemSummary[];
  team?: ServiceRoutingResolved;
}) {
  return useMemo(() => {
    if (!board) {
      return null;
    }
    const teamCandidates = team?.candidates ?? [];
    return {
      cardId: 'draft',
      serviceId: board.id,
      boardId: board.id,
      teamCandidates,
      allCandidates:
        golems?.map((golem) => ({
          golemId: golem.id,
          displayName: golem.displayName,
          state: golem.state,
          score: 0,
          reasons: ['Available from global fleet'],
          roleSlugs: golem.roleSlugs,
          inBoardTeam: teamCandidates.some((candidate) => candidate.golemId === golem.id),
        })) ?? [],
    };
  }, [board, golems, team]);
}
