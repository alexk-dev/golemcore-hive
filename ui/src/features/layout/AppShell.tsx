import { useEffect, useState } from 'react';
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../../app/providers/useAuth';
import { buildAppShellNavigation, type AppShellSection } from './appShellNavigation';

export function AppShell() {
  const { logout, user } = useAuth();
  const location = useLocation();
  const [isMobileNavigationOpen, setIsMobileNavigationOpen] = useState(false);
  const navigation = buildAppShellNavigation(location.pathname);
  const userRoles = formatUserRoles(user?.roles ?? []);

  useEffect(() => {
    setIsMobileNavigationOpen(false);
  }, [location.pathname]);

  return (
    <div className="min-h-screen px-3 py-3 md:px-4 md:py-4 lg:px-0 lg:py-0">
      <div className="grid min-h-screen gap-4 lg:grid-cols-[280px_minmax(0,1fr)] lg:gap-0">
        <aside
          aria-hidden={isMobileNavigationOpen ? 'true' : undefined}
          className="hidden border-r border-border/70 bg-[rgba(255,251,245,0.92)] lg:flex lg:min-h-screen lg:flex-col lg:px-4 lg:py-5"
        >
          <div className="border-b border-border/70 pb-4">
            <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-muted-foreground">Operator workspace</p>
            <h1 className="mt-3 text-2xl font-bold tracking-[-0.05em] text-foreground">Golemcore Hive</h1>
          </div>

          <SidebarNavigation sections={navigation.sections} className="mt-5 flex-1" />

          <AccountPanel displayName={user?.displayName} username={user?.username} userRoles={userRoles} onLogout={() => void logout()} />
        </aside>

        <main className="min-w-0 lg:px-6 lg:py-6">
          <div className="mb-4 flex items-center justify-between border border-border/70 bg-[rgba(255,251,245,0.92)] px-4 py-3 lg:hidden">
            <div>
              <p className="text-[10px] font-semibold uppercase tracking-[0.18em] text-muted-foreground">Operator workspace</p>
              <p className="mt-1 text-lg font-bold tracking-[-0.04em] text-foreground">
                Hive
              </p>
            </div>
            <button
              type="button"
              aria-controls="mobile-navigation-menu"
              aria-expanded={isMobileNavigationOpen}
              onClick={() => setIsMobileNavigationOpen(true)}
              className="border border-foreground/10 bg-white/90 px-4 py-2 text-sm font-semibold text-foreground transition hover:bg-white"
            >
              Open navigation
            </button>
          </div>
          <Outlet />
        </main>
      </div>

      {isMobileNavigationOpen ? (
        <div className="fixed inset-0 z-50 lg:hidden">
          <button
            type="button"
            onClick={() => setIsMobileNavigationOpen(false)}
            className="absolute inset-0 bg-foreground/30"
          />
          <div
            id="mobile-navigation-menu"
            role="dialog"
            aria-modal="true"
            aria-label="Navigation menu"
            className="absolute inset-y-0 left-0 flex w-[min(320px,100vw)] flex-col border-r border-border/70 bg-[rgba(255,251,245,0.98)] px-4 py-5"
          >
            <div className="flex items-start justify-between gap-3 border-b border-border/70 pb-4">
              <div>
                <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-muted-foreground">Operator workspace</p>
                <h2 className="mt-3 text-2xl font-bold tracking-[-0.05em] text-foreground">Golemcore Hive</h2>
              </div>
              <button
                type="button"
                onClick={() => setIsMobileNavigationOpen(false)}
                className="border border-foreground/10 bg-white/90 px-3 py-2 text-sm font-semibold text-foreground transition hover:bg-white"
              >
                Close navigation
              </button>
            </div>

            <SidebarNavigation
              sections={navigation.sections}
              className="mt-5 flex-1 overflow-y-auto"
              onNavigate={() => setIsMobileNavigationOpen(false)}
            />

            <AccountPanel displayName={user?.displayName} username={user?.username} userRoles={userRoles} onLogout={() => void logout()} />
          </div>
        </div>
      ) : null}
    </div>
  );
}

function SidebarNavigation({
  className,
  onNavigate,
  sections,
}: {
  className?: string;
  onNavigate?: () => void;
  sections: AppShellSection[];
}) {
  return (
    <nav aria-label="Primary navigation" className={className}>
      <div className="grid gap-1.5">
        {sections.map((section) => (
          <div key={section.id} className="grid gap-1">
            <Link to={section.to} onClick={onNavigate} className={sectionLinkClassName(section.isActive)}>
              <span>{section.label}</span>
              {section.children.length ? (
                <span
                  aria-hidden="true"
                  className={[
                    'text-[11px] transition',
                    section.isActive ? 'text-white/80' : 'text-muted-foreground',
                  ].join(' ')}
                >
                  {section.isActive ? '−' : '+'}
                </span>
              ) : null}
            </Link>

            {section.isActive && section.children.length ? (
              <div className="ml-4 grid gap-1 border-l border-border/60 pl-3">
                {section.children.map((child) => (
                  <NavLink key={child.to} to={child.to} end onClick={onNavigate} className={({ isActive }) => childLinkClassName(isActive)}>
                    {child.label}
                  </NavLink>
                ))}
              </div>
            ) : null}
          </div>
        ))}
      </div>
    </nav>
  );
}

function AccountPanel({
  displayName,
  onLogout,
  userRoles,
  username,
}: {
  displayName: string | undefined;
  onLogout: () => void;
  userRoles: string;
  username: string | undefined;
}) {
  return (
    <div className="mt-6 border-t border-border/70 pt-4">
      <div className="section-surface flex items-center justify-between gap-3 px-3 py-3">
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold text-foreground">{displayName}</p>
          <p className="truncate text-xs text-muted-foreground">
            @{username}
            {userRoles}
          </p>
        </div>
        <button
          type="button"
          onClick={onLogout}
          className="shrink-0 border border-foreground/10 bg-white/90 px-3 py-2 text-xs font-semibold uppercase tracking-[0.14em] text-foreground transition hover:bg-white"
        >
          Sign out
        </button>
      </div>
    </div>
  );
}

function sectionLinkClassName(isActive: boolean) {
  if (isActive) {
    return 'flex items-center justify-between bg-foreground px-4 py-3 text-sm font-semibold text-white shadow-glow transition';
  }

  return 'flex items-center justify-between bg-white/70 px-4 py-3 text-sm font-semibold text-foreground transition hover:bg-white';
}

function childLinkClassName(isActive: boolean) {
  if (isActive) {
    return 'bg-primary/10 px-3 py-2 text-[13px] font-semibold text-foreground transition';
  }

  return 'px-3 py-2 text-[13px] font-medium text-muted-foreground transition hover:bg-white/80 hover:text-foreground';
}

function formatUserRoles(roles: string[]) {
  if (!roles.length) {
    return '';
  }

  return ` · ${roles.join(' / ')}`;
}
