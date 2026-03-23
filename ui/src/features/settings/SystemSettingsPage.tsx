import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { acknowledgeNotification, getSystemSettings } from '../../lib/api/systemApi';

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
    return <div className="panel p-6 text-sm text-muted-foreground">Loading settings…</div>;
  }

  const settings = settingsQuery.data;

  return (
    <div className="grid gap-5">
      <section className="grid gap-3 lg:grid-cols-3">
        <SettingCard label="Production mode" value={settings.productionMode ? 'Enabled' : 'Disabled'} />
        <SettingCard label="Storage path" value={settings.storageBasePath} />
        <SettingCard label="Secure refresh cookie" value={settings.secureRefreshCookie ? 'Enabled' : 'Disabled'} />
        <SettingCard label="High-cost threshold" value={String(settings.highCostThresholdMicros)} />
        <SettingCard label="Audit retention" value={`${settings.retention.auditDays} days`} />
        <SettingCard label="Notifications retention" value={`${settings.retention.notificationsDays} days`} />
      </section>

      <section className="panel p-5">
        <h3 className="text-base font-bold tracking-tight text-foreground">Notification defaults</h3>
        <div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-4">
          <ToggleCard label="Approval requested" enabled={settings.notifications.approvalRequested} />
          <ToggleCard label="Blocker raised" enabled={settings.notifications.blockerRaised} />
          <ToggleCard label="Golem offline" enabled={settings.notifications.golemOffline} />
          <ToggleCard label="Command failed" enabled={settings.notifications.commandFailed} />
        </div>
      </section>

      <section className="panel p-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h3 className="text-base font-bold tracking-tight text-foreground">Recent notifications</h3>
          <span className="text-xs text-muted-foreground">{settings.recentNotifications.length} events</span>
        </div>
        <div className="mt-4 grid gap-3">
          {settings.recentNotifications.length ? (
            settings.recentNotifications.map((notification) => (
              <article key={notification.id} className="border border-border/70 bg-white/70 p-4">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="space-y-1">
                    <div className="flex flex-wrap gap-2">
                      <span className="pill">{notification.type}</span>
                      <span className="pill">{notification.severity}</span>
                    </div>
                    <p className="text-sm font-medium text-foreground">{notification.title}</p>
                    <p className="text-sm text-muted-foreground">{notification.message}</p>
                  </div>
                  {!notification.acknowledged ? (
                    <button
                      type="button"
                      onClick={() => acknowledgeMutation.mutate(notification.id)}
                      disabled={acknowledgeMutation.isPending}
                      className="border border-border bg-white/80 px-3 py-1.5 text-sm font-semibold text-foreground disabled:opacity-60"
                    >
                      Acknowledge
                    </button>
                  ) : (
                    <span className="text-xs text-muted-foreground">Acknowledged</span>
                  )}
                </div>
              </article>
            ))
          ) : (
            <p className="text-sm text-muted-foreground">No notifications yet.</p>
          )}
        </div>
      </section>
    </div>
  );
}

function SettingCard({ label, value }: { label: string; value: string }) {
  return (
    <article className="panel p-4">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="mt-2 text-lg font-bold tracking-tight text-foreground">{value}</p>
    </article>
  );
}

function ToggleCard({ label, enabled }: { label: string; enabled: boolean }) {
  return (
    <div className="border border-border bg-white/80 px-4 py-3">
      <p className="text-sm font-semibold text-foreground">{label}</p>
      <p className="mt-1 text-sm text-muted-foreground">{enabled ? 'Enabled' : 'Disabled'}</p>
    </div>
  );
}
