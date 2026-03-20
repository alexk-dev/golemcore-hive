import { Link, NavLink, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../../app/providers/useAuth';
import { buildAppShellNavigation, type AppShellSection } from './appShellNavigation';

export function AppShell() {
  const { logout, user } = useAuth();
  const location = useLocation();
  const navigation = buildAppShellNavigation(location.pathname);
  const userRoles = formatUserRoles(user?.roles ?? []);

  return (
    <div className="min-h-screen px-3 py-3 md:px-4 md:py-4">
      <div className="mx-auto grid min-h-screen max-w-[1800px] gap-4 lg:grid-cols-[280px_minmax(0,1fr)]">
        <aside className="panel hidden border-white/70 bg-[rgba(255,251,245,0.92)] lg:flex lg:min-h-[calc(100vh-2rem)] lg:flex-col lg:px-4 lg:py-5">
          <div className="border-b border-border/70 pb-4">
            <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-muted-foreground">Operator workspace</p>
            <h1 className="mt-3 text-2xl font-bold tracking-[-0.05em] text-foreground">Golemcore Hive</h1>
          </div>

          <SidebarNavigation sections={navigation.sections} className="mt-5 flex-1" />

          <div className="mt-6 border-t border-border/70 pt-4">
            <div className="soft-card border border-primary/10 bg-[linear-gradient(135deg,rgba(238,109,52,0.12),rgba(11,164,124,0.08))] px-4 py-3">
              <p className="text-sm font-semibold text-foreground">{user?.displayName}</p>
              <p className="mt-1 text-xs text-muted-foreground">
                @{user?.username}
                {userRoles}
              </p>
            </div>
            <button
              type="button"
              onClick={() => void logout()}
              className="mt-3 w-full rounded-[18px] border border-foreground/10 bg-white/90 px-4 py-3 text-sm font-semibold text-foreground transition hover:bg-white"
            >
              Sign out
            </button>
          </div>
        </aside>

        <main className="min-w-0">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

function SidebarNavigation({ className, sections }: { className?: string; sections: AppShellSection[] }) {
  return (
    <nav aria-label="Primary navigation" className={className}>
      <div className="grid gap-1.5">
        {sections.map((section) => (
          <div key={section.id} className="grid gap-1">
            <Link to={section.to} className={sectionLinkClassName(section.isActive)}>
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
                  <NavLink key={child.to} to={child.to} end className={({ isActive }) => childLinkClassName(isActive)}>
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

function sectionLinkClassName(isActive: boolean) {
  if (isActive) {
    return 'flex items-center justify-between rounded-[18px] bg-foreground px-4 py-3 text-sm font-semibold text-white shadow-glow transition';
  }

  return 'flex items-center justify-between rounded-[18px] bg-white/70 px-4 py-3 text-sm font-semibold text-foreground transition hover:bg-white';
}

function childLinkClassName(isActive: boolean) {
  if (isActive) {
    return 'rounded-[14px] bg-primary/10 px-3 py-2 text-[13px] font-semibold text-foreground transition';
  }

  return 'rounded-[14px] px-3 py-2 text-[13px] font-medium text-muted-foreground transition hover:bg-white/80 hover:text-foreground';
}

function formatUserRoles(roles: string[]) {
  if (!roles.length) {
    return '';
  }

  return ` · ${roles.join(' / ')}`;
}
