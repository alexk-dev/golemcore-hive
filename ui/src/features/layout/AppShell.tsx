import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../../app/providers/useAuth';

const navigationItems = [
  { to: '/', label: 'Overview' },
  { to: '/fleet', label: 'Fleet' },
  { to: '/boards', label: 'Boards' },
  { to: '/approvals', label: 'Approvals' },
  { to: '/audit', label: 'Audit' },
  { to: '/budgets', label: 'Budgets' },
  { to: '/settings', label: 'Settings' },
];

export function AppShell() {
  const { logout, user } = useAuth();

  return (
    <div className="min-h-screen px-3 py-3 md:px-4 md:py-4">
      <div className="mx-auto flex min-h-screen max-w-[1800px] flex-col gap-4">
        <header className="panel sticky top-3 z-40 border-white/70 bg-[rgba(255,251,245,0.92)]">
          <div className="flex flex-col gap-3 px-4 py-3 lg:flex-row lg:items-center lg:justify-between">
            <div className="flex min-w-0 flex-col gap-3 lg:flex-row lg:items-center">
              <div className="min-w-0">
                <span className="pill">Golemcore Hive</span>
                <p className="mt-1 text-sm font-semibold tracking-[-0.03em] text-foreground">
                  Operator workbench for boards, golems, and governed dispatch
                </p>
              </div>
              <nav className="flex flex-wrap gap-2 lg:ml-4">
                {navigationItems.map((item) => (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    className={({ isActive }) =>
                      [
                        'rounded-full px-3 py-2 text-sm font-medium transition',
                        isActive
                          ? 'bg-foreground text-white shadow-glow'
                          : 'bg-white/80 text-foreground hover:bg-white',
                      ].join(' ')
                    }
                  >
                    {item.label}
                  </NavLink>
                ))}
              </nav>
            </div>

            <div className="flex flex-wrap items-center gap-3">
              <div className="soft-card flex items-center gap-3 border border-primary/10 bg-[linear-gradient(135deg,rgba(238,109,52,0.12),rgba(11,164,124,0.08))] px-3 py-2">
                <div className="min-w-0">
                  <p className="text-sm font-semibold text-foreground">{user?.displayName}</p>
                  <p className="text-xs text-muted-foreground">
                    @{user?.username}
                    {user?.roles.length ? ` · ${user.roles.join(' / ')}` : ''}
                  </p>
                </div>
              </div>
              <button
                type="button"
                onClick={() => void logout()}
                className="rounded-full border border-foreground/10 bg-white/85 px-4 py-2 text-sm font-semibold text-foreground transition hover:bg-white"
              >
                Sign out
              </button>
            </div>
          </div>
        </header>

        <main className="flex-1">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
