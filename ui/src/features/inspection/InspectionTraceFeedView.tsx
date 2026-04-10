import { useMemo, useState } from 'react';
import type { InspectionMessage, InspectionTrace } from '../../lib/api/inspectionApi';
import { buildInspectionTraceFeed, type InspectionTraceFeedItem } from './inspectionTraceFeed';
import { formatTraceTimestamp } from './inspectionTraceFormat';
import { InspectionTraceSpanDetail } from './InspectionTraceSpanDetail';
import {
  TracePill,
  type SnapshotExportHandler,
} from './InspectionTraceCommon';

function bubbleClasses(kind: 'system' | 'llm' | 'tool' | 'outbound' | 'user' | 'assistant' | 'other'): string {
  switch (kind) {
    case 'user':
      return 'border-sky-200 bg-sky-50';
    case 'assistant':
      return 'border-emerald-200 bg-emerald-50';
    case 'tool':
      return 'border-amber-200 bg-amber-950/40';
    case 'llm':
      return 'border-accent/20 bg-accent/5';
    case 'outbound':
      return 'border-primary/25 bg-primary/5';
    case 'system':
      return 'border-border bg-panel';
    case 'other':
    default:
      return 'border-border bg-muted/40';
  }
}

export function InspectionTraceFeedView({
  messages,
  trace,
  onExportSnapshotPayload,
  isExportingSnapshot,
}: {
  messages: InspectionMessage[];
  trace: InspectionTrace;
  onExportSnapshotPayload: SnapshotExportHandler;
  isExportingSnapshot: boolean;
}) {
  const [selectedSpanId, setSelectedSpanId] = useState<string | null>(null);
  const feed = useMemo(() => buildInspectionTraceFeed(messages, trace), [messages, trace]);
  const spansById = useMemo(
    () => new Map(trace.traces.flatMap((record) => record.spans.map((span) => [span.spanId, { span, traceId: record.traceId }] as const))),
    [trace.traces],
  );
  const selected = selectedSpanId ? spansById.get(selectedSpanId) ?? null : null;

  if (feed.turns.length === 0) {
    return <p className="text-sm text-muted-foreground">No messages or trace spans were available for this session.</p>;
  }

  return (
    <div className="grid gap-4">
      {feed.turns.map((turn, index) => (
        <section key={turn.id} className="panel p-4">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <h4 className="text-sm font-bold text-foreground">
                {turn.title} {index + 1}
              </h4>
              {turn.timestamp ? <p className="text-xs text-muted-foreground">{formatTraceTimestamp(turn.timestamp)}</p> : null}
            </div>
            <div className="flex flex-wrap gap-2">
              {turn.traceNames.map((traceName) => (
                <TracePill key={`${turn.id}:${traceName}`}>{traceName}</TracePill>
              ))}
            </div>
          </div>

          <div className="mt-4 grid gap-3">
            {turn.items.map((item) => (
              <TraceBubble
                key={item.id}
                item={item}
                onSelectSpan={(spanId) => setSelectedSpanId(spanId)}
              />
            ))}
          </div>
        </section>
      ))}

      {selected ? (
        <InspectionTraceSpanDetail
          traceId={selected.traceId}
          span={selected.span}
          onExportSnapshotPayload={onExportSnapshotPayload}
          isExportingSnapshot={isExportingSnapshot}
          onClose={() => setSelectedSpanId(null)}
        />
      ) : null}
    </div>
  );
}

function TraceBubble({
  item,
  onSelectSpan,
}: {
  item: InspectionTraceFeedItem;
  onSelectSpan: (spanId: string) => void;
}) {
  const bubbleKind =
    item.type === 'message'
      ? item.role === 'user'
        ? 'user'
        : item.role === 'assistant'
          ? 'assistant'
          : 'other'
      : item.bubbleKind;

  return (
    <article className={`border p-4 ${bubbleClasses(bubbleKind)}`}>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="font-semibold text-foreground">{item.title}</p>
          {item.timestamp ? <p className="text-xs text-muted-foreground">{formatTraceTimestamp(item.timestamp)}</p> : null}
        </div>
      </div>

      <div className="mt-2 flex flex-wrap gap-2">
        {item.tags.map((tag) => (
          <TracePill key={`${item.id}:${tag.label}`} tone={tag.tone}>
            {tag.label}
          </TracePill>
        ))}
      </div>

      {item.type === 'message' ? (
        <div className="mt-3 whitespace-pre-wrap break-words text-sm text-foreground">{item.content}</div>
      ) : (
        <div className="mt-3 grid gap-3">
          {item.content ? <div className="whitespace-pre-wrap break-words text-sm text-foreground">{item.content}</div> : null}
          {item.eventNotes.length > 0 ? (
            <div className="grid gap-1 text-xs text-muted-foreground">
              {item.eventNotes.map((note) => (
                <p key={note}>{note}</p>
              ))}
            </div>
          ) : null}
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              onClick={() => onSelectSpan(item.spanId)}
              className="border border-border bg-panel px-3 py-1.5 text-xs font-semibold text-foreground"
            >
              Span detail
            </button>
            {item.hasPayloadInspect ? <TracePill tone="info">Payload captured</TracePill> : null}
          </div>
        </div>
      )}
    </article>
  );
}
