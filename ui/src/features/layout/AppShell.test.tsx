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
  it('renders grouped navigation sections', () => {
    renderShell('/boards');

    expect(screen.getByText('Hive')).toBeInTheDocument();
    expect(screen.getByText('Operate')).toBeInTheDocument();
    expect(screen.getByText('Fleet')).toBeInTheDocument();
    expect(screen.getByText('Observe')).toBeInTheDocument();
    expect(screen.getByText('Boards')).toBeInTheDocument();
    expect(screen.getByText('Policies')).toBeInTheDocument();
    expect(screen.getByText('Golems')).toBeInTheDocument();
    expect(screen.getByText('Roles')).toBeInTheDocument();
  });

  it('renders child route content', () => {
    renderShell('/boards/board_1');

    expect(screen.getByText('Board page')).toBeInTheDocument();
    expect(screen.getByText('Hive')).toBeInTheDocument();
  });
});

function renderShell(initialEntry: string) {
  render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route element={<AppShell />}>
          <Route path="/boards" element={<div>Boards page</div>} />
          <Route path="/boards/:boardId" element={<div>Board page</div>} />
          <Route path="/policies" element={<div>Policies page</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}
