import { useState } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../../app/providers/useAuth';

const navGroups = [
  {
    label: 'Operate',
    items: [
      { to: '/', label: 'Organization', end: true },
      { to: '/objectives', label: 'Objectives' },
      { to: '/services', label: 'Services' },
      { to: '/teams', label: 'Teams' },
      { to: '/policies', label: 'Policies' },
      { to: '/approvals', label: 'Approvals' },
    ],
  },
  {
    label: 'Fleet',
    items: [
      { to: '/fleet', label: 'Golems', end: true },
      { to: '/fleet/chat', label: 'Chat' },
      { to: '/fleet/roles', label: 'Roles' },
    ],
  },
  {
    label: 'Observe',
    items: [
      { to: '/audit', label: 'Audit' },
      { to: '/budgets', label: 'Budgets' },
    ],
  },
];

function SidebarContent({ logout, user, onNavigate }: {
  logout: () => void;
  user: { displayName?: string; roles?: string[] } | null;
  onNavigate?: () => void;
}) {
  return (
    <>
      <div className="px-5 py-5">
        <span className="text-base font-bold tracking-tight text-foreground">Hive</span>
      </div>

      <nav className="flex flex-1 flex-col gap-5 px-3">
        {navGroups.map((group) => (
          <div key={group.label}>
            <p className="px-3 pb-1 text-[11px] font-semibold uppercase tracking-widest text-muted-foreground">
              {group.label}
            </p>
            <div className="flex flex-col gap-0.5">
              {group.items.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={'end' in item ? item.end : undefined}
                  onClick={onNavigate}
                  className={({ isActive }) =>
                    [
                      'rounded-md px-3 py-2 text-sm transition',
                      isActive
                        ? 'bg-primary text-primary-foreground font-semibold'
                        : 'text-foreground/80 hover:bg-muted hover:text-foreground',
                    ].join(' ')
                  }
                >
                  {item.label}
                </NavLink>
              ))}
            </div>
          </div>
        ))}
      </nav>

      <div className="flex flex-col gap-0.5 border-t border-border/60 px-3 py-3">
        <NavLink
          to="/settings"
          onClick={onNavigate}
          className={({ isActive }) =>
            [
              'rounded-md px-3 py-2 text-sm transition',
              isActive
                ? 'bg-primary text-primary-foreground font-semibold'
                : 'text-foreground/80 hover:bg-muted hover:text-foreground',
            ].join(' ')
          }
        >
          Settings
        </NavLink>
      </div>

      <div className="border-t border-border/60 px-5 py-4">
        <p className="truncate text-sm font-medium text-foreground">{user?.displayName}</p>
        {user?.roles?.length ? (
          <p className="mt-0.5 truncate text-xs text-muted-foreground">{user.roles.join(' / ')}</p>
        ) : null}
        <button
          type="button"
          onClick={() => { logout(); }}
          className="mt-3 text-xs font-semibold text-muted-foreground transition hover:text-foreground"
        >
          Sign out
        </button>
      </div>
    </>
  );
}

export function AppShell() {
  const { logout, user } = useAuth();
  const [mobileOpen, setMobileOpen] = useState(false);

  return (
    <div className="flex min-h-screen">
      {/* Desktop sidebar */}
      <aside className="sticky top-0 hidden h-screen w-60 shrink-0 flex-col border-r border-border/70 bg-muted/60 backdrop-blur md:flex">
        <SidebarContent logout={logout} user={user} />
      </aside>

      {/* Mobile overlay */}
      {mobileOpen && (
        <div
          className="fixed inset-0 z-40 bg-black/60 md:hidden"
          onClick={() => setMobileOpen(false)}
        />
      )}

      {/* Mobile sidebar */}
      <aside
        className={[
          'fixed inset-y-0 left-0 z-50 flex w-64 flex-col border-r border-border/70 bg-muted transition-transform duration-200 md:hidden',
          mobileOpen ? 'translate-x-0' : '-translate-x-full',
        ].join(' ')}
      >
        <SidebarContent logout={logout} user={user} onNavigate={() => setMobileOpen(false)} />
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        {/* Mobile top bar */}
        <header className="sticky top-0 z-30 flex items-center border-b border-border/70 bg-background/90 px-4 py-2 backdrop-blur md:hidden">
          <button
            type="button"
            onClick={() => setMobileOpen(true)}
            className="mr-3 text-foreground"
            aria-label="Open menu"
          >
            <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
              <line x1="3" y1="5" x2="17" y2="5" />
              <line x1="3" y1="10" x2="17" y2="10" />
              <line x1="3" y1="15" x2="17" y2="15" />
            </svg>
          </button>
          <span className="text-sm font-bold tracking-tight text-foreground">Hive</span>
        </header>

        <main className="min-w-0 flex-1 p-3 sm:p-4">
          <div className="mx-auto max-w-[1600px]">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
}
