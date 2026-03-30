import type {
  InspectionMessage,
  InspectionTrace,
  InspectionTraceSnapshot,
  InspectionTraceSpan,
  InspectionTraceSpanEvent,
} from '../../lib/api/inspectionApi';
import { formatTraceDuration, type TraceTagTone } from './inspectionTraceFormat';

export interface InspectionTraceFeedTag {
  label: string;
  tone: TraceTagTone;
}

export interface InspectionTraceFeedMessageItem {
  id: string;
  type: 'message';
  role: string;
  title: string;
  content: string;
  timestamp: string | null;
  tags: InspectionTraceFeedTag[];
}

export interface InspectionTraceFeedSpanItem {
  id: string;
  type: 'span';
  spanId: string;
  bubbleKind: 'system' | 'llm' | 'tool' | 'outbound';
  title: string;
  timestamp: string | null;
  content: string | null;
  eventNotes: string[];
  tags: InspectionTraceFeedTag[];
  snapshots: InspectionTraceSnapshot[];
  hasPayloadInspect: boolean;
}

export type InspectionTraceFeedItem = InspectionTraceFeedMessageItem | InspectionTraceFeedSpanItem;

export interface InspectionTraceFeedTurn {
  id: string;
  title: string;
  timestamp: string | null;
  traceNames: string[];
  items: InspectionTraceFeedItem[];
}

export interface InspectionTraceFeed {
  turns: InspectionTraceFeedTurn[];
}

interface FlatFeedItem {
  item: InspectionTraceFeedItem;
  sortTime: number;
  priority: number;
  turnSeedTitle: string;
  traceName: string | null;
}

function parseTimestamp(value: string | null): number {
  if (value == null || value.length === 0) {
    return Number.MAX_SAFE_INTEGER;
  }
  const parsed = Date.parse(value);
  return Number.isNaN(parsed) ? Number.MAX_SAFE_INTEGER : parsed;
}

function buildMessageTags(message: InspectionMessage): InspectionTraceFeedTag[] {
  const tags: InspectionTraceFeedTag[] = [];
  if (message.skill != null && message.skill.length > 0) {
    tags.push({ label: `skill ${message.skill}`, tone: 'info' });
  }
  if (message.modelTier != null && message.modelTier.length > 0) {
    tags.push({ label: `tier ${message.modelTier}`, tone: 'muted' });
  }
  if (message.model != null && message.model.length > 0) {
    tags.push({ label: `model ${message.model}`, tone: 'warning' });
  }
  if (message.reasoning != null && message.reasoning.length > 0) {
    tags.push({ label: `reasoning ${message.reasoning}`, tone: 'muted' });
  }
  return tags;
}

function buildMessageItem(message: InspectionMessage): InspectionTraceFeedMessageItem {
  return {
    id: message.id,
    type: 'message',
    role: message.role,
    title: message.role === 'user' ? 'User' : message.role === 'assistant' ? 'Assistant' : message.role,
    content: message.content,
    timestamp: message.timestamp,
    tags: buildMessageTags(message),
  };
}

function inferSource(span: InspectionTraceSpan): string | null {
  const attrSource = span.attributes['context.model.source'];
  if (typeof attrSource === 'string' && attrSource.length > 0) {
    return attrSource;
  }
  for (const event of span.events) {
    const eventSource = event.attributes.source;
    if (typeof eventSource === 'string' && eventSource.length > 0) {
      return eventSource;
    }
  }
  return null;
}

function buildBaseSpanTags(span: InspectionTraceSpan): InspectionTraceFeedTag[] {
  const tags: InspectionTraceFeedTag[] = [];
  if (span.kind != null && span.kind.length > 0) {
    tags.push({ label: span.kind.toLowerCase(), tone: 'muted' });
  }
  if (span.statusCode != null && span.statusCode.length > 0) {
    tags.push({
      label: span.statusCode,
      tone: span.statusCode === 'ERROR' ? 'danger' : 'success',
    });
  }
  if (span.durationMs != null) {
    tags.push({ label: formatTraceDuration(span.durationMs), tone: 'muted' });
  }
  return tags;
}

