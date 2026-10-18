import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
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
import LoginPage from '@/pages/login-page';
import RegisterPage from '@/pages/register-page';
import DashboardPage from '@/pages/dashboard-page';
import TransactionsPage from '@/pages/transactions-page';
import TransactionDetailPage from '@/pages/transaction-detail-page';
import UploadPage from '@/pages/upload-page';
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
          email: user.email,
          role: user.role,
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
        // email from the token until the next successful call.
        dispatch(setCredentials({ token, refreshToken, email: emailFromToken(token) ?? '', role: '' }));
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
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/transactions" element={<TransactionsPage />} />
            <Route path="/transactions/:id" element={<TransactionDetailPage />} />
            <Route path="/upload" element={<UploadPage />} />
          </Route>

          {/* Default redirect */}
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
        <Toaster />
      </AuthCheck>
    </BrowserRouter>
  );
}
