import { apiRequest } from './httpClient';
import type { NotificationEvent } from './systemApi';

export interface MailboxMessagePage {
  messages: NotificationEvent[];
  hasMore: boolean;
}

export interface MailboxMessageFilters {
  limit?: number;
  before?: string;
  sender?: string;
  tags?: string[];
  unreadOnly?: boolean;
}

export function listMailboxMessages(filters: MailboxMessageFilters = {}) {
  const searchParams = new URLSearchParams();
  if (filters.limit) {
    searchParams.set('limit', String(filters.limit));
  }
  if (filters.before) {
    searchParams.set('before', filters.before);
  }
  if (filters.sender) {
    searchParams.set('sender', filters.sender);
  }
  if (filters.tags?.length) {
    searchParams.set('tag', filters.tags.join(','));
  }
  if (filters.unreadOnly) {
    searchParams.set('unreadOnly', 'true');
  }
  const query = searchParams.toString();
  return apiRequest<MailboxMessagePage>(`/api/v1/mailbox/messages${query ? `?${query}` : ''}`);
}

export function acknowledgeMailboxMessage(notificationId: string) {
  return apiRequest<NotificationEvent>(`/api/v1/mailbox/messages/${notificationId}:read`, {
    method: 'POST',
  });
}
