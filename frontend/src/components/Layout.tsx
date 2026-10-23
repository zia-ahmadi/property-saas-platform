import React, { useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import {
  Building2,
  FileText,
  LayoutDashboard,
  LogOut,
  Menu,
  ReceiptText,
  ShieldCheck,
  Wrench,
  X,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';

const navigation = [
  { to: '/', label: 'Overview', icon: LayoutDashboard },
  { to: '/properties', label: 'Properties', icon: Building2, manager: true },
  { to: '/leases', label: 'Leases', icon: FileText, manager: true },
  { to: '/billing', label: 'Billing', icon: ReceiptText, manager: true },
  { to: '/maintenance', label: 'Maintenance', icon: Wrench, manager: true },
];

const routeTitles: Record<string, string> = {
  '/': 'Portfolio overview',
  '/properties': 'Properties',
  '/leases': 'Leases',
  '/billing': 'Billing & payments',
  '/maintenance': 'Maintenance',
  '/platform-admin': 'Platform administration',
};

export const Layout: React.FC = () => {
  const { user, logout, isManager, isAdmin, isPlatformAdmin } = useAuth();
  const [menuOpen, setMenuOpen] = useState(false);
  const location = useLocation();
  const initials = user?.fullName
    ?.split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0])
    .join('') || 'U';
  const roleLabel = isPlatformAdmin
    ? 'Platform administrator'
    : isAdmin
      ? 'Organization administrator'
      : isManager
        ? 'Property manager'
        : 'Tenant';

  const visibleNavigation = navigation.filter((item) => !item.manager || isManager);

  const renderNavLink = (item: (typeof navigation)[number] & { label: string; to: string; icon: typeof LayoutDashboard }) => {
    const Icon = item.icon;
    return (
      <NavLink
        key={item.to}
        to={item.to}
        end={item.to === '/'}
        onClick={() => setMenuOpen(false)}
        className={({ isActive }) => `sidebar-link${isActive ? ' active' : ''}`}
      >
        <Icon size={17} strokeWidth={1.8} />
        <span>{item.label}</span>
      </NavLink>
    );
  };

  return (
    <div className="app-shell">
      <button
        type="button"
        className={`mobile-overlay${menuOpen ? ' visible' : ''}`}
        aria-label="Close navigation"
        onClick={() => setMenuOpen(false)}
      />

      <aside className={`sidebar${menuOpen ? ' open' : ''}`} aria-label="Main navigation">
        <NavLink to="/" className="brand" onClick={() => setMenuOpen(false)}>
          <span className="brand-mark"><Building2 size={20} strokeWidth={1.8} /></span>
          <span>
            <span className="brand-name">Northstar</span>
            <span className="brand-caption">Property workspace</span>
          </span>
        </NavLink>

        <p className="nav-section-label">Workspace</p>
        <nav className="sidebar-nav" aria-label="Workspace">
          {visibleNavigation.map(renderNavLink)}
        </nav>

        {isPlatformAdmin && (
          <>
            <p className="nav-section-label" style={{ marginTop: 25 }}>System</p>
            <nav className="sidebar-nav" aria-label="System">
              {renderNavLink({
                to: '/platform-admin',
                label: 'Platform admin',
                icon: ShieldCheck,
              })}
            </nav>
          </>
        )}

        <div className="sidebar-bottom">
          <div className="workspace-card">
            <p className="workspace-label">Secure workspace</p>
            <div className="workspace-value"><span className="workspace-dot" />Organization data isolated</div>
          </div>
          <div className="account-card">
            <div className="avatar" aria-hidden="true">{initials}</div>
            <div className="account-copy">
              <p className="account-name">{user?.fullName || 'Account'}</p>
              <p className="account-role">{roleLabel}</p>
            </div>
            <button type="button" className="icon-button" onClick={logout} aria-label="Sign out" title="Sign out">
              <LogOut size={16} />
            </button>
          </div>
        </div>
      </aside>

      <div className="app-main">
        <header className="topbar">
          <div className="topbar-left">
            <button
              type="button"
              className="icon-button menu-toggle"
              aria-label={menuOpen ? 'Close menu' : 'Open menu'}
              aria-expanded={menuOpen}
              onClick={() => setMenuOpen((open) => !open)}
            >
              {menuOpen ? <X size={19} /> : <Menu size={19} />}
            </button>
            <div>
              <p className="topbar-eyebrow">Property operations</p>
              <h1 className="topbar-title">{routeTitles[location.pathname] || 'Property workspace'}</h1>
            </div>
          </div>
          <div className="topbar-right">
            <span className="topbar-divider" />
            <span className="topbar-org">Workspace</span>
            <div className="avatar" aria-hidden="true">{initials}</div>
          </div>
        </header>

        <main className="content-area">
          <Outlet />
        </main>
      </div>
    </div>
  );
};
