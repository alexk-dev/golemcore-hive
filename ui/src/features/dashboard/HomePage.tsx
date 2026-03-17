import { useAuth } from '../../app/providers/AuthProvider';

const metrics = [
  { label: 'Storage', value: 'JSON-first', hint: 'No DB for v1' },
  { label: 'Auth', value: 'JWT + refresh', hint: 'Browser cookie rotation' },
  { label: 'Runtime', value: 'Spring Boot 4', hint: 'Java 25 / Maven' },
];

const roadmapCards = [
  {
    title: 'Fleet registration',
    text: 'Enrollment tokens, machine JWT scopes, and heartbeat-based presence are now online in the Fleet view.',
  },
  {
    title: 'Boards and cards',
    text: 'Multiple board flows, board teams, and Team/All assignee selection follow after fleet.',
  },
  {
    title: 'Card-bound threads',
    text: 'Operator chat, command dispatch, and lifecycle signal ingestion close the control loop.',
  },
];

export function HomePage() {
  const { user } = useAuth();

  return (
    <div className="grid gap-6">
      <section className="grid gap-4 lg:grid-cols-[1.3fr_0.9fr]">
        <article className="panel px-6 py-6 md:px-8">
          <div className="space-y-4">
            <span className="pill">Phase 1 + Phase 2</span>
            <h2 className="text-3xl font-bold tracking-[-0.04em] text-foreground">
              Hive now authenticates operators and manages a real runtime fleet instead of a static placeholder.
            </h2>
            <p className="max-w-2xl text-sm leading-7 text-muted-foreground md:text-base">
              The control plane can now issue one-time enrollment tokens, accept bot registration, track presence, and
              bind free-form roles, while leaving boards and command orchestration for the next phases.
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
              <p className="text-sm font-medium text-foreground">Ready for fleet bootstrap</p>
              <p className="mt-2 text-sm leading-6 text-muted-foreground">
                The current session now fronts a real fleet registry: JWT rotation, local JSON persistence, enrollment
                workflows, and heartbeat-aware runtime detail are in place before boards arrive.
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
