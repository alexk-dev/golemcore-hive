import { useAuth } from '../../app/providers/AuthProvider';

const metrics = [
  { label: 'Storage', value: 'JSON-first', hint: 'No DB for v1' },
  { label: 'Auth', value: 'JWT + refresh', hint: 'Browser cookie rotation' },
  { label: 'Governance', value: 'Approvals live', hint: 'Audit and budget views online' },
];

const roadmapCards = [
  {
    title: 'Approvals and audit',
    text: 'Risky commands can now pause for approval, and every auth, dispatch, and lifecycle decision lands in the audit stream.',
  },
  {
    title: 'Budget snapshots',
    text: 'System, board, card, and golem scopes now aggregate token and cost projections from the same local JSON runtime.',
  },
  {
    title: 'Self-hosted defaults',
    text: 'Settings now expose notification hooks, retention windows, and production deployment guardrails for packaged releases.',
  },
];

export function HomePage() {
  const { user } = useAuth();

  return (
    <div className="grid gap-6">
      <section className="grid gap-4 lg:grid-cols-[1.3fr_0.9fr]">
        <article className="panel px-6 py-6 md:px-8">
            <div className="space-y-4">
            <span className="pill">Phase 5</span>
            <h2 className="text-3xl font-bold tracking-[-0.04em] text-foreground">
              Hive now governs risky work, not just routing it.
            </h2>
            <p className="max-w-2xl text-sm leading-7 text-muted-foreground md:text-base">
              Operators can gate destructive or high-cost commands, inspect audit history, and watch budget pressure
              without leaving the same control plane that owns boards and golem threads.
            </p>
          </div>
        </article>

        <article className="panel px-6 py-6 md:px-8">
          <div className="space-y-4">
            <div>
              <p className="text-xs font-semibold uppercase tracking-[0.24em] text-muted-foreground">Operator profile</p>
              <h3 className="mt-2 text-2xl font-bold tracking-[-0.04em] text-foreground">{user?.displayName}</h3>
            </div>
            <div className="rounded-[22px] border border-border/80 bg-muted/60 p-4">
              <p className="text-sm font-medium text-foreground">Ready for thread orchestration</p>
              <p className="mt-2 text-sm leading-6 text-muted-foreground">
                The current session now fronts a governed control plane: JWT rotation, local JSON persistence, fleet
                routing, kanban state, approvals, and budget snapshots all share the same local-first runtime.
              </p>
            </div>
          </div>
        </article>
      </section>

      <section className="grid gap-4 md:grid-cols-3">
        {metrics.map((metric) => (
          <article key={metric.label} className="soft-card p-5">
            <p className="text-xs font-semibold uppercase tracking-[0.2em] text-muted-foreground">{metric.label}</p>
            <p className="mt-4 text-2xl font-bold tracking-[-0.04em] text-foreground">{metric.value}</p>
            <p className="mt-2 text-sm text-muted-foreground">{metric.hint}</p>
          </article>
        ))}
      </section>

      <section className="grid gap-4 lg:grid-cols-3">
        {roadmapCards.map((card) => (
          <article key={card.title} className="panel p-5">
            <h3 className="text-lg font-bold tracking-[-0.03em] text-foreground">{card.title}</h3>
            <p className="mt-3 text-sm leading-6 text-muted-foreground">{card.text}</p>
          </article>
        ))}
      </section>
    </div>
  );
}
