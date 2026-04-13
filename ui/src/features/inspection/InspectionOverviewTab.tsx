import type { ApprovalRequest } from '../../lib/api/approvalsApi';
import type { GolemDetails } from '../../lib/api/golemsApi';
import type {
  InspectionSessionSummary,
  InspectionTraceSummary,
} from '../../lib/api/inspectionApi';
import type { SelfEvolvingRun } from '../../lib/api/selfEvolvingApi';
import { formatTimestamp } from '../../lib/format';
import {
  formatCost,
  formatNumber,
  formatUptime,
  policySyncTone,
  stateTone,
} from './inspectionOverviewFormat';
import {
  Fact,
  FactGrid,
  OverviewCard,
  StatStrip,
  TagRow,
} from './inspectionOverviewPrimitives';

interface InspectionOverviewTabProps {
  golem: GolemDetails | undefined;
  sessions: InspectionSessionSummary[];
  traceSummary: InspectionTraceSummary | null;
  selfEvolvingRuns: SelfEvolvingRun[];
  promotionApprovals: ApprovalRequest[];
  onOpenSessions: () => void;
  onOpenSelfEvolving: () => void;
  onSelectSession: (sessionId: string) => void;
}

export function InspectionOverviewTab(props: InspectionOverviewTabProps) {
  const { golem } = props;
  if (!golem) {
    return (
      <section className="panel p-6">
        <p className="text-sm text-muted-foreground">Loading golem overview...</p>
      </section>
    );
  }

  return (
    <div className="grid gap-4">
      <GolemStatStrip golem={golem} />

      <div className="grid gap-4 xl:grid-cols-[minmax(0,1.3fr)_minmax(0,1fr)]">
        <RuntimeProfileCard golem={golem} />

        <div className="grid gap-4">
          <PolicyBindingCard binding={golem.policyBinding ?? null} />
          <WorkloadCard golem={golem} activeSessions={props.sessions.length} />
        </div>
      </div>

      <div className="grid gap-4 xl:grid-cols-[minmax(0,1.2fr)_minmax(0,1fr)]">
        <RecentSessionsCard
          sessions={props.sessions}
          onOpenSessions={props.onOpenSessions}
          onSelectSession={props.onSelectSession}
        />
        <SelfEvolvingSummaryCard
          golem={golem}
          traceSummary={props.traceSummary}
          selfEvolvingRuns={props.selfEvolvingRuns}
          promotionApprovals={props.promotionApprovals}
          onOpenSelfEvolving={props.onOpenSelfEvolving}
        />
      </div>
    </div>
  );
}

function GolemStatStrip({ golem }: { golem: GolemDetails }) {
  const heartbeat = golem.lastHeartbeat;
  return (
    <StatStrip
      stats={[
        { label: 'State', value: golem.state, tone: stateTone(golem.state) },
        { label: 'Uptime', value: formatUptime(heartbeat?.uptimeSeconds ?? null) },
        { label: 'Last heartbeat', value: formatTimestamp(golem.lastHeartbeatAt) },
        { label: 'Health', value: heartbeat?.healthSummary ?? heartbeat?.status ?? 'n/a' },
        { label: 'Queue depth', value: String(heartbeat?.queueDepth ?? 0) },
        { label: 'Model tier', value: heartbeat?.modelTier ?? 'n/a' },
      ]}
    />
  );
}

function RuntimeProfileCard({ golem }: { golem: GolemDetails }) {
  const capabilities = golem.capabilities ?? null;
  return (
    <OverviewCard
      title="Runtime profile"
      description="How this golem is configured and what it can do."
    >
      <FactGrid>
        <Fact label="Runtime" value={golem.runtimeVersion ?? 'n/a'} />
        <Fact label="Build" value={golem.buildVersion ?? 'n/a'} />
        <Fact label="Host" value={golem.hostLabel ?? 'n/a'} />
        <Fact label="Registered" value={formatTimestamp(golem.registeredAt)} />
        <Fact label="Default model" value={capabilities?.defaultModel ?? 'n/a'} />
        <Fact label="Heartbeat every" value={`${golem.heartbeatIntervalSeconds}s`} />
      </FactGrid>
      <div className="mt-4 grid gap-3">
        <TagRow label="Providers" items={capabilities?.providers ?? []} />
        <TagRow label="Channels" items={capabilities?.supportedChannels ?? golem.supportedChannels} />
        <TagRow label="Tools" items={capabilities?.enabledTools ?? []} />
        <TagRow label="Roles" items={golem.roleSlugs} />
      </div>
    </OverviewCard>
  );
}

type PolicyBinding = NonNullable<GolemDetails['policyBinding']>;

function PolicyBindingCard({ binding }: { binding: PolicyBinding | null }) {
  return (
    <OverviewCard title="Policy binding" description="How the fleet policy is bound to this golem.">
      {binding ? (
        <FactGrid>
          <Fact label="Group" value={binding.policyGroupId} />
          <Fact label="Sync" value={binding.syncStatus ?? 'n/a'} tone={policySyncTone(binding.syncStatus)} />
          <Fact label="Target version" value={String(binding.targetVersion)} />
          <Fact label="Applied version" value={binding.appliedVersion != null ? String(binding.appliedVersion) : 'n/a'} />
          <Fact label="Last applied" value={formatTimestamp(binding.lastAppliedAt)} />
          <Fact label="Drift since" value={formatTimestamp(binding.driftSince)} />
        </FactGrid>
      ) : (
        <p className="text-sm text-muted-foreground">No policy binding recorded for this golem.</p>
      )}
    </OverviewCard>
  );
}

