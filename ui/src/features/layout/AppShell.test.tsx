import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { AppShell } from './AppShell';

vi.mock('../../app/providers/useAuth', () => ({
  useAuth: () => ({
    user: {
      id: 'operator_1',
      username: 'admin',
      displayName: 'Admin',
      roles: ['ADMIN'],
    },
    logout: vi.fn().mockResolvedValue(undefined),
  }),
}));

describe('AppShell', () => {
  it('keeps the descriptive subtitle on non-board routes', () => {
    renderShell('/');

    expect(screen.getByText('Operator workbench for boards, golems, and governed dispatch')).toBeInTheDocument();
    expect(screen.getByText('Overview page')).toBeInTheDocument();
  });

  it('uses a quieter compact shell on board routes', () => {
    renderShell('/boards/board_1');

    expect(screen.queryByText('Operator workbench for boards, golems, and governed dispatch')).not.toBeInTheDocument();
    expect(screen.getByText('Board page')).toBeInTheDocument();
    expect(screen.getByText('Golemcore Hive')).toBeInTheDocument();
  });
});

function renderShell(initialEntry: string) {
  render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route element={<AppShell />}>
          <Route path="/" element={<div>Overview page</div>} />
          <Route path="/boards/:boardId" element={<div>Board page</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}
