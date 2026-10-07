import { useState } from 'react';
import { useNavigate, Link, Navigate } from 'react-router-dom';
import { useDispatch, useSelector } from 'react-redux';
import { useLoginMutation } from '@/store/api/auth-api';
import { setCredentials } from '@/store/auth-slice';
import { tokenStorage } from '@/lib/token-storage';
import type { RootState } from '@/store';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardFooter } from '@/components/ui/card';

export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [login, { isLoading }] = useLoginMutation();
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const isAuthenticated = useSelector((s: RootState) => s.auth.isAuthenticated);

  if (isAuthenticated) return <Navigate to="/dashboard" replace />;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    try {
      const data = await login({ email, password }).unwrap();
      tokenStorage.setToken(data.token);
      tokenStorage.setRefreshToken(data.refreshToken);
      dispatch(setCredentials(data));
      navigate('/dashboard', { replace: true });
    } catch (err: any) {
      setError(err?.data?.detail || err?.data?.message || err?.data?.error?.message || 'Login failed');
    }
  };

  return (
    <Card className="border-border/50 bg-card/80 shadow-xl shadow-black/10 backdrop-blur-sm">
      <div className="px-6 pt-6">
        <h2 className="text-xl font-semibold tracking-tight text-foreground">Welcome back</h2>
        <p className="mt-1 text-sm text-muted-foreground">Sign in to your account</p>
      </div>
      <form onSubmit={handleSubmit}>
        <CardContent className="space-y-4 pt-6">
          {error && (
            <div className="rounded-lg border border-destructive/20 bg-destructive/10 p-3 text-sm text-destructive">
              {error}
            </div>
          )}
          <div className="space-y-2">
            <Label htmlFor="email" className="text-xs font-medium text-muted-foreground">Email</Label>
            <Input
              id="email"
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@example.com"
              className="h-10 border-border bg-background/50 text-foreground placeholder:text-muted-foreground/50 focus:border-emerald-500/50 focus:ring-emerald-500/20"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="password" className="text-xs font-medium text-muted-foreground">Password</Label>
            <Input
              id="password"
              type="password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              className="h-10 border-border bg-background/50 text-foreground placeholder:text-muted-foreground/50 focus:border-emerald-500/50 focus:ring-emerald-500/20"
            />
          </div>
        </CardContent>
        <CardFooter className="flex flex-col gap-4">
          <Button
            type="submit"
            className="w-full h-10 bg-gradient-to-r from-emerald-500 to-emerald-600 text-white font-medium shadow-lg shadow-emerald-500/20 hover:from-emerald-400 hover:to-emerald-500 hover:shadow-emerald-500/30 transition-all duration-200"
            disabled={isLoading}
          >
            {isLoading ? 'Signing in...' : 'Sign In'}
          </Button>
          <p className="text-sm text-muted-foreground">
            Don't have an account?{' '}
            <Link to="/register" className="font-medium text-emerald-400 hover:text-emerald-300 transition-colors">
              Create one
            </Link>
          </p>
        </CardFooter>
      </form>
    </Card>
  );
}
