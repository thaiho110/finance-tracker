import { Outlet } from 'react-router-dom';
import { Wallet } from 'lucide-react';

export function AuthLayout() {
  return (
    <div className="relative flex min-h-screen items-center justify-center bg-bg-canvas p-4">
      {/* Background decoration */}
      <div className="pointer-events-none absolute inset-0 overflow-hidden">
        <div className="absolute -left-40 -top-40 h-80 w-80 rounded-full bg-brand-primary/5 blur-3xl" />
        <div className="absolute -bottom-40 -right-40 h-80 w-80 rounded-full bg-brand-primary/5 blur-3xl" />
      </div>

      <div className="relative w-full max-w-[400px]">
        {/* Brand */}
        <div className="mb-8 text-center">
          <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-brand-primary shadow-lg shadow-brand-primary/20">
            <Wallet className="h-7 w-7 text-text-on-primary" />
          </div>
          <h1 className="text-[28px] font-semibold leading-9 tracking-tight text-text-default">Finance Tracker</h1>
          <p className="mt-1 text-sm text-text-muted">Track your spending effortlessly</p>
        </div>
        <Outlet />
      </div>
    </div>
  );
}
