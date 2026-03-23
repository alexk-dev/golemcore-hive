import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { acknowledgeNotification, getSystemSettings } from '../../lib/api/systemApi';
import {
  createEnrollmentToken,
  listEnrollmentTokens,
  revokeEnrollmentToken,
  type EnrollmentToken,
} from '../../lib/api/golemsApi';
import { EnrollmentTokenDialog } from '../golems/EnrollmentTokenDialog';
import { formatTimestamp } from '../../lib/format';

export function SystemSettingsPage() {
  const queryClient = useQueryClient();
  const [isEnrollmentDialogOpen, setIsEnrollmentDialogOpen] = useState(false);

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

  const tokensQuery = useQuery({
    queryKey: ['enrollment-tokens'],
    queryFn: listEnrollmentTokens,
  });

  const enrollmentMutation = useMutation({
    mutationFn: async (input: { note: string; expiresInMinutes: number | null }) =>
      createEnrollmentToken(input.note, input.expiresInMinutes),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['enrollment-tokens'] });
    },
  });

  const revokeTokenMutation = useMutation({
    mutationFn: async (tokenId: string) => revokeEnrollmentToken(tokenId, 'Revoked by operator'),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['enrollment-tokens'] });
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

      <EnrollmentTokensSection
        tokens={tokensQuery.data ?? []}
        isRevoking={revokeTokenMutation.isPending}
        onRevoke={(tokenId) => revokeTokenMutation.mutate(tokenId)}
        onCreateToken={() => setIsEnrollmentDialogOpen(true)}
      />

      <EnrollmentTokenDialog
        open={isEnrollmentDialogOpen}
        isPending={enrollmentMutation.isPending}
        createdToken={enrollmentMutation.data ?? null}
        onClose={() => {
          setIsEnrollmentDialogOpen(false);
          enrollmentMutation.reset();
        }}
        onCreate={async (input) => {
          await enrollmentMutation.mutateAsync(input);
        }}
      />

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

function EnrollmentTokensSection({
  tokens,
  isRevoking,
  onRevoke,
  onCreateToken,
}: {
  tokens: EnrollmentToken[];
  isRevoking: boolean;
  onRevoke: (tokenId: string) => void;
  onCreateToken: () => void;
}) {
  return (
    <section className="panel p-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h3 className="text-base font-bold tracking-tight text-foreground">Enrollment tokens</h3>
        <button
          type="button"
          onClick={onCreateToken}
          className="bg-foreground px-3 py-1.5 text-sm font-semibold text-white"
        >
          Create token
        </button>
      </div>
      <div className="mt-4 divide-y divide-border/50">
        {tokens.length ? (
          tokens.map((token) => (
            <div key={token.id} className="flex items-center justify-between gap-3 py-2">
              <div>
                <p className="text-sm text-foreground">{token.note || token.preview}</p>
                <p className="text-xs text-muted-foreground">
                  By {token.createdByUsername || 'operator'} · expires {formatTimestamp(token.expiresAt)}
                  {' · '}{token.registrationCount} registrations
                  {token.revoked ? ' · Revoked' : ''}
                </p>
              </div>
              {!token.revoked ? (
                <button
                  type="button"
                  disabled={isRevoking}
                  onClick={() => onRevoke(token.id)}
                  className="shrink-0 border border-rose-300 bg-rose-100 px-2 py-1 text-xs font-semibold text-rose-900"
                >
                  Revoke
                </button>
              ) : null}
            </div>
          ))
        ) : (
          <p className="py-2 text-sm text-muted-foreground">No enrollment tokens yet.</p>
        )}
      </div>
    </section>
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
