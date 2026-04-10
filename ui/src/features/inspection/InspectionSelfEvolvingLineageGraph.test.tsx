import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { SelfEvolvingLineageResponse } from '../../lib/api/selfEvolvingApi';
import { InspectionSelfEvolvingLineageGraph } from './InspectionSelfEvolvingLineageGraph';

describe('InspectionSelfEvolvingLineageGraph', () => {
  it('renders readonly lineage nodes and their parent relationship', () => {
    const lineage: SelfEvolvingLineageResponse = {
      golemId: 'golem_1',
      nodes: [
        {
          id: 'lineage-1',
          golemId: 'golem_1',
          parentId: null,
          artifactType: 'skill',
          status: 'approved',
          updatedAt: '2026-03-30T20:00:00Z',
        },
        {
          id: 'lineage-2',
          golemId: 'golem_1',
          parentId: 'lineage-1',
          artifactType: 'prompt',
          status: 'shadowed',
          updatedAt: '2026-03-30T20:10:00Z',
        },
      ],
    };

    render(<InspectionSelfEvolvingLineageGraph lineage={lineage} />);

    expect(screen.getByText('lineage-1')).toBeInTheDocument();
    expect(screen.getByText('lineage-2')).toBeInTheDocument();
    expect(screen.getByText('parent lineage-1')).toBeInTheDocument();
  });
});