function appendContextTags(tags: InspectionTraceFeedTag[], span: InspectionTraceSpan): void {
  const skill = span.attributes['context.skill.name'];
  if (typeof skill === 'string' && skill.length > 0) {
    tags.push({ label: `skill ${skill}`, tone: 'info' });
  }
  const tier = span.attributes['context.model.tier'];
  if (typeof tier === 'string' && tier.length > 0) {
    tags.push({ label: `tier ${tier}`, tone: 'muted' });
  }
  const model = span.attributes['context.model.id'];
  if (typeof model === 'string' && model.length > 0) {
    tags.push({ label: `model ${model}`, tone: 'warning' });
  }
  const reasoning = span.attributes['context.model.reasoning'];
  if (typeof reasoning === 'string' && reasoning.length > 0) {
    tags.push({ label: `reasoning ${reasoning}`, tone: 'muted' });
  }
}

function formatEventTriple(eventName: string, first: unknown, second: unknown, third: unknown): string {
  return `${eventName}: ${String(first ?? '-')} -> ${String(second ?? '-')} -> ${String(third ?? '-')}`;
}

function formatEventTransition(eventName: string, fromValue: unknown, toValue: unknown): string {
  return `${eventName}: ${String(fromValue ?? '-')} -> ${String(toValue ?? '-')}`;
}

function formatTierTransitionNote(event: InspectionTraceSpanEvent): string {
  return `${event.name}: ${String(event.attributes.from_tier ?? '-')} / ${String(event.attributes.from_model_id ?? '-')} -> ${String(event.attributes.to_tier ?? '-')} / ${String(event.attributes.to_model_id ?? '-')}`;
}

function buildEventNote(event: InspectionTraceSpanEvent): string {
  switch (event.name) {
    case null:
      return 'event';
    case 'request.context':
      return formatEventTriple(event.name, event.attributes.skill, event.attributes.tier, event.attributes.model_id);
    case 'skill.transition.requested':
    case 'skill.transition.applied':
      return formatEventTransition(event.name, event.attributes.from_skill, event.attributes.to_skill);
    case 'tier.resolved':
      return formatEventTriple(event.name, event.attributes.skill, event.attributes.tier, event.attributes.model_id);
    case 'tier.transition':
      return formatTierTransitionNote(event);
    default:
      return event.name ?? 'event';
  }
}

function appendEventTags(tags: InspectionTraceFeedTag[], span: InspectionTraceSpan): void {
  for (const event of span.events) {
    if (event.name === 'tier.resolved') {
      const skill = event.attributes.skill;
      const tier = event.attributes.tier;
      const model = event.attributes.model_id;
      if (typeof skill === 'string' && skill.length > 0) {
        tags.push({ label: `skill ${skill}`, tone: 'info' });
      }
      if (typeof tier === 'string' && tier.length > 0) {
        tags.push({ label: `tier ${tier}`, tone: 'muted' });
      }
      if (typeof model === 'string' && model.length > 0) {
        tags.push({ label: `model ${model}`, tone: 'warning' });
      }
    }
    if (event.name === 'tier.transition') {
      const nextTier = event.attributes.to_tier;
      const nextModel = event.attributes.to_model_id;
      if (typeof nextTier === 'string' && nextTier.length > 0) {
        tags.push({ label: `tier ${nextTier}`, tone: 'muted' });
      }
      if (typeof nextModel === 'string' && nextModel.length > 0) {
        tags.push({ label: `model ${nextModel}`, tone: 'warning' });
      }
    }
  }
}

function dedupeTags(tags: InspectionTraceFeedTag[]): InspectionTraceFeedTag[] {
  const seen = new Set<string>();
  return tags.filter((tag) => {
    if (seen.has(tag.label)) {
      return false;
    }
    seen.add(tag.label);
    return true;
  });
}

