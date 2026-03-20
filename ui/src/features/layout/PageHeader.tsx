import type { ReactNode } from 'react';

interface PageHeaderProps {
  title: ReactNode;
  eyebrow?: ReactNode;
  description?: ReactNode;
  meta?: ReactNode;
  actions?: ReactNode;
}

export function PageHeader({
  title,
  eyebrow,
  description,
  meta,
  actions,
}: PageHeaderProps) {
  return (
    <section className="page-header">
      <div className="min-w-0 space-y-2">
        {eyebrow ? <div className="pill">{eyebrow}</div> : null}
        <h1 className="text-3xl font-bold tracking-[-0.05em] text-foreground">{title}</h1>
        {description ? <p className="max-w-3xl text-sm text-muted-foreground md:text-[15px]">{description}</p> : null}
      </div>

      {meta || actions ? (
        <div className="flex min-w-0 flex-wrap items-start justify-end gap-3">
          {meta ? <div className="meta-kv">{meta}</div> : null}
          {actions ? <div className="flex flex-wrap items-center justify-end gap-2">{actions}</div> : null}
        </div>
      ) : null}
    </section>
  );
}
