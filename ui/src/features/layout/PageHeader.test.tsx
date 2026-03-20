import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { PageHeader } from './PageHeader';

describe('PageHeader', () => {
  it('renders compact title chrome with description, meta, and actions', () => {
    render(
      <PageHeader
        eyebrow="Fleet"
        title="Registered runtimes"
        description="Manage live golems and enrollment."
        meta={<span>2 online</span>}
        actions={<button type="button">New token</button>}
      />,
    );

    expect(screen.getByText('Fleet')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Registered runtimes' })).toBeInTheDocument();
    expect(screen.getByText('Manage live golems and enrollment.')).toBeInTheDocument();
    expect(screen.getByText('2 online')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'New token' })).toBeInTheDocument();
  });

  it('supports a minimal title-only variant', () => {
    render(<PageHeader title="Budgets" />);

    expect(screen.getByRole('heading', { name: 'Budgets' })).toBeInTheDocument();
    expect(screen.queryByText('Fleet')).not.toBeInTheDocument();
  });
});
