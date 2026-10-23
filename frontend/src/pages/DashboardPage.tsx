import React, { useEffect, useState } from 'react';
import { graphqlApi, type PortfolioDashboard } from '../services/api';
import { 
  Building2, 
  Home, 
  DollarSign, 
  AlertTriangle, 
  Clock, 
  TrendingUp
} from 'lucide-react';

export const DashboardPage: React.FC = () => {
  const [dashboard, setDashboard] = useState<PortfolioDashboard | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadDashboard = async () => {
    try {
      setLoading(true);
      const data = await graphqlApi.getDashboard();
      setDashboard(data);
    } catch (err: any) {
      setError(err.message || 'Failed to load dashboard data');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadDashboard();
  }, []);

  if (loading) {
    return (
      <div className="flex items-center justify-center h-96">
        <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-blue-600"></div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-red-50 border border-red-200 text-red-700 p-6 rounded-2xl">
        <h3 className="font-bold text-base mb-1">Failed to load GraphQL Dashboard</h3>
        <p className="text-sm">{error}</p>
        <button
          onClick={loadDashboard}
          className="mt-4 px-4 py-2 bg-red-600 text-white rounded-xl text-xs font-semibold hover:bg-red-500"
        >
          Retry
        </button>
      </div>
    );
  }

  if (!dashboard) return null;

  const occupancyPercent = Math.round(dashboard.occupancyRate * 100);

  return (
    <div className="space-y-6">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Portfolio Dashboard</h1>
          <p className="text-sm text-slate-500">Real-time aggregated metrics via GraphQL</p>
        </div>
        <div className="flex items-center space-x-2 text-xs font-medium bg-white px-4 py-2 rounded-xl border border-slate-200 shadow-xs">
          <Clock className="w-4 h-4 text-slate-400" />
          <span>Last computed: {new Date(dashboard.generatedAt).toLocaleTimeString()}</span>
        </div>
      </div>

      {/* Metric Cards Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
        <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-xs flex items-center justify-between">
          <div>
            <p className="text-xs font-bold uppercase tracking-wider text-slate-400">Properties</p>
            <h3 className="text-3xl font-ext500 font-bold text-slate-900 mt-1">{dashboard.totalProperties}</h3>
            <span className="text-xs text-blue-600 font-medium mt-2 inline-flex items-center space-x-1">
              <Building2 className="w-3.5 h-3.5 mr-1" /> Managed portfolios
            </span>
          </div>
          <div className="w-12 h-12 bg-blue-50 text-blue-600 rounded-2xl flex items-center justify-center">
            <Building2 className="w-6 h-6" />
          </div>
        </div>

        <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-xs flex items-center justify-between">
          <div>
            <p className="text-xs font-bold uppercase tracking-wider text-slate-400">Total Units</p>
            <h3 className="text-3xl font-bold text-slate-900 mt-1">{dashboard.totalUnits}</h3>
            <span className="text-xs text-green-600 font-medium mt-2 inline-flex items-center space-x-1">
              <Home className="w-3.5 h-3.5 mr-1" /> {dashboard.occupiedUnits} Occupied
            </span>
          </div>
          <div className="w-12 h-12 bg-green-50 text-green-600 rounded-2xl flex items-center justify-center">
            <Home className="w-6 h-6" />
          </div>
        </div>

        <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-xs flex items-center justify-between">
          <div>
            <p className="text-xs font-bold uppercase tracking-wider text-slate-400">Occupancy Rate</p>
            <h3 className="text-3xl font-bold text-slate-900 mt-1">{occupancyPercent}%</h3>
            <div className="w-full bg-slate-100 h-2 rounded-full mt-3 overflow-hidden">
              <div 
                className="bg-blue-600 h-full rounded-full transition-all duration-500" 
                style={{ width: `${occupancyPercent}%` }}
              ></div>
            </div>
          </div>
          <div className="w-12 h-12 bg-indigo-50 text-indigo-600 rounded-2xl flex items-center justify-center">
            <TrendingUp className="w-6 h-6" />
          </div>
        </div>

        <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-xs flex items-center justify-between">
          <div>
            <p className="text-xs font-bold uppercase tracking-wider text-slate-400">Outstanding Balance</p>
            <h3 className="text-3xl font-bold text-slate-900 mt-1">${dashboard.totalOutstandingAmount.toLocaleString()}</h3>
            <span className="text-xs text-amber-600 font-medium mt-2 inline-flex items-center space-x-1">
              <AlertTriangle className="w-3.5 h-3.5 mr-1" /> {dashboard.overdueInvoiceCount} Overdue invoices
            </span>
          </div>
          <div className="w-12 h-12 bg-amber-50 text-amber-600 rounded-2xl flex items-center justify-center">
            <DollarSign className="w-6 h-6" />
          </div>
        </div>
      </div>

      {/* Active Maintenance Requests / Alerts */}
      <div className="bg-white rounded-2xl border border-slate-200 shadow-xs overflow-hidden">
        <div className="p-6 border-b border-slate-100 flex items-center justify-between">
          <div>
            <h3 className="font-bold text-base text-slate-900">Active Maintenance Alerts</h3>
            <p className="text-xs text-slate-500">Unresolved requests requiring vendor assignment or resolution</p>
          </div>
          <span className="bg-amber-100 text-amber-800 text-xs font-bold px-3 py-1 rounded-full">
            {dashboard.activeMaintenanceRequests.length} Active
          </span>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse text-sm">
            <thead>
              <tr className="bg-slate-50 text-slate-400 text-xs uppercase tracking-wider border-b border-slate-200">
                <th className="py-3 px-6 font-semibold">Property / Unit</th>
                <th className="py-3 px-6 font-semibold">Priority</th>
                <th className="py-3 px-6 font-semibold">Status</th>
                <th className="py-3 px-6 font-semibold">Description</th>
                <th className="py-3 px-6 font-semibold">Requested Date</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {dashboard.activeMaintenanceRequests.length === 0 ? (
                <tr>
                  <td colSpan={5} className="py-8 text-center text-slate-400 text-sm">
                    No active maintenance requests. All clear!
                  </td>
                </tr>
              ) : (
                dashboard.activeMaintenanceRequests.map((req) => (
                  <tr key={req.id} className="hover:bg-slate-50/80">
                    <td className="py-4 px-6 font-medium text-slate-900">
                      <div>{req.propertyName}</div>
                      <div className="text-xs text-slate-400 font-normal">Unit {req.unitName}</div>
                    </td>
                    <td className="py-4 px-6">
                      <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-bold ${
                        req.priority === 'EMERGENCY' ? 'bg-red-100 text-red-700' :
                        req.priority === 'HIGH' ? 'bg-orange-100 text-orange-700' :
                        req.priority === 'MEDIUM' ? 'bg-yellow-100 text-yellow-700' :
                        'bg-slate-100 text-slate-700'
                      }`}>
                        {req.priority}
                      </span>
                    </td>
                    <td className="py-4 px-6">
                      <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-blue-50 text-blue-700">
                        {req.status}
                      </span>
                    </td>
                    <td className="py-4 px-6 text-slate-600 max-w-xs truncate">{req.description}</td>
                    <td className="py-4 px-6 text-slate-500 text-xs font-mono">{new Date(req.requestedDate).toLocaleDateString()}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
