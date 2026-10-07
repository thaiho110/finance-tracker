import { Outlet, NavLink, useNavigate } from 'react-router-dom';
import { useSelector, useDispatch } from 'react-redux';
import {
  LayoutDashboard,
  ArrowLeftRight,
  Upload,
  LogOut,
  Moon,
  Sun,
  Menu,
  X,
  Wallet,
} from 'lucide-react';
import { useState } from 'react';
import { logout as logoutAction } from '@/store/auth-slice';
import { tokenStorage } from '@/lib/token-storage';
import type { RootState } from '@/store';
import { Button } from '@/components/ui/button';
import { Avatar, AvatarFallback } from '@/components/ui/avatar';
import { cn } from 'cn';

const navItems = [
  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/transactions', label: 'Transactions', icon: ArrowLeftRight },
  { to: '/upload', label: 'Import', icon: Upload },
];

export function AppLayout() {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [dark, setDark] = useState(() => document.documentElement.classList.contains('dark'));
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const { email } = useSelector((state: RootState) => state.auth);

  const toggleDark = () => {
    const root = document.documentElement;
    const isDark = root.classList.contains('dark');
    root.classList.toggle('dark');
    root.classList.toggle('light');
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
    <div className="flex h-screen overflow-hidden bg-background">
      {/* Mobile overlay */}
      {sidebarOpen && (
        <div
          className="fixed inset-0 z-40 bg-black/60 backdrop-blur-sm lg:hidden"
          onClick={() => setSidebarOpen(false)}
        />
      )}

      {/* Sidebar */}
      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-50 flex w-60 flex-col border-r border-sidebar-border bg-sidebar transition-transform lg:static lg:translate-x-0',
          sidebarOpen ? 'translate-x-0' : '-translate-x-full'
        )}
      >
        {/* Brand header */}
        <div className="flex h-16 items-center gap-3 border-b border-sidebar-border px-5">
          <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-br from-emerald-400 to-emerald-600 shadow-lg shadow-emerald-500/20">
            <Wallet className="h-5 w-5 text-white" />
          </div>
          <div>
            <span className="text-base font-semibold tracking-tight text-sidebar-foreground">Finance Tracker</span>
            <p className="-mt-0.5 text-[11px] text-sidebar-foreground/40">Personal Finance</p>
          </div>
        </div>

        {/* Navigation */}
        <nav className="flex-1 space-y-1 p-3 pt-4">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              onClick={() => setSidebarOpen(false)}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-all duration-200',
                  isActive
                    ? 'bg-emerald-500/10 text-emerald-400 shadow-sm'
                    : 'text-sidebar-foreground/60 hover:bg-sidebar-accent hover:text-sidebar-foreground'
                )
              }
            >
              <item.icon className="h-4 w-4" />
              {item.label}
            </NavLink>
          ))}
        </nav>

        {/* Bottom section */}
        <div className="border-t border-sidebar-border p-3">
          <div className="rounded-lg bg-sidebar-accent/50 p-3">
            <p className="text-[11px] text-sidebar-foreground/40">Tracked this month</p>
            <p className="text-sm font-semibold text-sidebar-foreground">—</p>
          </div>
        </div>
      </aside>

      {/* Main area */}
      <div className="flex flex-1 flex-col overflow-hidden">
        {/* Top bar */}
        <header className="flex h-16 items-center justify-between border-b border-border bg-card/50 px-6 backdrop-blur-sm">
          <div className="flex items-center gap-3">
            <Button variant="ghost" size="icon" onClick={() => setSidebarOpen(true)} className="lg:hidden text-muted-foreground">
              <Menu className="h-5 w-5" />
            </Button>
          </div>
          <div className="flex items-center gap-3">
            <Button variant="ghost" size="icon" onClick={toggleDark} title="Toggle theme" className="text-muted-foreground hover:text-foreground">
              {dark ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
            </Button>
            <div className="flex items-center gap-2 rounded-lg bg-muted/50 px-3 py-1.5">
              <Avatar className="h-7 w-7">
                <AvatarFallback className="bg-emerald-500/20 text-xs font-medium text-emerald-400">{initials}</AvatarFallback>
              </Avatar>
              <span className="hidden text-sm text-muted-foreground sm:inline">{email}</span>
            </div>
            <Button variant="ghost" size="icon" onClick={handleLogout} title="Logout" className="text-muted-foreground hover:text-destructive">
              <LogOut className="h-4 w-4" />
            </Button>
          </div>
        </header>

        {/* Content */}
        <main className="flex-1 overflow-y-auto p-6 lg:p-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
