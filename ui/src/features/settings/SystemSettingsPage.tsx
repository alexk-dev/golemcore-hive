import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { acknowledgeNotification, getSystemSettings } from '../../lib/api/systemApi';
import { PageHeader } from '../layout/PageHeader';

export function SystemSettingsPage() {
  const queryClient = useQueryClient();
  const settingsQuery = useQuery({
    queryKey: ['system-settings'],
    queryFn: getSystemSettings,
  });

  const acknowledgeMutation = useMutation({
    mutationFn: acknowledgeNotification,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['system-settings'] });
    },
  });

  if (!settingsQuery.data) {
    return <div className="section-surface px-4 py-3 text-sm text-muted-foreground">Loading settings…</div>;
  }

  const settings = settingsQuery.data;

  return (
    <div className="grid gap-6">
      <section className="panel p-6 md:p-8">
        <PageHeader
          eyebrow="Settings"
          title="System settings"
          description="Review deployment defaults and recent notifications."
        />
      </section>

      <section className="section-surface p-4">
        <dl className="divide-y divide-border/60">
          <SettingRow label="Production mode" value={settings.productionMode ? 'Enabled' : 'Disabled'} />
          <SettingRow label="Storage path" value={settings.storageBasePath} />
          <SettingRow label="Secure refresh cookie" value={settings.secureRefreshCookie ? 'Enabled' : 'Disabled'} />
          <SettingRow label="High-cost threshold" value={String(settings.highCostThresholdMicros)} />
          <SettingRow label="Audit retention" value={`${settings.retention.auditDays} days`} />
          <SettingRow label="Notifications retention" value={`${settings.retention.notificationsDays} days`} />
        </dl>
      </section>

      <section className="section-surface p-4">
        <h2 className="text-lg font-semibold tracking-[-0.03em] text-foreground">Notification defaults</h2>
        <dl className="mt-3 divide-y divide-border/60">
          <ToggleRow label="Approval requested" enabled={settings.notifications.approvalRequested} />
          <ToggleRow label="Blocker raised" enabled={settings.notifications.blockerRaised} />
          <ToggleRow label="Golem offline" enabled={settings.notifications.golemOffline} />
          <ToggleRow label="Command failed" enabled={settings.notifications.commandFailed} />
        </dl>
      </section>

      <section className="section-surface p-4">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <h2 className="text-lg font-semibold tracking-[-0.03em] text-foreground">Recent notifications</h2>
          <span className="text-sm text-muted-foreground">{settings.recentNotifications.length} stored events</span>
        </div>
        <div className="mt-3">
          {settings.recentNotifications.length ? (
            <ul className="divide-y divide-border/60">
              {settings.recentNotifications.map((notification) => (
                <li key={notification.id} className="dense-row py-4">
                  <div className="min-w-0 flex-1 space-y-2">
                    <div className="flex flex-wrap gap-2">
                      <span className="pill">{notification.type}</span>
                      <span className="pill">{notification.severity}</span>
                    </div>
                    <h3 className="text-base font-semibold tracking-[-0.03em] text-foreground">{notification.title}</h3>
                    <p className="text-sm text-muted-foreground">{notification.message}</p>
                  </div>
                  {!notification.acknowledged ? (
                    <button
                      type="button"
                      onClick={() => acknowledgeMutation.mutate(notification.id)}
                      disabled={acknowledgeMutation.isPending}
                      className="border border-border bg-white/80 px-4 py-3 text-sm font-semibold text-foreground disabled:opacity-60"
                    >
                      Acknowledge
                    </button>
                  ) : (
                    <span className="text-sm text-muted-foreground">Acknowledged</span>
                  )}
                </li>
              ))}
            </ul>
          ) : (
            <div className="section-surface px-4 py-3 text-sm text-muted-foreground">
              No notifications yet.
            </div>
          )}
        </div>
      </section>
    </div>
  );
}

function SettingRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="dense-row py-3">
      <dt className="text-sm font-semibold text-foreground">{label}</dt>
      <dd className="text-sm text-muted-foreground">{value}</dd>
    </div>
  );
}

function ToggleRow({ label, enabled }: { label: string; enabled: boolean }) {
  return (
    <div className="dense-row py-3">
      <dt className="text-sm font-semibold text-foreground">{label}</dt>
      <dd className="text-sm text-muted-foreground">{enabled ? 'Enabled' : 'Disabled'}</dd>
    </div>
  );
}
