import { Outlet } from 'react-router-dom';

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
          {/* Brand mark (plan/design/brand): no shadow or wrapper, per the logo rules. */}
          <img src="/mark.svg" alt="" className="mx-auto mb-4 size-14" />
          <h1 className="text-[28px] font-semibold leading-9 tracking-tight text-text-default">Finance Tracker</h1>
          <p className="mt-1 text-sm text-text-muted">Track your spending effortlessly</p>
        </div>
        <Outlet />
      </div>
    </div>
  );
}
