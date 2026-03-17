import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../../app/providers/AuthProvider';

const navigationItems = [
  { to: '/', label: 'Overview' },
  { to: '/fleet', label: 'Fleet' },
  { to: '/boards', label: 'Boards' },
];

export function AppShell() {
  const { logout, user } = useAuth();

  return (
    <div className="min-h-screen px-4 py-6 md:px-8">
      <div className="mx-auto flex max-w-7xl flex-col gap-6">
        <header className="panel overflow-hidden">
          <div className="grid gap-6 px-6 py-6 md:grid-cols-[1.4fr_0.8fr] md:px-8">
            <div className="space-y-4">
              <span className="pill">Golemcore Hive</span>
              <div className="space-y-2">
                <h1 className="max-w-3xl text-4xl font-bold tracking-[-0.04em] text-foreground md:text-5xl">
                  One control plane for cards, golems, and operator judgment.
                </h1>
                <p className="max-w-2xl text-sm leading-7 text-muted-foreground md:text-base">
                  Phase 3 adds live kanban boards, per-board team composition, and card assignment flows on top of the
                  fleet registry and machine auth foundation.
                </p>
              </div>
              <nav className="flex flex-wrap gap-2">
                {navigationItems.map((item) => (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    className={({ isActive }) =>
                      [
                        'rounded-full px-4 py-2 text-sm font-medium transition',
                        isActive
                          ? 'bg-foreground text-white shadow-glow'
                          : 'bg-white/70 text-foreground hover:bg-white',
                      ].join(' ')
                    }
                  >
                    {item.label}
                  </NavLink>
                ))}
              </nav>
            </div>

            <div className="soft-card flex flex-col justify-between gap-4 border border-primary/10 bg-[linear-gradient(135deg,rgba(238,109,52,0.16),rgba(11,164,124,0.12))] p-5">
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-semibold uppercase tracking-[0.24em] text-muted-foreground">
                    Signed in
                  </span>
                  <span className="rounded-full bg-white/70 px-3 py-1 text-xs font-semibold text-foreground">
                    {user?.roles.join(' / ')}
                  </span>
                </div>
                <div>
                  <p className="text-2xl font-bold tracking-[-0.04em] text-foreground">{user?.displayName}</p>
                  <p className="text-sm text-muted-foreground">@{user?.username}</p>
                </div>
              </div>
              <button
                type="button"
                onClick={() => void logout()}
                className="rounded-2xl border border-foreground/10 bg-white/80 px-4 py-3 text-sm font-semibold text-foreground transition hover:bg-white"
              >
                Sign out
              </button>
            </div>
          </div>
        </header>

        <main>
          <Outlet />
        </main>
      </div>
    </div>
  );
}
