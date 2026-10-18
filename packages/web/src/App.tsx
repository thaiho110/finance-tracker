import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { useEffect } from 'react';
import { useDispatch } from 'react-redux';
import { logout, setCredentials, setLoading } from '@/store/auth-slice';
import { authApi } from '@/store/api/auth-api';
import { tokenStorage } from '@/lib/token-storage';
import { emailFromToken } from '@/lib/jwt';
import type { AppDispatch } from '@/store';
import { AppLayout } from '@/layouts/app-layout';
import { AuthLayout } from '@/layouts/auth-layout';
import { ProtectedRoute } from '@/components/guards/auth-guard';
import { HomeRedirect, RequirePermission } from '@/components/guards/require-permission';
import LoginPage from '@/pages/login-page';
import RegisterPage from '@/pages/register-page';
import DashboardPage from '@/pages/dashboard-page';
import TransactionsPage from '@/pages/transactions-page';
import TransactionDetailPage from '@/pages/transaction-detail-page';
import UploadPage from '@/pages/upload-page';
import AdminUsersPage from '@/pages/admin/users-page';
import AdminRolesPage from '@/pages/admin/roles-page';
import AdminAuditPage from '@/pages/admin/audit-page';
import { Toaster } from '@/components/ui/sonner';

function AuthCheck({ children }: { children: React.ReactNode }) {
  const dispatch = useDispatch<AppDispatch>();

  useEffect(() => {
    const token = tokenStorage.getToken();
    const refreshToken = tokenStorage.getRefreshToken();

    // Light mode is the default (the design is light-first); dark only when the user chose it
    let darkPref: string | null = null;
    try { darkPref = localStorage.getItem('ft_dark'); } catch { /* storage unavailable */ }
    document.documentElement.classList.toggle('dark', darkPref === 'true');
    document.documentElement.classList.remove('light');

    if (!token || !refreshToken) {
      dispatch(setLoading(false));
      return;
    }

    // Redux state lives in memory, so after a reload only the tokens survive (in localStorage).
    // Ask the server who they belong to before rendering protected pages (ProtectedRoute shows a
    // spinner while isLoading). base-query refreshes an expired access token on the way.
    dispatch(authApi.endpoints.me.initiate(undefined, { subscribe: false, forceRefetch: true }))
      .unwrap()
      .then((user) => {
        dispatch(setCredentials({
          token: tokenStorage.getToken() ?? token,
          refreshToken: tokenStorage.getRefreshToken() ?? refreshToken,
          id: user.id,
          email: user.email,
          role: user.role,
          permissions: user.permissions,
        }));
      })
      .catch((err: { status?: number | string }) => {
        if (err?.status === 401) {
          // Session expired and could not be refreshed: sign out cleanly.
          tokenStorage.clear();
          dispatch(logout());
          return;
        }
        // Server unreachable: keep the session (the token may still be valid) and show the
        // email from the token until the next successful call. The id isn't in the token, so the
        // avatar shows initials until then.
        dispatch(setCredentials({ token, refreshToken, id: null, email: emailFromToken(token) ?? '', role: '' }));
      });
  }, [dispatch]);

  return <>{children}</>;
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthCheck>
        <Routes>
          {/* Public routes */}
          <Route element={<AuthLayout />}>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
          </Route>

          {/* Protected routes */}
          <Route
            element={
              <ProtectedRoute>
                <AppLayout />
              </ProtectedRoute>
            }
          >
            <Route path="/dashboard" element={<RequirePermission permission="DASHBOARD_VIEW"><DashboardPage /></RequirePermission>} />
            <Route path="/transactions" element={<RequirePermission permission="TRANSACTION_READ"><TransactionsPage /></RequirePermission>} />
            <Route
              path="/transactions/:id"
              element={<RequirePermission permission="TRANSACTION_READ"><TransactionDetailPage /></RequirePermission>}
            />
            {/* The import page checks CSV_IMPORT and RECEIPT_SCAN per tab */}
            <Route path="/upload" element={<UploadPage />} />

            {/* Administration (ADR-018) */}
            <Route path="/admin/users" element={<RequirePermission permission="USER_READ"><AdminUsersPage /></RequirePermission>} />
            <Route path="/admin/roles" element={<RequirePermission permission="USER_READ"><AdminRolesPage /></RequirePermission>} />
            <Route path="/admin/audit" element={<RequirePermission permission="AUDIT_READ"><AdminAuditPage /></RequirePermission>} />
          </Route>

          {/* Default redirect */}
          <Route path="/" element={<HomeRedirect />} />
          <Route path="*" element={<HomeRedirect />} />
        </Routes>
        <Toaster />
      </AuthCheck>
    </BrowserRouter>
  );
}
