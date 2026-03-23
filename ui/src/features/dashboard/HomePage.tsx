import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { listBoards } from '../../lib/api/boardsApi';
import { listGolems } from '../../lib/api/golemsApi';
import { listApprovals } from '../../lib/api/approvalsApi';
import { listAuditEvents } from '../../lib/api/auditApi';

export function HomePage() {
  const boardsQuery = useQuery({ queryKey: ['boards'], queryFn: listBoards });
  const golemsQuery = useQuery({ queryKey: ['golems', '', '', ''], queryFn: () => listGolems() });
  const pendingApprovalsQuery = useQuery({
    queryKey: ['approvals', 'PENDING'],
    queryFn: () => listApprovals({ status: 'PENDING' }),
  });
  const recentAuditQuery = useQuery({
    queryKey: ['audit', { actorId: '', golemId: '', boardId: '', cardId: '', eventType: '' }],
    queryFn: () => listAuditEvents({}),
  });

  const boards = boardsQuery.data ?? [];
  const golems = golemsQuery.data ?? [];
  const pendingApprovals = pendingApprovalsQuery.data ?? [];
  const recentAudit = recentAuditQuery.data ?? [];

  const onlineGolems = golems.filter((g) => g.state === 'ONLINE').length;
  const totalCards = boards.reduce((sum, b) => sum + b.cardCounts.reduce((s, c) => s + c.count, 0), 0);

  return (
    <div className="grid gap-5">
      <section className="grid gap-3 md:grid-cols-2 lg:grid-cols-4">
        <MetricCard label="Boards" value={boards.length} to="/boards" />
        <MetricCard label="Cards" value={totalCards} to="/boards" />
        <MetricCard label="Golems online" value={`${onlineGolems}/${golems.length}`} to="/fleet" />
        <MetricCard label="Pending approvals" value={pendingApprovals.length} to="/approvals" />
      </section>

      <div className="grid gap-5 lg:grid-cols-2">
        <section className="panel p-5">
          <div className="flex items-center justify-between gap-3">
            <h3 className="text-base font-bold tracking-tight text-foreground">Boards</h3>
            <Link to="/boards" className="text-sm font-semibold text-primary">View all</Link>
          </div>
          <div className="mt-3 grid gap-2">
            {boards.length ? boards.slice(0, 5).map((board) => (
              <Link
                key={board.id}
                to={`/boards/${board.id}`}
                className="flex items-center justify-between gap-3 border border-border/70 bg-white/70 p-3 transition hover:bg-white"
              >
                <div>
                  <p className="text-sm font-semibold text-foreground">{board.name}</p>
                  <p className="text-xs text-muted-foreground">{board.templateKey} · {board.cardCounts.reduce((s, c) => s + c.count, 0)} cards</p>
                </div>
              </Link>
            )) : (
              <p className="text-sm text-muted-foreground">No boards yet.</p>
            )}
          </div>
        </section>

        <section className="panel p-5">
          <div className="flex items-center justify-between gap-3">
            <h3 className="text-base font-bold tracking-tight text-foreground">Recent audit</h3>
            <Link to="/audit" className="text-sm font-semibold text-primary">View all</Link>
          </div>
          <div className="mt-3 grid gap-2">
            {recentAudit.length ? recentAudit.slice(0, 6).map((event) => (
              <div
                key={event.id}
                className="border border-border/70 bg-white/70 p-3"
              >
                <div className="flex items-center justify-between gap-3">
                  <span className="pill">{event.eventType}</span>
                  <span className="text-xs text-muted-foreground">
                    {new Date(event.createdAt).toLocaleString()}
                  </span>
                </div>
                <p className="mt-1 text-sm text-foreground">{event.summary || 'No summary'}</p>
              </div>
            )) : (
              <p className="text-sm text-muted-foreground">No audit events yet.</p>
            )}
          </div>
        </section>
      </div>
    </div>
  );
}

function MetricCard({ label, value, to }: { label: string; value: number | string; to: string }) {
  return (
    <Link to={to} className="panel p-4 transition hover:shadow-md">
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="mt-2 text-2xl font-bold tracking-tight text-foreground">{value}</p>
    </Link>
  );
}
