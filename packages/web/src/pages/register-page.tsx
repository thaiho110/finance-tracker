import { useState, useMemo } from 'react';
import { useNavigate, Link, Navigate, useSearchParams } from 'react-router-dom';
import { useDispatch, useSelector } from 'react-redux';
import { useRegisterMutation } from '@/store/api/auth-api';
import { setCredentials } from '@/store/auth-slice';
import { tokenStorage } from '@/lib/token-storage';
import type { RootState } from '@/store';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { cn } from 'cn';

type StrengthLevel = 'none' | 'weak' | 'fair' | 'strong' | 'very-strong';

function getStrength(password: string): { level: StrengthLevel; score: number; label: string; color: string } {
  let score = 0;
  if (password.length >= 8) score += 1;
  if (password.length >= 12) score += 1;
  if (/[a-z]/.test(password)) score += 1;
  if (/[A-Z]/.test(password)) score += 1;
  if (/\d/.test(password)) score += 1;
  if (/[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?`~]/.test(password)) score += 1;

  if (score <= 1) return { level: 'weak', score, label: 'Weak', color: 'bg-status-danger' };
  if (score <= 2) return { level: 'fair', score, label: 'Fair', color: 'bg-status-warning' };
  if (score <= 4) return { level: 'strong', score, label: 'Strong', color: 'bg-brand-primary' };
  return { level: 'very-strong', score, label: 'Very Strong', color: 'bg-brand-primary' };
}

export default function RegisterPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<{ email?: string; password?: string; confirm?: string }>({});
  const [register, { isLoading }] = useRegisterMutation();
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const inviteToken = searchParams.get('invite') ?? undefined;
  const isAuthenticated = useSelector((s: RootState) => s.auth.isAuthenticated);

  const strength = useMemo(() => getStrength(password), [password]);
  const passwordsMatch = password === confirm;
  const hasConfirmValue = confirm.length > 0;

  if (isAuthenticated) return <Navigate to="/dashboard" replace />;

  const validate = (): boolean => {
    const errors: { email?: string; password?: string; confirm?: string } = {};
    if (!email.trim()) errors.email = 'Email is required';
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) errors.email = 'Please enter a valid email address';
    if (!password) errors.password = 'Password is required';
    else if (password.length < 8) errors.password = 'Password must be at least 8 characters';
    else if (!/[A-Z]/.test(password)) errors.password = 'Password must contain at least one uppercase letter';
    else if (!/[a-z]/.test(password)) errors.password = 'Password must contain at least one lowercase letter';
    else if (!/\d/.test(password)) errors.password = 'Password must contain at least one digit';
    else if (!/[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?`~]/.test(password)) errors.password = 'Password must contain at least one special character';
    if (!confirm) errors.confirm = 'Please confirm your password';
    else if (password !== confirm) errors.confirm = 'Passwords do not match';
    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setFieldErrors({});
    if (!validate()) return;

    try {
      const data = await register({ email, password, inviteToken }).unwrap();
      tokenStorage.setTokens(data.token, data.refreshToken);
      dispatch(setCredentials(data));
      toast.success('Account created successfully!');
      navigate('/dashboard', { replace: true });
    } catch (err: any) {
      setError(err?.data?.detail || err?.data?.message || err?.error || 'Registration failed. Please try again.');
    }
  };

  const clearFieldError = (field: 'email' | 'password' | 'confirm') => {
    setFieldErrors((prev) => ({ ...prev, [field]: undefined }));
  };

  return (
    <div className="surface-card p-6">
      <div className="mb-6">
        <h2 className="text-xl font-semibold leading-7 text-text-default">Get started</h2>
        <p className="mt-1 text-sm text-text-muted">Create your account</p>
      </div>

      <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-5">
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
            onChange={(e) => { setEmail(e.target.value); clearFieldError('email'); }}
            placeholder="you@example.com"
            className={cn(fieldErrors.email && 'aria-invalid:error-ring border-status-danger')}
          />
          {fieldErrors.email && <p className="text-xs text-status-danger">{fieldErrors.email}</p>}
        </div>

        {/* Password */}
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="password" className="field-label">Password</Label>
          <Input
            id="password" type="password" required
            value={password}
            onChange={(e) => { setPassword(e.target.value); clearFieldError('password'); }}
            placeholder="Min. 8 characters"
            className={cn(fieldErrors.password && 'aria-invalid:error-ring border-status-danger')}
          />
          {password.length > 0 && (
            <div className="space-y-1">
              <div className="flex gap-1">
                {[1, 2, 3, 4, 5].map((bar) => (
                  <div key={bar} className={cn('h-1 flex-1 rounded-full transition-all duration-300', bar <= strength.score ? strength.color : 'bg-bg-muted')} />
                ))}
              </div>
              <p className="text-[11px] text-text-muted">
                {strength.label}
                {strength.level !== 'very-strong' && (
                  <span className="ml-1 text-text-muted/60">— use uppercase, lowercase, number &amp; special character</span>
                )}
              </p>
            </div>
          )}
          {fieldErrors.password && <p className="text-xs text-status-danger">{fieldErrors.password}</p>}
        </div>

        {/* Confirm Password */}
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="confirm" className="field-label">Confirm Password</Label>
          <Input
            id="confirm" type="password" required
            value={confirm}
            onChange={(e) => { setConfirm(e.target.value); clearFieldError('confirm'); }}
            placeholder="Repeat password"
            className={cn((fieldErrors.confirm || (hasConfirmValue && !passwordsMatch)) && 'aria-invalid:error-ring border-status-danger')}
          />
          {hasConfirmValue && !passwordsMatch && <p className="text-xs text-status-danger">Passwords do not match</p>}
          {fieldErrors.confirm && !hasConfirmValue && <p className="text-xs text-status-danger">{fieldErrors.confirm}</p>}
        </div>

        <Button type="submit" className="w-full h-10" disabled={isLoading}>
          {isLoading ? 'Creating account…' : 'Create Account'}
        </Button>

        <p className="text-center text-sm text-text-muted">
          Already have an account?{' '}
          <Link to="/login" className="font-medium text-brand-primary hover:underline">Sign In</Link>
        </p>
      </form>
    </div>
  );
}