function WorkloadCard({ golem, activeSessions }: { golem: GolemDetails; activeSessions: number }) {
  const heartbeat = golem.lastHeartbeat;
  return (
    <OverviewCard title="Workload" description="What this golem has been processing recently.">
      <FactGrid>
        <Fact label="Input tokens" value={formatNumber(heartbeat?.inputTokens ?? 0)} />
        <Fact label="Output tokens" value={formatNumber(heartbeat?.outputTokens ?? 0)} />
        <Fact label="Cost" value={formatCost(heartbeat?.accumulatedCostMicros ?? 0)} />
        <Fact label="Active sessions" value={String(activeSessions)} />
      </FactGrid>
    </OverviewCard>
  );
}

function RecentSessionsCard({
  sessions,
  onOpenSessions,
  onSelectSession,
}: {
  sessions: InspectionSessionSummary[];
  onOpenSessions: () => void;
  onSelectSession: (sessionId: string) => void;
}) {
  const recentSessions = sessions.slice(0, 5);
  return (
    <OverviewCard
      title="Recent sessions"
      description="Latest conversations captured from this golem."
      action={
        sessions.length > 0 ? (
          <button
            type="button"
            onClick={onOpenSessions}
            className="rounded-lg border border-border/70 bg-muted/50 px-3 py-1.5 text-xs font-semibold text-foreground transition hover:bg-muted"
          >
            View all
          </button>
        ) : null
      }
    >
      {recentSessions.length === 0 ? (
        <p className="text-sm text-muted-foreground">No sessions captured yet for the selected channel.</p>
      ) : (
        <ul className="grid gap-2">
          {recentSessions.map((session) => (
            <RecentSessionRow
              key={session.id}
              session={session}
              onOpenSessions={onOpenSessions}
              onSelectSession={onSelectSession}
            />
          ))}
        </ul>
      )}
    </OverviewCard>
  );
}

function RecentSessionRow({
  session,
  onOpenSessions,
  onSelectSession,
}: {
  session: InspectionSessionSummary;
  onOpenSessions: () => void;
  onSelectSession: (sessionId: string) => void;
}) {
  return (
    <li>
      <button
        type="button"
        onClick={() => {
          onSelectSession(session.id);
          onOpenSessions();
        }}
        className="w-full rounded-lg border border-border/60 bg-muted/40 p-3 text-left transition hover:border-primary/40 hover:bg-muted/70"
      >
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold text-foreground">{session.title || session.id}</p>
            <p className="truncate text-xs text-muted-foreground">
              {session.channelType} · {session.messageCount} msgs · {formatTimestamp(session.updatedAt)}
            </p>
          </div>
          <span className="pill shrink-0">{session.state}</span>
        </div>
        {session.preview ? (
          <p className="mt-1 line-clamp-1 text-xs text-muted-foreground">{session.preview}</p>
        ) : null}
      </button>
    </li>
  );
}

function SelfEvolvingSummaryCard({
  golem,
  traceSummary,
  selfEvolvingRuns,
  promotionApprovals,
  onOpenSelfEvolving,
}: {
  golem: GolemDetails;
  traceSummary: InspectionTraceSummary | null;
  selfEvolvingRuns: SelfEvolvingRun[];
  promotionApprovals: ApprovalRequest[];
  onOpenSelfEvolving: () => void;
}) {
  const pendingApprovals = promotionApprovals.filter((approval) => approval.status === 'PENDING').length;
  const traceCount = traceSummary?.traceCount ?? 0;
  const spanCount = traceSummary?.spanCount ?? 0;
  const heartbeat = golem.lastHeartbeat;
  return (
    <OverviewCard
      title="Self-evolving"
      description="Automation activity gated to this golem."
      action={
        <button
          type="button"
          onClick={onOpenSelfEvolving}
          className="rounded-lg border border-border/70 bg-muted/50 px-3 py-1.5 text-xs font-semibold text-foreground transition hover:bg-muted"
        >
          Open control panel
        </button>
      }
    >
      <FactGrid>
        <Fact label="Runs" value={String(selfEvolvingRuns.length)} />
        <Fact
          label="Pending approvals"
          value={String(pendingApprovals)}
          tone={pendingApprovals > 0 ? 'warning' : 'muted'}
        />
        <Fact label="Traces" value={String(traceCount)} />
        <Fact label="Spans" value={String(spanCount)} />
      </FactGrid>
      {heartbeat?.lastErrorSummary ? (
        <p className="mt-3 rounded-lg border border-rose-500/40 bg-rose-500/10 p-3 text-xs text-rose-200">
          Last error: {heartbeat.lastErrorSummary}
        </p>
      ) : null}
    </OverviewCard>
  );
}

