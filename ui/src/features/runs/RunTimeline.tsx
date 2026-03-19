import { CardLifecycleSignal, CommandRecord, RunProjection } from '../../lib/api/commandsApi';
import { SignalBadge } from './SignalBadge';

type RunTimelineProps = {
  commands: CommandRecord[];
  runs: RunProjection[];
  signals: CardLifecycleSignal[];
};

type TimelineItem =
  | { kind: 'command'; timestamp: string; id: string; payload: CommandRecord }
  | { kind: 'run'; timestamp: string; id: string; payload: RunProjection }
  | { kind: 'signal'; timestamp: string; id: string; payload: CardLifecycleSignal };

export function RunTimeline({ commands, runs, signals }: RunTimelineProps) {
  const items: TimelineItem[] = [
    ...commands.map((command) => ({ kind: 'command' as const, timestamp: command.createdAt, id: command.id, payload: command })),
    ...runs.map((run) => ({ kind: 'run' as const, timestamp: run.updatedAt || run.createdAt, id: run.id, payload: run })),
    ...signals.map((signal) => ({ kind: 'signal' as const, timestamp: signal.createdAt, id: signal.id, payload: signal })),
  ].sort((left, right) => new Date(right.timestamp).getTime() - new Date(left.timestamp).getTime());

  return (
    <section className="panel p-6 md:p-8">
      <div>
        <span className="pill">Run timeline</span>
        <h3 className="mt-4 text-2xl font-bold tracking-[-0.04em] text-foreground">Commands, runs, and signals in one place</h3>
      </div>
      <div className="mt-6 grid gap-3">
        {items.length ? (
          items.map((item) => {
            if (item.kind === 'command') {
              return (
                <article key={`${item.kind}-${item.id}`} className="rounded-[20px] border border-border bg-white/70 p-4">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <span className="pill">Command</span>
                    <span className="text-xs uppercase tracking-[0.16em] text-muted-foreground">{item.payload.status}</span>
                  </div>
                  <p className="mt-3 text-sm leading-6 text-foreground">{item.payload.body}</p>
                  {item.payload.approvalRiskLevel ? (
                    <p className="mt-2 text-sm text-muted-foreground">
                      Approval {item.payload.approvalRiskLevel} · {item.payload.approvalRequestId || 'pending request'}
                    </p>
                  ) : null}
                  {item.payload.approvalReason ? (
                    <p className="mt-2 text-sm text-muted-foreground">{item.payload.approvalReason}</p>
                  ) : null}
                  {item.payload.queueReason ? <p className="mt-2 text-sm text-amber-900">{item.payload.queueReason}</p> : null}
                  <p className="mt-3 text-xs uppercase tracking-[0.16em] text-muted-foreground">{new Date(item.timestamp).toLocaleString()}</p>
                </article>
              );
            }
            if (item.kind === 'run') {
              return (
                <article key={`${item.kind}-${item.id}`} className="rounded-[20px] border border-border bg-white/70 p-4">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <span className="pill">Run</span>
                    <span className="text-xs uppercase tracking-[0.16em] text-muted-foreground">{item.payload.status}</span>
                  </div>
                  <p className="mt-3 text-sm text-muted-foreground">
                    {item.payload.summary || 'No run summary yet'} · events {item.payload.eventCount}
                  </p>
                  {item.payload.approvalRequestId ? (
                    <p className="mt-2 text-sm text-muted-foreground">Approval request {item.payload.approvalRequestId}</p>
                  ) : null}
                  <p className="mt-2 text-sm text-muted-foreground">
                    Tokens {item.payload.inputTokens}/{item.payload.outputTokens} · cost micros {item.payload.accumulatedCostMicros}
                  </p>
                  <p className="mt-3 text-xs uppercase tracking-[0.16em] text-muted-foreground">{new Date(item.timestamp).toLocaleString()}</p>
                </article>
              );
            }
            return (
              <article key={`${item.kind}-${item.id}`} className="rounded-[20px] border border-border bg-white/70 p-4">
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <SignalBadge signalType={item.payload.signalType} />
                  <span className="text-xs uppercase tracking-[0.16em] text-muted-foreground">
                    {item.payload.resolutionOutcome || 'RECORDED'}
                  </span>
                </div>
                <p className="mt-3 text-sm leading-6 text-foreground">{item.payload.summary}</p>
                {item.payload.details ? <p className="mt-2 whitespace-pre-wrap text-sm leading-6 text-muted-foreground">{item.payload.details}</p> : null}
                {item.payload.blockerCode ? (
                  <p className="mt-2 text-xs uppercase tracking-[0.16em] text-rose-900">Blocker code: {item.payload.blockerCode}</p>
                ) : null}
                {item.payload.evidenceRefs.length ? (
                  <div className="mt-3 flex flex-wrap gap-2">
                    {item.payload.evidenceRefs.map((ref) => (
                      <span
                        key={`${item.payload.id}-${ref.kind}-${ref.ref}`}
                        className="rounded-full border border-border bg-white/90 px-3 py-1 text-xs font-semibold text-muted-foreground"
                      >
                        {ref.kind}: {ref.ref}
                      </span>
                    ))}
                  </div>
                ) : null}
                {item.payload.resolutionSummary ? <p className="mt-2 text-sm text-muted-foreground">{item.payload.resolutionSummary}</p> : null}
                <p className="mt-3 text-xs uppercase tracking-[0.16em] text-muted-foreground">{new Date(item.timestamp).toLocaleString()}</p>
              </article>
            );
          })
        ) : (
          <div className="rounded-[20px] border border-dashed border-border px-4 py-8 text-sm text-muted-foreground">
            No run or signal activity yet.
          </div>
        )}
      </div>
    </section>
  );
}
