import type { ReactNode } from 'react';
import { type FactTone, toneText } from './inspectionOverviewFormat';

export function StatStrip({
  stats,
}: {
  stats: Array<{ label: string; value: string; tone?: FactTone }>;
}) {
  return (
    <section className="panel p-0">
      <div className="grid grid-cols-2 divide-x divide-y divide-border/40 sm:grid-cols-3 xl:grid-cols-6 xl:divide-y-0">
        {stats.map((stat) => (
          <div key={stat.label} className="p-4">
            <p className="text-[11px] font-semibold uppercase tracking-[0.12em] text-muted-foreground">
              {stat.label}
            </p>
            <p className={`mt-1 text-sm font-bold ${toneText(stat.tone ?? 'muted')}`}>{stat.value}</p>
          </div>
        ))}
      </div>
    </section>
  );
}

export function OverviewCard({
  title,
  description,
  action,
  children,
}: {
  title: string;
  description?: string;
  action?: ReactNode;
  children: ReactNode;
}) {
  return (
    <section className="panel p-4">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 className="text-sm font-bold text-foreground">{title}</h2>
          {description ? <p className="mt-0.5 text-xs text-muted-foreground">{description}</p> : null}
        </div>
        {action}
      </div>
      <div className="mt-4">{children}</div>
    </section>
  );
}

export function FactGrid({ children }: { children: ReactNode }) {
  return <dl className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">{children}</dl>;
}

export function Fact({
  label,
  value,
  tone = 'muted',
}: {
  label: string;
  value: string;
  tone?: FactTone;
}) {
  return (
    <div className="min-w-0 rounded-lg border border-border/50 bg-muted/30 p-3">
      <dt className="text-[11px] font-semibold uppercase tracking-[0.12em] text-muted-foreground">{label}</dt>
      <dd className={`mt-1 truncate text-sm font-semibold ${toneText(tone)}`}>{value}</dd>
    </div>
  );
}

export function TagRow({ label, items }: { label: string; items: string[] }) {
  if (items.length === 0) {
    return (
      <div>
        <p className="text-[11px] font-semibold uppercase tracking-[0.12em] text-muted-foreground">{label}</p>
        <p className="mt-1 text-xs text-muted-foreground">None</p>
      </div>
    );
  }
  return (
    <div>
      <p className="text-[11px] font-semibold uppercase tracking-[0.12em] text-muted-foreground">{label}</p>
      <div className="mt-1 flex flex-wrap gap-1.5">
        {items.map((item) => (
          <span
            key={`${label}:${item}`}
            className="inline-flex items-center rounded-full border border-border/70 bg-muted/40 px-2 py-0.5 text-[11px] font-medium text-foreground"
          >
            {item}
          </span>
        ))}
      </div>
    </div>
  );
}
