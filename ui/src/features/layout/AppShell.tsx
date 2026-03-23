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
    <div className="flex min-h-screen">
      <aside className="sticky top-0 flex h-screen w-48 shrink-0 flex-col border-r border-border/70 bg-white/60 backdrop-blur">
        <div className="px-4 py-4">
          <span className="text-sm font-bold tracking-tight text-foreground">Hive</span>
        </div>

        <nav className="flex flex-1 flex-col gap-0.5 px-2">
          {navigationItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              className={({ isActive }) =>
                [
                  'px-3 py-1.5 text-sm transition',
                  isActive
                    ? 'bg-foreground text-white font-semibold'
                    : 'text-foreground hover:bg-white',
                ].join(' ')
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>

        <div className="border-t border-border/60 px-4 py-3">
          <p className="truncate text-sm text-foreground">{user?.displayName}</p>
          {user?.roles?.length ? (
            <p className="truncate text-xs text-muted-foreground">{user.roles.join(' / ')}</p>
          ) : null}
          <button
            type="button"
            onClick={() => void logout()}
            className="mt-2 text-xs font-semibold text-muted-foreground transition hover:text-foreground"
          >
            Sign out
          </button>
        </div>
      </aside>

      <main className="min-w-0 flex-1 p-4">
        <div className="mx-auto max-w-[1600px]">
          <Outlet />
        </div>
      </main>
    </div>
  );
}
