import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { authApi } from '../services/api';
import { Building2, Check, Lock, Mail } from 'lucide-react';

export const AuthPage: React.FC = () => {
  const [isLogin, setIsLogin] = useState(true);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fullName, setFullName] = useState('');
  const [organizationName, setOrganizationName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const { login } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);

    try {
      const res = isLogin
        ? await authApi.login({ email, password })
        : await authApi.register({ organizationName, email, password, fullName });
      await login(res.accessToken);
      navigate('/');
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Authentication failed');
    } finally {
      setLoading(false);
    }
  };

  const toggleMode = (loginMode: boolean) => {
    setIsLogin(loginMode);
    setError(null);
  };

  const brand = (
    <div className="brand">
      <span className="brand-mark"><Building2 size={20} strokeWidth={1.8} /></span>
      <span>
        <span className="brand-name">Northstar</span>
        <span className="brand-caption">Property workspace</span>
      </span>
    </div>
  );

  return (
    <div className="auth-shell">
      <aside className="auth-intro">
        {brand}
        <div className="auth-intro-copy">
          <p className="auth-kicker">Property operations, made clear</p>
          <h1 className="auth-headline">Bring your portfolio into focus.</h1>
          <p className="auth-description">
            One thoughtfully organized workspace for properties, leases, billing, and maintenance.
          </p>
          <div className="auth-benefits">
            <span className="auth-benefit"><Check size={15} /> Keep property operations in one place</span>
            <span className="auth-benefit"><Check size={15} /> See the details that need your attention</span>
            <span className="auth-benefit"><Check size={15} /> Give your team a clearer daily workflow</span>
          </div>
        </div>
        <p className="auth-footnote">A secure workspace for your property team.</p>
      </aside>

      <main className="auth-panel">
        <div className="auth-form-wrap">
          <div className="auth-mobile-brand">{brand}</div>
          <p className="page-eyebrow">{isLogin ? 'Welcome back' : 'Get started'}</p>
          <h2 className="auth-heading">{isLogin ? 'Sign in to your workspace' : 'Create your workspace'}</h2>
          <p className="auth-subheading">
            {isLogin
              ? 'Enter your account details to continue.'
              : 'Set up your organization and invite your team later.'}
          </p>

          <div className="auth-switch" role="tablist" aria-label="Authentication mode">
            <button
              type="button"
              role="tab"
              aria-selected={isLogin}
              className={isLogin ? 'active' : ''}
              onClick={() => toggleMode(true)}
            >
              Sign in
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={!isLogin}
              className={!isLogin ? 'active' : ''}
              onClick={() => toggleMode(false)}
            >
              Create account
            </button>
          </div>

          {error && <div className="auth-error" role="alert">{error}</div>}

          <form onSubmit={handleSubmit} className="auth-form">
            {!isLogin && (
              <>
                <div className="auth-field">
                  <label htmlFor="organization-name">Organization name</label>
                  <input
                    id="organization-name"
                    type="text"
                    autoComplete="organization"
                    required
                    value={organizationName}
                    onChange={(e) => setOrganizationName(e.target.value)}
                    placeholder="Your property company"
                  />
                </div>
                <div className="auth-field">
                  <label htmlFor="full-name">Your name</label>
                  <input
                    id="full-name"
                    type="text"
                    autoComplete="name"
                    required
                    value={fullName}
                    onChange={(e) => setFullName(e.target.value)}
                    placeholder="Full name"
                  />
                </div>
                <div className="auth-admin-note">
                  Your account will be the administrator for this organization.
                </div>
              </>
            )}
            <div className="auth-field auth-field-with-icon">
              <label htmlFor="email">Email address</label>
              <span className="auth-input-icon"><Mail size={15} /></span>
              <input
                id="email"
                type="email"
                autoComplete="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="you@company.com"
              />
            </div>
            <div className="auth-field auth-field-with-icon">
              <label htmlFor="password">Password</label>
              <span className="auth-input-icon"><Lock size={15} /></span>
              <input
                id="password"
                type="password"
                autoComplete={isLogin ? 'current-password' : 'new-password'}
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="Enter your password"
              />
            </div>
            <button type="submit" disabled={loading} className="auth-submit">
              {loading ? 'Please wait…' : isLogin ? 'Sign in' : 'Create organization'}
            </button>
          </form>

          <p className="auth-terms">Your organization’s data is protected with tenant-isolated access.</p>
        </div>
      </main>
    </div>
  );
};
