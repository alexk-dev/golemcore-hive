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
    renderShell('/services');

    expect(screen.getByText('Hive')).toBeInTheDocument();
    expect(screen.getByText('Operate')).toBeInTheDocument();
    expect(screen.getByText('Fleet')).toBeInTheDocument();
    expect(screen.getByText('Observe')).toBeInTheDocument();
    expect(screen.getByText('Organization')).toBeInTheDocument();
    expect(screen.getByText('Objectives')).toBeInTheDocument();
    expect(screen.getByText('Services')).toBeInTheDocument();
    expect(screen.getByText('Teams')).toBeInTheDocument();
    expect(screen.getByText('Golems')).toBeInTheDocument();
    expect(screen.getByText('Roles')).toBeInTheDocument();
  });

  it('renders child route content', () => {
    renderShell('/services/service_1');

    expect(screen.getByText('Board page')).toBeInTheDocument();
    expect(screen.getByText('Hive')).toBeInTheDocument();
  });
});

function renderShell(initialEntry: string) {
  render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route element={<AppShell />}>
          <Route path="/" element={<div>Organization page</div>} />
          <Route path="/objectives" element={<div>Objectives page</div>} />
          <Route path="/services" element={<div>Services page</div>} />
          <Route path="/teams" element={<div>Teams page</div>} />
          <Route path="/services/:serviceId" element={<div>Board page</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}
