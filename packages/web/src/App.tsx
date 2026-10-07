import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useEffect } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { setCredentials, setLoading } from '@/store/auth-slice';
import { tokenStorage } from '@/lib/token-storage';
import type { RootState } from '@/store';
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
  const dispatch = useDispatch();
  const isAuthenticated = useSelector((s: RootState) => s.auth.isAuthenticated);

  useEffect(() => {
    const token = tokenStorage.getToken();
    const refreshToken = tokenStorage.getRefreshToken();

    // Default to dark mode; respect saved preference
    const darkPref = localStorage.getItem('ft_dark');
    if (darkPref === 'false') {
      document.documentElement.classList.remove('dark');
      document.documentElement.classList.add('light');
    } else {
      document.documentElement.classList.add('dark');
    }

    if (token && refreshToken) {
      // Try to restore session from stored tokens
      // The backend will validate on the first API call
      dispatch(setCredentials({
        token,
        refreshToken,
        email: '', // Will be populated from API response
        role: 'user',
      }));
    } else {
      dispatch(setLoading(false));
    }
  }, [dispatch]);

  // If authenticated and email is empty (restored from storage), try to get user info
  // For now, just mark as loaded
  useEffect(() => {
    if (isAuthenticated) {
      dispatch(setLoading(false));
    }
  }, [isAuthenticated, dispatch]);

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
