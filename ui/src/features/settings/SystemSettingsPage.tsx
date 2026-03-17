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
    return <div className="panel p-6 md:p-8 text-sm text-muted-foreground">Loading system settings…</div>;
  }

  const settings = settingsQuery.data;

  return (
    <div className="grid gap-6">
      <section className="panel p-6 md:p-8">
        <span className="pill">Settings</span>
        <h2 className="mt-4 text-3xl font-bold tracking-[-0.04em] text-foreground">Operational defaults for self-hosted Hive</h2>
        <p className="mt-3 max-w-3xl text-sm leading-7 text-muted-foreground">
          Review deployment safeguards, retention windows, and lightweight notification hooks before packaging Hive for
          production use.
        </p>
      </section>

      <section className="grid gap-4 lg:grid-cols-3">
        <SettingCard label="Production mode" value={settings.productionMode ? 'Enabled' : 'Disabled'} />
        <SettingCard label="Storage path" value={settings.storageBasePath} />
        <SettingCard label="Secure refresh cookie" value={settings.secureRefreshCookie ? 'Enabled' : 'Disabled'} />
        <SettingCard label="High-cost threshold" value={String(settings.highCostThresholdMicros)} />
        <SettingCard label="Audit retention" value={`${settings.retention.auditDays} days`} />
        <SettingCard label="Notifications retention" value={`${settings.retention.notificationsDays} days`} />
      </section>

      <section className="panel p-6 md:p-8">
        <h3 className="text-2xl font-bold tracking-[-0.04em] text-foreground">Notification defaults</h3>
        <div className="mt-6 grid gap-3 md:grid-cols-2 xl:grid-cols-4">
          <ToggleCard label="Approval requested" enabled={settings.notifications.approvalRequested} />
          <ToggleCard label="Blocker raised" enabled={settings.notifications.blockerRaised} />
          <ToggleCard label="Golem offline" enabled={settings.notifications.golemOffline} />
          <ToggleCard label="Command failed" enabled={settings.notifications.commandFailed} />
        </div>
      </section>

      <section className="panel p-6 md:p-8">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <h3 className="text-2xl font-bold tracking-[-0.04em] text-foreground">Recent notifications</h3>
          <span className="text-sm text-muted-foreground">{settings.recentNotifications.length} stored events</span>
        </div>
        <div className="mt-6 grid gap-4">
          {settings.recentNotifications.length ? (
            settings.recentNotifications.map((notification) => (
              <article key={notification.id} className="soft-card p-5">
                <div className="flex flex-wrap items-start justify-between gap-4">
                  <div>
                    <div className="flex flex-wrap gap-2">
                      <span className="pill">{notification.type}</span>
                      <span className="pill">{notification.severity}</span>
                    </div>
                    <h4 className="mt-3 text-lg font-bold tracking-[-0.03em] text-foreground">{notification.title}</h4>
                    <p className="mt-2 text-sm leading-6 text-muted-foreground">{notification.message}</p>
                  </div>
                  {!notification.acknowledged ? (
                    <button
                      type="button"
                      onClick={() => acknowledgeMutation.mutate(notification.id)}
                      disabled={acknowledgeMutation.isPending}
                      className="rounded-[18px] border border-border bg-white/80 px-4 py-3 text-sm font-semibold text-foreground disabled:opacity-60"
                    >
                      Acknowledge
                    </button>
                  ) : (
                    <span className="text-sm text-muted-foreground">Acknowledged</span>
                  )}
                </div>
              </article>
            ))
          ) : (
            <div className="rounded-[22px] border border-dashed border-border px-4 py-8 text-sm text-muted-foreground">
              No notifications recorded yet.
            </div>
          )}
        </div>
      </section>
    </div>
  );
}

function SettingCard({ label, value }: { label: string; value: string }) {
  return (
    <article className="soft-card p-5">
      <p className="text-xs font-semibold uppercase tracking-[0.2em] text-muted-foreground">{label}</p>
      <p className="mt-4 text-xl font-bold tracking-[-0.03em] text-foreground">{value}</p>
    </article>
  );
}

function ToggleCard({ label, enabled }: { label: string; enabled: boolean }) {
  return (
    <div className="rounded-[20px] border border-border bg-white/80 px-4 py-4">
      <p className="text-sm font-semibold text-foreground">{label}</p>
      <p className="mt-2 text-sm text-muted-foreground">{enabled ? 'Enabled' : 'Disabled'}</p>
    </div>
  );
}
