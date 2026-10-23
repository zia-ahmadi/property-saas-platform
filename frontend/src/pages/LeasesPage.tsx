import React, { useEffect, useState } from 'react';
import { leaseApi, propertyApi, type Lease, type Property, type Unit } from '../services/api';
import { Plus, X } from 'lucide-react';

export const LeasesPage: React.FC = () => {
  const [leases, setLeases] = useState<Lease[]>([]);
  const [loading, setLoading] = useState(true);

  const [showModal, setShowModal] = useState(false);
  const [properties, setProperties] = useState<Property[]>([]);
  const [units, setUnits] = useState<Unit[]>([]);
  const [selectedPropertyId, setSelectedPropertyId] = useState('');

  // Form state
  const [unitId, setUnitId] = useState('');
  const [renterId, setRenterId] = useState('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [monthlyRent, setMonthlyRent] = useState(1500);
  const [securityDeposit, setSecurityDeposit] = useState(1500);

  const loadLeases = async () => {
    try {
      setLoading(true);
      const page = await leaseApi.list(0, 50);
      setLeases(page.content);
    } catch (err: any) {
      console.error(err.message || 'Failed to load leases');
    } finally {
      setLoading(false);
    }
  };

  const loadFormDependencies = async () => {
    try {
      const propPage = await propertyApi.list(0, 50);
      setProperties(propPage.content);
      if (propPage.content.length > 0) {
        setSelectedPropertyId(propPage.content[0].id);
        const uList = await propertyApi.listUnits(propPage.content[0].id);
        setUnits(uList);
        if (uList.length > 0) setUnitId(uList[0].id);
      }
    } catch (e) {
      console.error('Failed to load properties for lease modal', e);
    }
  };

  useEffect(() => {
    loadLeases();
  }, []);

  const handlePropertyChange = async (propId: string) => {
    setSelectedPropertyId(propId);
    try {
      const uList = await propertyApi.listUnits(propId);
      setUnits(uList);
      if (uList.length > 0) setUnitId(uList[0].id);
      else setUnitId('');
    } catch (e) {
      setUnits([]);
      setUnitId('');
    }
  };

  const handleCreateLease = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await leaseApi.create({
        unitId,
        renterId: renterId || '00000000-0000-0000-0000-000000000001', // Default renter UUID for demo
        startDate,
        endDate,
        monthlyRent,
        securityDeposit,
      });
      setShowModal(false);
      setRenterId('');
      setStartDate('');
      setEndDate('');
      loadLeases();
    } catch (err: any) {
      alert(err.message || 'Failed to create lease');
    }
  };

  const handleActivate = async (lease: Lease) => {
    try {
      await leaseApi.activate(lease.id, lease.version);
      loadLeases();
    } catch (err: any) {
      alert(err.message || 'Activation failed (Optimistic locking version mismatch?)');
    }
  };

  const handleTerminate = async (lease: Lease) => {
    try {
      await leaseApi.terminate(lease.id, lease.version);
      loadLeases();
    } catch (err: any) {
      alert(err.message || 'Termination failed');
    }
  };

  if (loading && leases.length === 0) {
    return (
      <div className="flex items-center justify-center h-96">
        <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-blue-600"></div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-950 tracking-tight">Lease Management</h1>
          <p className="text-sm text-slate-600">Lifecycle, renewals, and optimistic locking workflow transitions</p>
        </div>
        <button
          onClick={() => {
            setShowModal(true);
            loadFormDependencies();
          }}
          className="flex items-center justify-center space-x-2 px-4 py-2.5 bg-blue-600 hover:bg-blue-500 text-white rounded-xl font-semibold shadow-lg shadow-blue-600/35 transition-all text-sm"
        >
          <Plus className="w-4 h-4" />
          <span>Create Lease</span>
        </button>
      </div>

      <div className="bg-white rounded-2xl border border-slate-200 shadow-xs overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse text-sm">
            <thead>
              <tr className="bg-slate-50 text-slate-400 text-xs uppercase tracking-wider border-b border-slate-200">
                <th className="py-3 px-6 font-semibold">Lease ID</th>
                <th className="py-3 px-6 font-semibold">Unit ID</th>
                <th className="py-3 px-6 font-semibold">Term</th>
                <th className="py-3 px-6 font-semibold">Monthly Rent</th>
                <th className="py-3 px-6 font-semibold">Status & Version</th>
                <th className="py-3 px-6 font-semibold text-right">Workflow Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {leases.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-12 text-center text-slate-400 text-sm">
                    No leases found. Create your first lease to get started.
                  </td>
                </tr>
              ) : (
                leases.map((lease) => (
                  <tr key={lease.id} className="hover:bg-slate-50/80">
                    <td className="py-4 px-6 font-mono text-xs text-slate-700">{lease.id.substring(0, 8)}...</td>
                    <td className="py-4 px-6 font-mono text-xs text-slate-500">{lease.unitId.substring(0, 8)}...</td>
                    <td className="py-4 px-6 text-slate-600 text-xs">
                      {lease.startDate} to {lease.endDate}
                    </td>
                    <td className="py-4 px-6 font-semibold text-slate-900">${lease.monthlyRent.toLocaleString()}/mo</td>
                    <td className="py-4 px-6">
                      <div className="flex items-center space-x-2">
                        <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-bold ${
                          lease.status === 'ACTIVE' ? 'bg-green-100 text-green-700' :
                          lease.status === 'TERMINATED' ? 'bg-red-100 text-red-700' :
                          lease.status === 'EXPIRED' ? 'bg-slate-100 text-slate-600' :
                          'bg-amber-100 text-amber-700'
                        }`}>
                          {lease.status}
                        </span>
                        <span className="text-[10px] text-slate-400 font-mono">v{lease.version}</span>
                      </div>
                    </td>
                    <td className="py-4 px-6 text-right space-x-2">
                      {lease.status === 'PENDING' && (
                        <button
                          onClick={() => handleActivate(lease)}
                          className="px-3 py-1 bg-green-600 hover:bg-green-500 text-white rounded-lg text-xs font-semibold shadow transition-all"
                        >
                          Activate
                        </button>
                      )}
                      {lease.status === 'ACTIVE' && (
                        <button
                          onClick={() => handleTerminate(lease)}
                          className="px-3 py-1 bg-red-600 hover:bg-red-500 text-white rounded-lg text-xs font-semibold shadow transition-all"
                        >
                          Terminate
                        </button>
                      )}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Create Lease Modal */}
      {showModal && (
        <div className="fixed inset-0 bg-slate-950/50 backdrop-blur-xs flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-2xl max-w-lg w-full p-6 shadow-2xl border border-slate-200">
            <div className="flex items-center justify-between mb-4">
              <h3 className="font-bold text-lg text-slate-900">Create New Lease</h3>
              <button onClick={() => setShowModal(false)} className="text-slate-400 hover:text-slate-600">
                <X className="w-5 h-5" />
              </button>
            </div>
            <form onSubmit={handleCreateLease} className="space-y-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1">Select Property</label>
                  <select
                    value={selectedPropertyId}
                    onChange={(e) => handlePropertyChange(e.target.value)}
                    className="w-full px-3 py-2 border border-slate-300 rounded-xl text-sm focus:outline-none focus:border-blue-600"
                  >
                    {properties.map((p) => (
                      <option key={p.id} value={p.id}>{p.name}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1">Select Unit</label>
                  <select
                    value={unitId}
                    onChange={(e) => setUnitId(e.target.value)}
                    className="w-full px-3 py-2 border border-slate-300 rounded-xl text-sm focus:outline-none focus:border-blue-600"
                  >
                    {units.map((u) => (
                      <option key={u.id} value={u.id}>Unit {u.unitNumber} (${u.rentAmount}/mo)</option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1">Start Date</label>
                  <input
                    type="date"
                    required
                    value={startDate}
                    onChange={(e) => setStartDate(e.target.value)}
                    className="w-full px-3 py-2 border border-slate-300 rounded-xl text-sm focus:outline-none focus:border-blue-600"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1">End Date</label>
                  <input
                    type="date"
                    required
                    value={endDate}
                    onChange={(e) => setEndDate(e.target.value)}
                    className="w-full px-3 py-2 border border-slate-300 rounded-xl text-sm focus:outline-none focus:border-blue-600"
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1">Monthly Rent ($)</label>
                  <input
                    type="number"
                    required
                    min={0}
                    value={monthlyRent}
                    onChange={(e) => setMonthlyRent(parseFloat(e.target.value))}
                    className="w-full px-3 py-2 border border-slate-300 rounded-xl text-sm focus:outline-none focus:border-blue-600"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1">Security Deposit ($)</label>
                  <input
                    type="number"
                    required
                    min={0}
                    value={securityDeposit}
                    onChange={(e) => setSecurityDeposit(parseFloat(e.target.value))}
                    className="w-full px-3 py-2 border border-slate-300 rounded-xl text-sm focus:outline-none focus:border-blue-600"
                  />
                </div>
              </div>

              <div className="flex justify-end space-x-3 pt-2">
                <button
                  type="button"
                  onClick={() => setShowModal(false)}
                  className="px-4 py-2 border border-slate-300 rounded-xl text-xs font-semibold text-slate-700 hover:bg-slate-50"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 bg-blue-600 hover:bg-blue-500 text-white rounded-xl text-xs font-semibold shadow"
                >
                  Create Lease
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
