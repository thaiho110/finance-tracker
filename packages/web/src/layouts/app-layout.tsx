import { Outlet, NavLink, useNavigate, useLocation } from 'react-router-dom';
import { useSelector, useDispatch } from 'react-redux';
import {
  LayoutDashboard,
  ArrowLeftRight,
  Upload,
  LogOut,
  Moon,
  Sun,
  Menu,
} from 'lucide-react';
import { useState, useMemo } from 'react';
import { logout as logoutAction } from '@/store/auth-slice';
import { tokenStorage } from '@/lib/token-storage';
import type { RootState } from '@/store';
import { Button } from '@/components/ui/button';
import { Avatar, AvatarFallback } from '@/components/ui/avatar';
import { Peek } from '@doan-labs/peek';
import { cn } from 'cn';

const navItems = [
  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/transactions', label: 'Transactions', icon: ArrowLeftRight },
  { to: '/upload', label: 'Import', icon: Upload },
];

/** Map route paths to page titles for the top bar */
const pageTitles: Record<string, string> = {
  '/dashboard': 'Dashboard',
  '/transactions': 'Transactions',
  '/upload': 'Import',
};

export function AppLayout() {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [dark, setDark] = useState(() => document.documentElement.classList.contains('dark'));
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const location = useLocation();
  const { id, email } = useSelector((state: RootState) => state.auth);

  const pageTitle = useMemo(() => {
    // Match the current path to a title
    for (const [path, title] of Object.entries(pageTitles)) {
      if (location.pathname.startsWith(path)) return title;
    }
    return '';
  }, [location.pathname]);

  const toggleDark = () => {
    const root = document.documentElement;
    const isDark = root.classList.contains('dark');
    root.classList.toggle('dark');
    setDark(!isDark);
    localStorage.setItem('ft_dark', (!isDark).toString());
  };

  const handleLogout = () => {
    tokenStorage.clear();
    dispatch(logoutAction());
    navigate('/login');
  };

  const initials = email?.charAt(0).toUpperCase() || '?';

  return (
    <div className="flex h-screen overflow-hidden bg-bg-canvas">
      {/* Mobile overlay */}
      {sidebarOpen && (
        <div
          className="fixed inset-0 z-40 bg-black/45 lg:hidden"
          onClick={() => setSidebarOpen(false)}
        />
      )}

      {/* Sidebar — 240px, bg-sidebar */}
      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-50 flex w-[240px] flex-col border-r border-sidebar-border bg-sidebar transition-transform lg:static lg:translate-x-0',
          sidebarOpen ? 'translate-x-0' : '-translate-x-full'
        )}
      >
        {/* Brand header */}
        <div className="flex h-16 items-center gap-3 border-b border-sidebar-border px-5">
          {/* Brand mark (plan/design/brand). The green tile is part of the SVG; alt="" because the name sits next to it. */}
          <img src="/mark.svg" alt="" className="size-9 shrink-0" />
          <div>
            <span className="text-base font-semibold tracking-tight text-sidebar-foreground">Finance Tracker</span>
            <p className="-mt-0.5 text-[11px] text-text-muted">Personal Finance</p>
          </div>
        </div>

        {/* Navigation */}
        <nav className="flex-1 space-y-1 p-4">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              onClick={() => setSidebarOpen(false)}
              className={({ isActive }) =>
                cn(
                  'nav-item',
                  isActive
                    ? 'bg-brand-primary-soft text-brand-primary'
                    : 'text-text-muted hover:bg-bg-muted hover:text-text-default'
                )
              }
            >
              <item.icon className="size-[18px]" />
              {item.label}
            </NavLink>
          ))}
        </nav>

        {/* Bottom section */}
        <div className="border-t border-sidebar-border p-3">
          <div className="rounded-lg bg-bg-muted p-3">
            <p className="text-[11px] text-text-muted">Tracked this month</p>
            <p className="text-sm font-semibold text-text-default">—</p>
          </div>
        </div>
      </aside>

      {/* Main area */}
      <div className="flex flex-1 flex-col overflow-hidden">
        {/* Top bar — 64px, bg-canvas, page title on left */}
        <header className="flex h-16 shrink-0 items-center justify-between border-b border-border-default bg-bg-canvas px-8">
          <div className="flex items-center gap-3">
            <Button
              variant="ghost"
              size="icon"
              onClick={() => setSidebarOpen(true)}
              className="lg:hidden text-text-muted"
            >
              <Menu className="size-5" />
            </Button>
            <h1 className="page-title">{pageTitle}</h1>
          </div>

          <div className="flex items-center gap-3">
            {/* Dark mode toggle */}
            <Button
              variant="ghost"
              size="icon-sm"
              onClick={toggleDark}
              title="Toggle theme"
              className="text-text-muted hover:text-text-default"
            >
              {dark ? <Sun className="size-4" /> : <Moon className="size-4" />}
            </Button>

            {/* User info */}
            <div className="flex items-center gap-2 rounded-lg bg-bg-muted px-3 py-1.5">
              {id ? (
                // Deterministic avatar seeded by the user id, not the email, so changing email keeps the same face.
                // Version pinned so library updates don't redraw it.
                <Peek
                  name={id}
                  size={28}
                  square={false}
                  frame={dark ? 'ink' : 'paper'}
                  version={1}
                  title={false}
                  className="shrink-0"
                />
              ) : (
                <Avatar className="size-7">
                  <AvatarFallback className="bg-brand-primary-soft text-xs font-medium text-brand-primary">
                    {initials}
                  </AvatarFallback>
                </Avatar>
              )}
              <span className="hidden text-sm text-text-muted sm:inline">{email}</span>
            </div>

            {/* Logout */}
            <Button
              variant="ghost"
              size="icon-sm"
              onClick={handleLogout}
              title="Logout"
              className="text-text-muted hover:text-status-danger"
            >
              <LogOut className="size-4" />
            </Button>
          </div>
        </header>

        {/* Content */}
        <main className="flex-1 overflow-y-auto px-8 py-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
