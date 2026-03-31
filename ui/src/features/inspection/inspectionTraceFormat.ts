import type { InspectionTraceRecord, InspectionTraceSpan } from '../../lib/api/inspectionApi';

export type TraceTagTone = 'muted' | 'info' | 'success' | 'warning' | 'danger';

export interface TraceTreeNode {
  span: InspectionTraceSpan;
  children: TraceTreeNode[];
}

export function formatTraceTimestamp(value: string | null): string {
  if (value == null || value.length === 0) {
    return '';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '';
  }
  return date.toLocaleString(undefined, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    timeZoneName: 'short',
  });
}

export function formatTraceTime(value: string | null): string {
  if (value == null || value.length === 0) {
    return '';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '';
  }
  return date.toLocaleTimeString(undefined, {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    timeZoneName: 'short',
  });
}

export function formatTraceDuration(durationMs: number | null): string {
  if (durationMs == null) {
    return '-';
  }
  if (durationMs < 1000) {
    return `${durationMs} ms`;
  }
  return `${(durationMs / 1000).toFixed(durationMs >= 10_000 ? 0 : 1)} s`;
}

export function formatTraceBytes(value: number | null): string {
  if (value == null || value <= 0) {
    return '0 B';
  }
  if (value < 1024) {
    return `${value} B`;
  }
  if (value < 1024 * 1024) {
    return `${(value / 1024).toFixed(1)} KB`;
  }
  return `${(value / (1024 * 1024)).toFixed(1)} MB`;
}

export function getTraceStatusTone(statusCode: string | null): TraceTagTone {
  if (statusCode === 'ERROR') {
    return 'danger';
  }
  if (statusCode === 'OK') {
    return 'success';
  }
  return 'muted';
}

export function buildTraceTree(spans: InspectionTraceSpan[]): TraceTreeNode[] {
  const nodes = new Map<string, TraceTreeNode>();
  spans.forEach((span) => {
    nodes.set(span.spanId, { span, children: [] });
  });

  const roots: TraceTreeNode[] = [];
  spans.forEach((span) => {
    const node = nodes.get(span.spanId);
    if (node == null) {
      return;
    }
    if (span.parentSpanId == null) {
      roots.push(node);
      return;
    }
    const parent = nodes.get(span.parentSpanId);
    if (parent == null) {
      roots.push(node);
      return;
    }
    parent.children.push(node);
  });

  return roots;
}

export function flattenTraceTree(nodes: TraceTreeNode[], depth = 0): Array<{ span: InspectionTraceSpan; depth: number }> {
  return nodes.flatMap((node) => [
    { span: node.span, depth },
    ...flattenTraceTree(node.children, depth + 1),
  ]);
}

export function getInitialTrace(trace: InspectionTraceRecord[] | null | undefined): InspectionTraceRecord | null {
  if (trace == null || trace.length === 0) {
    return null;
  }
  return trace[0];
}
