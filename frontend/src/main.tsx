import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import { Layout } from './components/Layout';
import { AuthPage } from './pages/AuthPage';
import { DashboardPage } from './pages/DashboardPage';
import { PropertiesPage } from './pages/PropertiesPage';
import { LeasesPage } from './pages/LeasesPage';
import { BillingPage } from './pages/BillingPage';
import { MaintenancePage } from './pages/MaintenancePage';
import './index.css';

interface ProtectedRouteProps {
  children: React.ReactNode;
  requiredRole?: 'ADMIN' | 'MANAGER' | 'TENANT';
}

const ProtectedRoute: React.FC<ProtectedRouteProps> = ({ children, requiredRole }) => {
  const { token, isLoading, hasRole, isAdmin, isManager } = useAuth();
  
  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-50">
        <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-blue-600"></div>
      </div>
    );
  }

  if (!token) {
    return <Navigate to="/auth" />;
  }

  if (requiredRole) {
    const authorized = 
      (requiredRole === 'ADMIN' && isAdmin) ||
      (requiredRole === 'MANAGER' && isManager) ||
      (requiredRole === 'TENANT' && hasRole('TENANT'));

    if (!authorized) {
      return <Navigate to="/" />;
    }
  }

  return <>{children}</>;
};

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/auth" element={<AuthPage />} />
          <Route path="/" element={<ProtectedRoute><Layout /></ProtectedRoute>}>
            <Route index element={<DashboardPage />} />
            <Route path="properties" element={<ProtectedRoute requiredRole="MANAGER"><PropertiesPage /></ProtectedRoute>} />
            <Route path="leases" element={<ProtectedRoute requiredRole="MANAGER"><LeasesPage /></ProtectedRoute>} />
            <Route path="billing" element={<ProtectedRoute requiredRole="MANAGER"><BillingPage /></ProtectedRoute>} />
            <Route path="maintenance" element={<ProtectedRoute requiredRole="MANAGER"><MaintenancePage /></ProtectedRoute>} />
          </Route>
          <Route path="*" element={<Navigate to="/" />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  </React.StrictMode>
);
