import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { HomePage } from './HomePage';

vi.mock('../../app/providers/useAuth', () => ({
  useAuth: () => ({
    user: {
      id: 'operator_1',
      username: 'admin',
      displayName: 'Hive Admin',
      roles: ['ADMIN', 'OPERATOR'],
    },
  }),
}));

describe('HomePage', () => {
  it('renders an operator summary instead of the old phase hero', () => {
    render(
      <MemoryRouter>
        <HomePage />
      </MemoryRouter>,
    );

    expect(screen.getByRole('heading', { name: 'Operator workspace' })).toBeInTheDocument();
    expect(screen.queryByText('Hive now governs risky work, not just routing it.')).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Open Fleet/ })).toHaveAttribute('href', '/fleet');
    expect(screen.getByRole('link', { name: /Open Boards/ })).toHaveAttribute('href', '/boards');
    expect(screen.getByRole('link', { name: /Review Approvals/ })).toHaveAttribute('href', '/approvals');
    expect(screen.getByText('Hive Admin')).toBeInTheDocument();
  });
});
