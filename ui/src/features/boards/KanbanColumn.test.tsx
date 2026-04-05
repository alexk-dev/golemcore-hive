import { DndContext } from '@dnd-kit/core';
import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { KanbanColumn } from './KanbanColumn';

describe('KanbanColumn', () => {
  it('keeps kanban cards compact and exposes an explicit append drop zone', () => {
    render(
      <DndContext>
        <KanbanColumn
          column={{
            id: 'in_progress',
            name: 'In Progress',
            description: 'Active work',
            wipLimit: null,
            terminal: false,
          }}
          cards={[
            {
              id: 'card_1',
              boardId: 'board_1',
              threadId: 'thread_1',
              title: 'Write forecast summary',
              columnId: 'in_progress',
              assigneeGolemId: 'golem_1',
              assignmentPolicy: 'AUTOMATIC',
              position: 0,
              archived: false,
              controlState: null,
            },
          ]}
          allGolems={[
            {
              id: 'golem_1',
              displayName: 'Atlas Planner',
              hostLabel: 'host-1',
              runtimeVersion: 'bot-1.0.0',
              state: 'ONLINE',
              lastHeartbeatAt: null,
              lastSeenAt: null,
              missedHeartbeatCount: 0,
              roleSlugs: ['developer'],
            },
          ]}
          onOpenCard={vi.fn()}
        />
      </DndContext>,
    );

    expect(screen.getByText('Write forecast summary')).toBeInTheDocument();
    expect(screen.getByText('Atlas Planner')).toBeInTheDocument();
    expect(screen.queryByText('golem_1')).not.toBeInTheDocument();
    expect(screen.queryByText('AUTOMATIC')).not.toBeInTheDocument();
    expect(screen.queryByText(/card /i)).not.toBeInTheDocument();
    expect(screen.getByText(/drop to end/i)).toBeInTheDocument();
  });
});