function resolveBubbleKind(span: InspectionTraceSpan): InspectionTraceFeedSpanItem['bubbleKind'] {
  if (span.kind === 'LLM') {
    return 'llm';
  }
  if (span.kind === 'TOOL') {
    return 'tool';
  }
  if (span.kind === 'OUTBOUND') {
    return 'outbound';
  }
  return 'system';
}

function buildSpanContent(span: InspectionTraceSpan, eventNotes: string[]): string | null {
  if (span.statusMessage != null && span.statusMessage.length > 0) {
    return span.statusMessage;
  }
  if (eventNotes.length > 0) {
    return eventNotes[0];
  }
  return null;
}

function buildSpanItem(traceId: string, span: InspectionTraceSpan): InspectionTraceFeedSpanItem {
  const tags = buildBaseSpanTags(span);
  appendContextTags(tags, span);
  appendEventTags(tags, span);
  const eventNotes = span.events.map((event) => buildEventNote(event));
  inferSource(span);

  return {
    id: `span:${traceId}:${span.spanId}`,
    type: 'span',
    spanId: span.spanId,
    bubbleKind: resolveBubbleKind(span),
    title: span.name ?? span.spanId,
    timestamp: span.startedAt,
    content: buildSpanContent(span, eventNotes),
    eventNotes,
    tags: dedupeTags(tags),
    snapshots: span.snapshots,
    hasPayloadInspect: span.snapshots.some((snapshot) => snapshot.payloadAvailable),
  };
}

function buildFlatItems(messages: InspectionMessage[], trace: InspectionTrace | null): FlatFeedItem[] {
  const flatItems: FlatFeedItem[] = messages.map((message) => ({
    item: buildMessageItem(message),
    sortTime: parseTimestamp(message.timestamp),
    priority: message.role === 'user' ? 0 : message.role === 'assistant' ? 3 : 4,
    turnSeedTitle: message.role === 'user' ? 'User turn' : 'System turn',
    traceName: null,
  }));

  if (trace == null) {
    return flatItems.sort((left, right) => left.sortTime - right.sortTime || left.priority - right.priority);
  }

  for (const record of trace.traces) {
    for (const span of record.spans) {
      if (span.spanId === record.rootSpanId) {
        continue;
      }
      flatItems.push({
        item: buildSpanItem(record.traceId, span),
        sortTime: parseTimestamp(span.startedAt),
        priority: 1,
        turnSeedTitle: 'System turn',
        traceName: record.traceName,
      });
    }
  }

  return flatItems.sort((left, right) => left.sortTime - right.sortTime || left.priority - right.priority);
}

function createTurn(index: number, title: string, timestamp: string | null): InspectionTraceFeedTurn {
  return {
    id: `turn-${index}`,
    title,
    timestamp,
    traceNames: [],
    items: [],
  };
}

export function buildInspectionTraceFeed(messages: InspectionMessage[], trace: InspectionTrace | null): InspectionTraceFeed {
  const flatItems = buildFlatItems(messages, trace);
  if (flatItems.length === 0) {
    return { turns: [] };
  }

  const turns: InspectionTraceFeedTurn[] = [];
  let currentTurn = createTurn(0, flatItems[0].turnSeedTitle, flatItems[0].item.timestamp);
  turns.push(currentTurn);

  flatItems.forEach((flatItem, index) => {
    const nextTurn = flatItem.item.type === 'message' && flatItem.item.role === 'user' && currentTurn.items.length > 0;
    if (nextTurn) {
      currentTurn = createTurn(turns.length, flatItem.turnSeedTitle, flatItem.item.timestamp);
      turns.push(currentTurn);
    } else if (index === 0) {
      currentTurn.title = flatItem.turnSeedTitle;
      currentTurn.timestamp = flatItem.item.timestamp;
    }

    currentTurn.items.push(flatItem.item);
    if (flatItem.traceName != null && !currentTurn.traceNames.includes(flatItem.traceName)) {
      currentTurn.traceNames.push(flatItem.traceName);
    }
  });

  return { turns };
}
