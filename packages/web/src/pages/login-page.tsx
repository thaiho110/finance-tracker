import { useState } from 'react';
import { useNavigate, Link, Navigate } from 'react-router-dom';
import { useDispatch, useSelector } from 'react-redux';
import { useLoginMutation } from '@/store/api/auth-api';
import { setCredentials } from '@/store/auth-slice';
import { tokenStorage } from '@/lib/token-storage';
import type { RootState } from '@/store';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { cn } from 'cn';

export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [fieldError, setFieldError] = useState<'email' | 'password' | null>(null);
  const [login, { isLoading }] = useLoginMutation();
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const isAuthenticated = useSelector((s: RootState) => s.auth.isAuthenticated);

  if (isAuthenticated) return <Navigate to="/dashboard" replace />;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setFieldError(null);

    if (!email.trim()) { setFieldError('email'); setError('Please enter your email address'); return; }
    if (!password) { setFieldError('password'); setError('Please enter your password'); return; }

    try {
      const data = await login({ email, password }).unwrap();
      tokenStorage.setToken(data.token);
      tokenStorage.setRefreshToken(data.refreshToken);
      dispatch(setCredentials(data));
      toast.success('Signed in successfully');
      navigate('/dashboard', { replace: true });
    } catch (err: any) {
      const message = err?.data?.detail || err?.data?.message || err?.error || 'Login failed. Please try again.';
      setError(message);
    }
  };

  return (
    <div className="surface-card p-6">
      <div className="mb-6">
        <h2 className="text-xl font-semibold leading-7 text-text-default">Welcome back</h2>
        <p className="mt-1 text-sm text-text-muted">Sign in to your account</p>
      </div>

      <form onSubmit={handleSubmit} className="flex flex-col gap-5">
        {/* Error banner */}
        {error && (
          <div className="rounded-[8px] border border-status-danger/20 bg-status-danger-soft px-4 py-3 text-sm text-status-danger">
            {error}
          </div>
        )}

        {/* Email */}
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="email" className="field-label">Email</Label>
          <Input
            id="email" type="email" required
            value={email}
            onChange={(e) => { setEmail(e.target.value); setFieldError(null); }}
            placeholder="you@example.com"
            className={cn(fieldError === 'email' && 'aria-invalid:error-ring border-status-danger')}
          />
        </div>

        {/* Password */}
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="password" className="field-label">Password</Label>
          <Input
            id="password" type="password" required
            value={password}
            onChange={(e) => { setPassword(e.target.value); setFieldError(null); }}
            placeholder="••••••••"
            className={cn(fieldError === 'password' && 'aria-invalid:error-ring border-status-danger')}
          />
        </div>

        {/* Submit */}
        <Button type="submit" className="w-full h-10" disabled={isLoading}>
          {isLoading ? 'Signing in…' : 'Sign in'}
        </Button>

        <p className="text-center text-sm text-text-muted">
          Don&apos;t have an account?{' '}
          <Link to="/register" className="font-medium text-brand-primary hover:underline">
            Create one
          </Link>
        </p>
      </form>
    </div>
  );
}
