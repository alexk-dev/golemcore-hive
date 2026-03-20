import { matchPath, NavLink, Outlet, useLocation } from 'react-router-dom';
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
  const location = useLocation();
  const isBoardRoute = matchPath({ path: '/boards/:boardId', end: true }, location.pathname) !== null;
  const shellTone = getShellTone(isBoardRoute);
  const userRoles = formatUserRoles(user?.roles ?? []);

  return (
    <div className={shellTone.outerShellClassName}>
      <div className={shellTone.innerShellClassName}>
        <header
          className={[
            'panel sticky z-40 border-white/70 bg-[rgba(255,251,245,0.92)]',
            shellTone.headerClassName,
          ].join(' ')}
        >
          <div
            className={[
              'flex flex-col lg:flex-row lg:items-center lg:justify-between',
              shellTone.headerLayoutClassName,
            ].join(' ')}
          >
            <div className={shellTone.brandRowClassName}>
              <div className="min-w-0">
                <span className={shellTone.brandPillClassName}>Golemcore Hive</span>
                {shellTone.showSubtitle ? (
                  <p className="mt-1 text-sm font-semibold tracking-[-0.03em] text-foreground">
                    Operator workbench for boards, golems, and governed dispatch
                  </p>
                ) : null}
              </div>
              <nav className={shellTone.navigationClassName}>
                {navigationItems.map((item) => (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    className={({ isActive }) => navigationLinkClassName(isBoardRoute, isActive)}
                  >
                    {item.label}
                  </NavLink>
                ))}
              </nav>
            </div>

            <div className={shellTone.accountRowClassName}>
              <div
                className={[
                  'soft-card flex items-center gap-3 border border-primary/10 bg-[linear-gradient(135deg,rgba(238,109,52,0.12),rgba(11,164,124,0.08))]',
                  shellTone.accountCardClassName,
                ].join(' ')}
              >
                <div className="min-w-0">
                  <p className="text-sm font-semibold text-foreground">{user?.displayName}</p>
                  <p className="text-xs text-muted-foreground">
                    @{user?.username}
                    {userRoles}
                  </p>
                </div>
              </div>
              <button
                type="button"
                onClick={() => void logout()}
                className={[
                  'rounded-full border border-foreground/10 bg-white/85 font-semibold text-foreground transition hover:bg-white',
                  shellTone.signOutButtonClassName,
                ].join(' ')}
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

function navigationLinkClassName(isBoardRoute: boolean, isActive: boolean) {
  if (isActive) {
    return ['rounded-full font-medium transition', isBoardRoute ? 'px-2.5 py-1.5 text-[13px]' : 'px-3 py-2 text-sm', 'bg-foreground text-white shadow-glow'].join(' ');
  }

  return [
    'rounded-full font-medium transition',
    isBoardRoute ? 'px-2.5 py-1.5 text-[13px]' : 'px-3 py-2 text-sm',
    isBoardRoute ? 'bg-white/65 text-foreground hover:bg-white' : 'bg-white/80 text-foreground hover:bg-white',
  ].join(' ');
}

function formatUserRoles(roles: string[]) {
  if (!roles.length) {
    return '';
  }

  return ` · ${roles.join(' / ')}`;
}

function getShellTone(isBoardRoute: boolean) {
  if (isBoardRoute) {
    return {
      outerShellClassName: 'min-h-screen px-2.5 py-2.5 md:px-3 md:py-3',
      innerShellClassName: 'mx-auto flex min-h-screen max-w-[1800px] flex-col gap-3',
      headerClassName: 'top-2 shadow-[0_8px_24px_rgba(34,29,24,0.05)]',
      headerLayoutClassName: 'gap-2.5 px-3 py-2.5',
      brandRowClassName: 'flex min-w-0 flex-col gap-2 lg:flex-row lg:items-center',
      brandPillClassName: 'pill px-2 py-0.5 text-[9px] tracking-[0.16em]',
      showSubtitle: false,
      navigationClassName: 'flex flex-wrap gap-1.5 lg:ml-3',
      accountRowClassName: 'flex flex-wrap items-center gap-2',
      accountCardClassName: 'px-2.5 py-1.5',
      signOutButtonClassName: 'px-3 py-1.5 text-[13px]',
    };
  }

  return {
    outerShellClassName: 'min-h-screen px-3 py-3 md:px-4 md:py-4',
    innerShellClassName: 'mx-auto flex min-h-screen max-w-[1800px] flex-col gap-4',
    headerClassName: 'top-3',
    headerLayoutClassName: 'gap-3 px-4 py-3',
    brandRowClassName: 'flex min-w-0 flex-col gap-3 lg:flex-row lg:items-center',
    brandPillClassName: 'pill',
    showSubtitle: true,
    navigationClassName: 'flex flex-wrap gap-2 lg:ml-4',
    accountRowClassName: 'flex flex-wrap items-center gap-3',
    accountCardClassName: 'px-3 py-2',
    signOutButtonClassName: 'px-4 py-2 text-sm',
  };
}
