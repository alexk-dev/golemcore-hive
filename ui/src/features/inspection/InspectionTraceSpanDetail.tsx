import { useState } from 'react';
import type { InspectionTraceSpan } from '../../lib/api/inspectionApi';
import { formatTraceDuration, formatTraceTimestamp, getTraceStatusTone } from './inspectionTraceFormat';
import {
  SnapshotPreview,
  TracePill,
  type DetailTab,
  type SnapshotExportHandler,
} from './InspectionTraceCommon';

export function InspectionTraceSpanDetail({
  traceId,
  span,
  onExportSnapshotPayload,
  isExportingSnapshot,
  onClose,
}: {
  traceId: string;
  span: InspectionTraceSpan;
  onExportSnapshotPayload: SnapshotExportHandler;
  isExportingSnapshot: boolean;
  onClose: () => void;
}) {
  const [activeTab, setActiveTab] = useState<DetailTab>('attributes');
  const attributes = Object.entries(span.attributes).filter(([, value]) => value != null && String(value).length > 0);

  const tabs: Array<{ key: DetailTab; label: string; count: number }> = [
    { key: 'attributes', label: 'Attributes', count: attributes.length },
    { key: 'events', label: 'Events', count: span.events.length },
    { key: 'snapshots', label: 'Snapshots', count: span.snapshots.length },
  ];

  return (
    <section className="border border-border/70 bg-muted/30 p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <p className="font-semibold text-foreground">{span.name ?? span.spanId}</p>
            <TracePill tone={getTraceStatusTone(span.statusCode)}>{span.statusCode ?? 'UNKNOWN'}</TracePill>
            <TracePill>{span.kind ?? 'UNKNOWN'}</TracePill>
            <TracePill>{formatTraceDuration(span.durationMs)}</TracePill>
          </div>
          <div className="mt-1 flex flex-wrap gap-3 text-xs text-muted-foreground">
            <span>span: {span.spanId}</span>
            {span.parentSpanId ? <span>parent: {span.parentSpanId}</span> : null}
            <span>trace: {traceId}</span>
            <span>{formatTraceTimestamp(span.startedAt)} - {formatTraceTimestamp(span.endedAt)}</span>
          </div>
        </div>
        <button
          type="button"
          onClick={onClose}
          className="border border-border bg-white px-3 py-1 text-xs font-semibold text-foreground"
        >
          Close
        </button>
      </div>

      <div className="mt-4 flex flex-wrap gap-2">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            type="button"
            onClick={() => setActiveTab(tab.key)}
            className={
              activeTab === tab.key
                ? 'bg-foreground px-3 py-1.5 text-xs font-semibold text-white'
                : 'border border-border bg-white px-3 py-1.5 text-xs font-semibold text-foreground'
            }
          >
            {tab.label} ({tab.count})
          </button>
        ))}
      </div>

      {activeTab === 'attributes' ? <AttributesTab attributes={attributes} /> : null}
      {activeTab === 'events' ? <EventsTab span={span} /> : null}
      {activeTab === 'snapshots' ? (
        <SnapshotsTab
          span={span}
          onExportSnapshotPayload={onExportSnapshotPayload}
          isExportingSnapshot={isExportingSnapshot}
        />
      ) : null}
    </section>
  );
}

function AttributesTab({ attributes }: { attributes: Array<[string, unknown]> }) {
  if (attributes.length === 0) {
    return <p className="mt-4 text-xs text-muted-foreground">No attributes recorded.</p>;
  }

  return (
    <div className="mt-4 overflow-auto">
      <table className="min-w-full border-collapse text-left text-xs">
        <thead>
          <tr className="border-b border-border">
            <th className="py-2 pr-4 font-semibold text-muted-foreground">Key</th>
            <th className="py-2 font-semibold text-muted-foreground">Value</th>
          </tr>
        </thead>
        <tbody>
          {attributes.map(([key, value]) => (
            <tr key={key} className="border-b border-border/60">
              <td className="py-2 pr-4 align-top font-medium text-foreground">{key}</td>
              <td className="py-2 align-top whitespace-pre-wrap break-words text-muted-foreground">{String(value)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function EventsTab({ span }: { span: InspectionTraceSpan }) {
  if (span.events.length === 0) {
    return <p className="mt-4 text-xs text-muted-foreground">No events recorded.</p>;
  }

  return (
    <div className="mt-4 grid gap-2">
      {span.events.map((event, index) => (
        <div key={`${event.name ?? 'event'}-${index}`} className="border border-border/70 bg-white/70 p-3">
          <div className="flex flex-wrap items-center gap-2">
            <TracePill>{event.name ?? 'event'}</TracePill>
            {event.timestamp ? <span className="text-xs text-muted-foreground">{formatTraceTimestamp(event.timestamp)}</span> : null}
          </div>
          {Object.entries(event.attributes).length > 0 ? (
            <p className="mt-2 whitespace-pre-wrap break-words text-xs text-muted-foreground">
              {Object.entries(event.attributes)
                .filter(([, value]) => value != null)
                .map(([key, value]) => `${key}=${String(value)}`)
                .join(', ')}
            </p>
          ) : null}
        </div>
      ))}
    </div>
  );
}

function SnapshotsTab({
  span,
  onExportSnapshotPayload,
  isExportingSnapshot,
}: {
  span: InspectionTraceSpan;
  onExportSnapshotPayload: SnapshotExportHandler;
  isExportingSnapshot: boolean;
}) {
  if (span.snapshots.length === 0) {
    return <p className="mt-4 text-xs text-muted-foreground">No snapshots stored.</p>;
  }

  return (
    <div className="mt-4 grid gap-3">
      {span.snapshots.map((snapshot) => (
        <SnapshotPreview
          key={snapshot.snapshotId}
          snapshot={snapshot}
          spanName={span.name}
          isExportingSnapshot={isExportingSnapshot}
          onExportSnapshotPayload={onExportSnapshotPayload}
        />
      ))}
    </div>
  );
}
