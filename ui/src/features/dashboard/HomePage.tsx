import { Link } from 'react-router-dom';
import { useAuth } from '../../app/providers/useAuth';
import { PageHeader } from '../layout/PageHeader';

const summaryRows = [
  { label: 'Storage', value: 'JSON-first', hint: 'Local-first runtime without a database dependency.' },
  { label: 'Auth', value: 'JWT + refresh', hint: 'Browser refresh uses the secure cookie rotation flow.' },
  { label: 'Governance', value: 'Approvals, audit, budgets', hint: 'Risk and cost tooling is live in the same shell.' },
];

const quickLinks = [
  { label: 'Open Fleet', description: 'Manage runtimes, roles, and enrollment tokens.', to: '/fleet' },
  { label: 'Open Boards', description: 'Review active flows and open the current workspace.', to: '/boards' },
  { label: 'Review Approvals', description: 'Check blocked high-risk commands.', to: '/approvals' },
  { label: 'Inspect Audit', description: 'Scan operator and runtime history.', to: '/audit' },
  { label: 'View Budgets', description: 'Check token and cost pressure by scope.', to: '/budgets' },
  { label: 'System settings', description: 'Review deployment defaults and notifications.', to: '/settings' },
];

export function HomePage() {
  const { user } = useAuth();
  const roleLabel = user?.roles?.join(' / ') ?? 'OPERATOR';

  return (
    <div className="grid gap-6">
      <section className="panel p-6 md:p-8">
        <PageHeader
          eyebrow="Overview"
          title="Operator workspace"
          description="Monitor fleet, boards, approvals, audit, and budgets from one control plane."
          meta={
            <>
              <span>{user?.displayName}</span>
              <span>@{user?.username}</span>
              <span>{roleLabel}</span>
            </>
          }
        />

        <div className="mt-6 grid gap-4 xl:grid-cols-[0.95fr_1.05fr]">
          <section className="section-surface p-5">
            <h2 className="text-lg font-semibold tracking-[-0.03em] text-foreground">Current control plane</h2>
            <div className="mt-4 grid gap-3">
              {summaryRows.map((row) => (
                <article key={row.label} className="dense-row">
                  <div className="min-w-0">
                    <p className="text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">{row.label}</p>
                    <p className="mt-1 text-base font-semibold text-foreground">{row.value}</p>
                  </div>
                  <p className="max-w-xs text-sm text-muted-foreground">{row.hint}</p>
                </article>
              ))}
            </div>
          </section>

          <section className="section-surface p-5">
            <h2 className="text-lg font-semibold tracking-[-0.03em] text-foreground">Primary routes</h2>
            <div className="mt-4 grid gap-1">
              {quickLinks.map((link) => (
                <Link key={link.to} to={link.to} className="dense-row px-1 text-left transition hover:text-foreground">
                  <div className="min-w-0">
                    <p className="text-sm font-semibold text-foreground">{link.label}</p>
                    <p className="mt-1 text-sm text-muted-foreground">{link.description}</p>
                  </div>
                  <span className="text-xs uppercase tracking-[0.14em] text-muted-foreground">Open</span>
                </Link>
              ))}
            </div>
          </section>
        </div>
      </section>
    </div>
  );
}
