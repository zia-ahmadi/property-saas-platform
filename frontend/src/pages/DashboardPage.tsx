import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  AlertTriangle,
  ArrowUpRight,
  Building2,
  Clock3,
  DollarSign,
  Home,
  RefreshCw,
  Wrench,
} from 'lucide-react';
import { graphqlApi, type PortfolioDashboard } from '../services/api';

const currency = new Intl.NumberFormat(undefined, {
  style: 'currency',
  currency: 'USD',
  maximumFractionDigits: 0,
});

const dateTime = (value: string) => {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? 'Unavailable'
    : date.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
};

const dateOnly = (value: string) => {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? 'Unavailable'
    : date.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
};

const statusClass = (value: string) => value.toLowerCase().replaceAll('_', '-');

export const DashboardPage: React.FC = () => {
  const [dashboard, setDashboard] = useState<PortfolioDashboard | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadDashboard = async (showLoading = false) => {
    try {
      if (showLoading) {
        setLoading(true);
        setError(null);
      }
      setDashboard(await graphqlApi.getDashboard());
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to load portfolio data.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    let mounted = true;

    void graphqlApi.getDashboard()
      .then((data) => {
        if (mounted) {
          setDashboard(data);
          setLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (mounted) {
          setError(err instanceof Error ? err.message : 'Unable to load portfolio data.');
          setLoading(false);
        }
      });

    return () => {
      mounted = false;
    };
  }, []);

  if (loading && !dashboard) {
    return (
      <div className="dashboard-loading" role="status" aria-label="Loading portfolio overview">
        <span className="loading-spinner" />
        <span>Loading your portfolio…</span>
      </div>
    );
  }

  if (error && !dashboard) {
    return (
      <section className="surface dashboard-error" role="alert">
        <span className="dashboard-error-icon"><AlertTriangle size={20} /></span>
        <div>
          <h2>Portfolio data is unavailable</h2>
          <p>{error}</p>
          <button type="button" className="button button-secondary" onClick={() => void loadDashboard(true)}>
            <RefreshCw size={14} /> Try again
          </button>
        </div>
      </section>
    );
  }

  if (!dashboard) return null;

  const occupancyPercent = Math.max(0, Math.min(100, Math.round(dashboard.occupancyRate * 100)));
  const activeRequests = dashboard.activeMaintenanceRequests;
  const metrics = [
    {
      label: 'Properties',
      value: dashboard.totalProperties.toLocaleString(),
      foot: 'In your portfolio',
      icon: Building2,
      tone: '',
    },
    {
      label: 'Residential units',
      value: dashboard.totalUnits.toLocaleString(),
      foot: `${dashboard.occupiedUnits.toLocaleString()} currently occupied`,
      icon: Home,
      tone: 'green',
    },
    {
      label: 'Outstanding balance',
      value: currency.format(dashboard.totalOutstandingAmount),
      foot: `${dashboard.overdueInvoiceCount.toLocaleString()} overdue invoices`,
      icon: DollarSign,
      tone: dashboard.overdueInvoiceCount > 0 ? 'amber' : 'green',
    },
    {
      label: 'Open maintenance',
      value: activeRequests.length.toLocaleString(),
      foot: 'Requests needing attention',
      icon: Wrench,
      tone: activeRequests.length > 0 ? 'red' : 'green',
    },
  ];

  return (
    <div className="page dashboard-page">
      <section className="page-header">
        <div>
          <p className="page-eyebrow">Portfolio</p>
          <h2 className="page-title">Your portfolio at a glance</h2>
          <p className="page-description">A clear view of occupancy, finances, and day-to-day operations.</p>
        </div>
        <div className="dashboard-updated" title={`Updated ${dateTime(dashboard.generatedAt)}`}>
          <Clock3 size={14} />
          <span>Updated {dateTime(dashboard.generatedAt)}</span>
        </div>
      </section>

      {error && (
        <div className="dashboard-inline-error" role="status">
          Could not refresh the latest data. Showing the last successfully loaded snapshot.
          <button type="button" onClick={() => void loadDashboard(true)}>Retry</button>
        </div>
      )}

      <section className="metrics-grid" aria-label="Portfolio metrics">
        {metrics.map(({ label, value, foot, icon: Icon, tone }) => (
          <article className="metric-card" key={label}>
            <span className={`metric-icon ${tone}`}><Icon size={17} strokeWidth={1.8} /></span>
            <p className="metric-label">{label}</p>
            <p className="metric-value" title={value}>{value}</p>
            <p className="metric-foot">{foot}</p>
          </article>
        ))}
      </section>

      <section className="dashboard-grid">
        <article className="surface">
          <header className="surface-heading">
            <div>
              <h3 className="surface-title">Occupancy</h3>
              <p className="surface-caption">Current portfolio utilization</p>
            </div>
            <Link to="/properties" className="text-link">View properties <ArrowUpRight size={13} /></Link>
          </header>
          <div className="occupancy-content">
            <div
              className="occupancy-ring"
              style={{ '--occupancy': `${occupancyPercent}%` } as React.CSSProperties}
              role="img"
              aria-label={`${occupancyPercent}% of units occupied`}
            >
              <div className="occupancy-ring-inner">
                <span className="occupancy-ring-value">{occupancyPercent}%</span>
                <span className="occupancy-ring-label">occupied</span>
              </div>
            </div>
            <div className="occupancy-copy">
              <h3>Units across your portfolio</h3>
              <p>Occupancy reflects the latest unit status recorded across your properties.</p>
              <div className="occupancy-legend">
                <div className="legend-row">
                  <span className="legend-label"><i className="legend-dot" /> Occupied</span>
                  <span className="legend-value">{dashboard.occupiedUnits.toLocaleString()}</span>
                </div>
                <div className="legend-row">
                  <span className="legend-label"><i className="legend-dot vacant" /> Vacant or unavailable</span>
                  <span className="legend-value">{Math.max(0, dashboard.totalUnits - dashboard.occupiedUnits).toLocaleString()}</span>
                </div>
              </div>
            </div>
          </div>
        </article>

        <article className="surface attention-card">
          <div>
            <span className="attention-icon"><DollarSign size={16} /></span>
            <h3>Receivables to review</h3>
            <p>Outstanding invoices across your properties.</p>
            <div className="attention-value">{currency.format(dashboard.totalOutstandingAmount)}</div>
            <p className="attention-detail">
              {dashboard.overdueInvoiceCount.toLocaleString()} {dashboard.overdueInvoiceCount === 1 ? 'invoice is' : 'invoices are'} overdue
            </p>
          </div>
          <Link to="/billing" className="attention-link">
            Review billing <ArrowUpRight size={15} />
          </Link>
        </article>
      </section>

      <section className="surface">
        <header className="surface-heading">
          <div>
            <h3 className="surface-title">Maintenance requests</h3>
            <p className="surface-caption">Open work orders that may need follow-up</p>
          </div>
          <div className="surface-heading-actions">
            <span className="request-count">{activeRequests.length} open</span>
            <Link to="/maintenance" className="text-link">View all <ArrowUpRight size={13} /></Link>
          </div>
        </header>
        <div className="alerts-table-wrap">
          <table className="alerts-table">
            <thead>
              <tr>
                <th scope="col">Property / unit</th>
                <th scope="col">Issue</th>
                <th scope="col">Priority</th>
                <th scope="col">Status</th>
                <th scope="col">Requested</th>
              </tr>
            </thead>
            <tbody>
              {activeRequests.length === 0 ? (
                <tr>
                  <td colSpan={5} className="empty-state">No open maintenance requests. You’re all caught up.</td>
                </tr>
              ) : activeRequests.slice(0, 6).map((request) => (
                <tr key={request.id}>
                  <td>
                    <span className="table-primary">{request.propertyName}</span>
                    <span className="table-secondary">Unit {request.unitName}</span>
                  </td>
                  <td className="maintenance-description" title={request.description}>{request.description}</td>
                  <td><span className={`status-pill ${statusClass(request.priority)}`}>{request.priority}</span></td>
                  <td><span className={`status-pill ${statusClass(request.status)}`}>{request.status.replaceAll('_', ' ')}</span></td>
                  <td className="requested-date">{dateOnly(request.requestedDate)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
};
