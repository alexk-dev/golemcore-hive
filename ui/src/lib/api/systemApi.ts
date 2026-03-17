import { apiRequest } from './httpClient';

export type NotificationEvent = {
  id: string;
  type: string;
  severity: string;
  title: string;
  message: string;
  boardId: string | null;
  cardId: string | null;
  threadId: string | null;
  golemId: string | null;
  commandId: string | null;
  approvalId: string | null;
  acknowledged: boolean;
  createdAt: string;
  acknowledgedAt: string | null;
};

export type SystemSettings = {
  productionMode: boolean;
  storageBasePath: string;
  secureRefreshCookie: boolean;
  highCostThresholdMicros: number;
  retention: {
    approvalsDays: number;
    auditDays: number;
    notificationsDays: number;
  };
  notifications: {
    approvalRequested: boolean;
    blockerRaised: boolean;
    golemOffline: boolean;
    commandFailed: boolean;
  };
  recentNotifications: NotificationEvent[];
};

export function getSystemSettings() {
  return apiRequest<SystemSettings>('/api/v1/system/settings');
}

export function acknowledgeNotification(notificationId: string) {
  return apiRequest<NotificationEvent>(`/api/v1/system/notifications/${notificationId}:ack`, {
    method: 'POST',
  });
}
