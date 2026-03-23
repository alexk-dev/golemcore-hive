import type { CardLifecycleSignal, CommandRecord, RunProjection } from '../../lib/api/commandsApi';
import { SignalBadge } from './SignalBadge';

interface RunTimelineProps {
  commands: CommandRecord[];
  runs: RunProjection[];
  signals: CardLifecycleSignal[];
}

type TimelineItem =
  | { kind: 'command'; timestamp: string; id: string; payload: CommandRecord }
  | { kind: 'run'; timestamp: string; id: string; payload: RunProjection }
  | { kind: 'signal'; timestamp: string; id: string; payload: CardLifecycleSignal };

function CommandTimelineItem({ item }: { item: Extract<TimelineItem, { kind: 'command' }> }) {
  return (
    <article className="border border-border bg-white/70 p-3">
      <div className="flex items-center justify-between gap-2">
        <span className="text-xs font-semibold text-muted-foreground">Command</span>
        <span className="text-xs text-muted-foreground">{item.payload.status}</span>
      </div>
      <p className="mt-2 text-sm text-foreground">{item.payload.body}</p>
      {item.payload.approvalRiskLevel ? (
        <p className="mt-1 text-xs text-muted-foreground">
          {item.payload.approvalRiskLevel} · {item.payload.approvalRequestId || 'pending'}
        </p>
      ) : null}
      {item.payload.cancelRequestedAt ? (
        <p className="mt-1 text-xs text-rose-900">
          Stop requested{item.payload.cancelRequestedByActorName ? ` by ${item.payload.cancelRequestedByActorName}` : ''}
        </p>
      ) : null}
      <p className="mt-2 text-xs text-muted-foreground">{new Date(item.timestamp).toLocaleString()}</p>
    </article>
  );
}

function RunTimelineItemCard({ item }: { item: Extract<TimelineItem, { kind: 'run' }> }) {
  return (
    <article className="border border-border bg-white/70 p-3">
      <div className="flex items-center justify-between gap-2">
        <span className="text-xs font-semibold text-muted-foreground">Run</span>
        <span className="text-xs text-muted-foreground">{item.payload.status}</span>
      </div>
      <p className="mt-2 text-sm text-muted-foreground">
        {item.payload.summary || 'No summary'} · {item.payload.eventCount} events
      </p>
      {item.payload.cancelRequestedAt ? (
        <p className="mt-1 text-xs text-rose-900">
          Stop requested{item.payload.cancelRequestedByActorName ? ` by ${item.payload.cancelRequestedByActorName}` : ''}
        </p>
      ) : null}
      <p className="mt-1 text-xs text-muted-foreground">
        Tokens {item.payload.inputTokens}/{item.payload.outputTokens} · cost {item.payload.accumulatedCostMicros}
      </p>
      <p className="mt-2 text-xs text-muted-foreground">{new Date(item.timestamp).toLocaleString()}</p>
    </article>
  );
}

function SignalTimelineItemCard({ item }: { item: Extract<TimelineItem, { kind: 'signal' }> }) {
  return (
    <article className="border border-border bg-white/70 p-3">
      <div className="flex items-center justify-between gap-2">
        <SignalBadge signalType={item.payload.signalType} />
        <span className="text-xs text-muted-foreground">
          {item.payload.resolutionOutcome || 'RECORDED'}
        </span>
      </div>
      <p className="mt-2 text-sm text-foreground">{item.payload.summary}</p>
      {item.payload.details ? (
        <p className="mt-1 whitespace-pre-wrap text-sm text-muted-foreground">{item.payload.details}</p>
      ) : null}
      {item.payload.evidenceRefs.length ? (
        <div className="mt-2 flex flex-wrap gap-1">
          {item.payload.evidenceRefs.map((ref) => (
            <span
              key={`${item.payload.id}-${ref.kind}-${ref.ref}`}
              className="border border-border bg-white/90 px-2 py-0.5 text-xs text-muted-foreground"
            >
              {ref.kind}: {ref.ref}
            </span>
          ))}
        </div>
      ) : null}
      <p className="mt-2 text-xs text-muted-foreground">{new Date(item.timestamp).toLocaleString()}</p>
    </article>
  );
}

export function RunTimeline({ commands, runs, signals }: RunTimelineProps) {
  const items: TimelineItem[] = [
    ...commands.map((command) => ({ kind: 'command' as const, timestamp: command.createdAt, id: command.id, payload: command })),
    ...runs.map((run) => ({ kind: 'run' as const, timestamp: run.updatedAt || run.createdAt, id: run.id, payload: run })),
    ...signals.map((signal) => ({ kind: 'signal' as const, timestamp: signal.createdAt, id: signal.id, payload: signal })),
  ].sort((left, right) => new Date(right.timestamp).getTime() - new Date(left.timestamp).getTime());

  return (
    <section className="panel p-4">
      <h3 className="text-base font-bold tracking-tight text-foreground">Timeline</h3>
      <div className="mt-3 grid gap-2">
        {items.length ? (
          items.map((item) => {
            if (item.kind === 'command') {
              return <CommandTimelineItem key={`${item.kind}-${item.id}`} item={item} />;
            }
            if (item.kind === 'run') {
              return <RunTimelineItemCard key={`${item.kind}-${item.id}`} item={item} />;
            }
            return <SignalTimelineItemCard key={`${item.kind}-${item.id}`} item={item} />;
          })
        ) : (
          <p className="text-sm text-muted-foreground">No activity yet.</p>
        )}
      </div>
    </section>
  );
}
