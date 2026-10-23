import React, { useEffect, useState } from 'react';
import { maintenanceApi, vendorApi, propertyApi, renterApi, type MaintenanceRequest, type Vendor, type Property, type Unit, type Renter } from '../services/api';
import { Plus, Users, X } from 'lucide-react';

export const MaintenancePage: React.FC = () => {
  const [requests, setRequests] = useState<MaintenanceRequest[]>([]);
  const [vendors, setVendors] = useState<Vendor[]>([]);
  const [loading, setLoading] = useState(true);

  // Modals state
  const [showRequestModal, setShowRequestModal] = useState(false);
  const [showVendorModal, setShowVendorModal] = useState(false);
  const [showAssignModal, setShowAssignModal] = useState(false);
  const [showResolveModal, setShowResolveModal] = useState(false);
  const [selectedRequest, setSelectedRequest] = useState<MaintenanceRequest | null>(null);
  const [resolveNotes, setResolveNotes] = useState('');

  // Form states for request
  const [properties, setProperties] = useState<Property[]>([]);
  const [units, setUnits] = useState<Unit[]>([]);
  const [renters, setRenters] = useState<Renter[]>([]);
  const [selectedPropId, setSelectedPropId] = useState('');
  const [unitId, setUnitId] = useState('');
  const [renterId, setRenterId] = useState('');
  const [description, setDescription] = useState('');
  const [priority, setPriority] = useState<'LOW' | 'MEDIUM' | 'HIGH' | 'EMERGENCY'>('MEDIUM');

  // Vendor assignment state
  const [vendorId, setVendorId] = useState('');

  // New vendor form state
  const [vendorName, setVendorName] = useState('');
  const [vendorServiceType, setVendorServiceType] = useState('');
  const [vendorPhone, setVendorPhone] = useState('');
  const [vendorEmail, setVendorEmail] = useState('');

  const loadData = async () => {
    try {
      setLoading(true);
      const [reqPage, vendorPage] = await Promise.all([
        maintenanceApi.list(0, 50),
        vendorApi.list(0, 100),
      ]);
      setRequests(reqPage.content);
      setVendors(vendorPage.content);
      if (vendorPage.content.length > 0) setVendorId(vendorPage.content[0].id);
    } catch (err: any) {
      console.error(err.message || 'Failed to load maintenance data');
    } finally {
      setLoading(false);
    }
  };

  const loadPropertiesForModal = async () => {
    try {
      const pPage = await propertyApi.list(0, 50);
      setProperties(pPage.content);
      if (pPage.content.length > 0) {
        setSelectedPropId(pPage.content[0].id);
        const uList = (await propertyApi.listUnits(pPage.content[0].id)).content;
        setUnits(uList);
        if (uList.length > 0) setUnitId(uList[0].id);
      }
    } catch (e) {
      console.error('Failed to load properties for maintenance modal', e);
    }
    try {
      const renterPage = await renterApi.list(0, 100);
      setRenters(renterPage.content);
      if (renterPage.content.length > 0) setRenterId(renterPage.content[0].id);
    } catch (e) {
      console.error('Failed to load renters for maintenance modal', e);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handlePropertyChange = async (propId: string) => {
    setSelectedPropId(propId);
    try {
      const uList = (await propertyApi.listUnits(propId)).content;
      setUnits(uList);
      if (uList.length > 0) setUnitId(uList[0].id);
      else setUnitId('');
    } catch (e) {
      setUnits([]);
      setUnitId('');
    }
  };

  const handleCreateRequest = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!unitId) {
      alert('Please select a unit');
      return;
    }
    if (!renterId) {
      alert('Please select a renter');
      return;
    }
    try {
      await maintenanceApi.create({ unitId, renterId, description, priority });
      setShowRequestModal(false);
      setDescription('');
      loadData();
    } catch (err: any) {
      alert(err.message || 'Failed to create maintenance request');
    }
  };

  const handleCreateVendor = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await vendorApi.create({
        name: vendorName,
        serviceType: vendorServiceType,
        phone: vendorPhone,
        email: vendorEmail,
      });
      setShowVendorModal(false);
      setVendorName('');
      setVendorServiceType('');
      setVendorPhone('');
      setVendorEmail('');
      loadData();
    } catch (err: any) {
      alert(err.message || 'Failed to create vendor');
    }
  };

  const handleAssignVendor = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedRequest) return;
    if (!vendorId) {
      alert('Please create a vendor first');
      return;
    }
    try {
      await maintenanceApi.assign(selectedRequest.id, vendorId, selectedRequest.version);
      setShowAssignModal(false);
      setSelectedRequest(null);
      loadData();
    } catch (err: any) {
      alert(err.message || 'Failed to assign vendor (Optimistic locking mismatch?)');
    }
  };

  const handleStart = async (req: MaintenanceRequest) => {
    try {
      await maintenanceApi.start(req.id, req.version);
      loadData();
    } catch (err: any) {
      alert(err.message || 'Failed to start request');
    }
  };

  const handleResolve = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedRequest) return;
    try {
      await maintenanceApi.resolve(selectedRequest.id, selectedRequest.version, resolveNotes);
      setShowResolveModal(false);
      setSelectedRequest(null);
      setResolveNotes('');
      loadData();
    } catch (err: any) {
      alert(err.message || 'Failed to resolve request');
    }
  };

  if (loading && requests.length === 0) {
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
          <h1 className="text-2xl font-bold text-slate-950 tracking-tight">Maintenance & Vendors</h1>
          <p className="text-sm text-slate-600">Track service requests, vendor assignments, and resolution workflow</p>
        </div>
        <div className="flex items-center space-x-3">
          <button
            onClick={() => setShowVendorModal(true)}
            className="flex items-center justify-center space-x-1.5 px-4 py-2.5 bg-slate-900 hover:bg-slate-800 text-white rounded-xl font-semibold shadow transition-all text-sm"
          >
            <Users className="w-4 h-4" />
            <span>Add Vendor</span>
          </button>
          <button
            onClick={() => {
              setShowRequestModal(true);
              loadPropertiesForModal();
            }}
            className="flex items-center justify-center space-x-1.5 px-4 py-2.5 bg-blue-600 hover:bg-blue-500 text-white rounded-xl font-semibold shadow-lg shadow-blue-600/35 transition-all text-sm"
          >
            <Plus className="w-4 h-4" />
            <span>New Request</span>
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Maintenance Requests List */}
        <div className="lg:col-span-2 bg-white rounded-2xl border border-slate-200 shadow-xs overflow-hidden flex flex-col">
          <div className="p-4 border-b border-slate-100 bg-slate-50 flex items-center justify-between">
            <h3 className="font-bold text-xs uppercase tracking-wider text-slate-500">Maintenance Requests ({requests.length})</h3>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-sm">
              <thead>
                <tr className="bg-slate-50/50 text-slate-400 text-xs uppercase tracking-wider border-b border-slate-200">
                  <th className="py-3 px-6 font-semibold">Unit ID</th>
                  <th className="py-3 px-6 font-semibold">Priority</th>
                  <th className="py-3 px-6 font-semibold">Status</th>
                  <th className="py-3 px-6 font-semibold">Description</th>
                  <th className="py-3 px-6 font-semibold text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {requests.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="py-12 text-center text-slate-400 text-sm">
                      No maintenance requests recorded.
                    </td>
                  </tr>
                ) : (
                  requests.map((req) => (
                    <tr key={req.id} className="hover:bg-slate-50/80">
                      <td className="py-4 px-6 font-mono text-xs text-slate-600">{req.unitId.substring(0, 8)}...</td>
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
                        <div className="flex items-center space-x-1.5">
                          <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-blue-50 text-blue-700">
                            {req.status}
                          </span>
                          <span className="text-[10px] text-slate-400 font-mono">v{req.version}</span>
                        </div>
                      </td>
                      <td className="py-4 px-6 text-slate-700 max-w-xs truncate">{req.description}</td>
                      <td className="py-4 px-6 text-right space-x-2">
                        {req.status === 'SUBMITTED' && (
                          <button
                            onClick={() => {
                              setSelectedRequest(req);
                              setShowAssignModal(true);
                            }}
                            className="px-3 py-1 bg-amber-600 hover:bg-amber-500 text-white rounded-lg text-xs font-semibold shadow transition-all"
                          >
                            Assign
                          </button>
                        )}
                        {req.status === 'ASSIGNED' && (
                          <button
                            onClick={() => handleStart(req)}
                            className="px-3 py-1 bg-blue-600 hover:bg-blue-500 text-white rounded-lg text-xs font-semibold shadow transition-all"
                          >
                            Start
                          </button>
                        )}
                        {req.status === 'IN_PROGRESS' && (
                          <button
                            onClick={() => {
                              setSelectedRequest(req);
                              setResolveNotes('');
                              setShowResolveModal(true);
                            }}
                            className="px-3 py-1 bg-green-600 hover:bg-green-500 text-white rounded-lg text-xs font-semibold shadow transition-all"
                          >
                            Resolve
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

        {/* Vendors Directory */}
        <div className="bg-white rounded-2xl border border-slate-200 shadow-xs overflow-hidden flex flex-col">
          <div className="p-4 border-b border-slate-100 bg-slate-50 flex items-center justify-between">
            <h3 className="font-bold text-xs uppercase tracking-wider text-slate-500">Vendors Directory ({vendors.length})</h3>
          </div>
          <div className="divide-y divide-slate-100 overflow-y-auto max-h-[500px]">
            {vendors.length === 0 ? (
              <p className="p-6 text-center text-slate-400 text-xs">No vendors available.</p>
            ) : (
              vendors.map((v) => (
                <div key={v.id} className="p-4 hover:bg-slate-50 transition-all">
                  <div className="flex items-center justify-between">
                    <h4 className="font-bold text-sm text-slate-900">{v.name}</h4>
                    <span className="text-[10px] font-semibold bg-blue-50 text-blue-700 px-2 py-0.5 rounded-full">
                      {v.serviceType}
                    </span>
                  </div>
                  <p className="text-xs text-slate-500 mt-1">{v.phone} • {v.email}</p>
                </div>
              ))
            )}
          </div>
        </div>
      </div>

      {/* New Maintenance Request Modal */}
      {showRequestModal && (
        <div className="fixed inset-0 bg-slate-950/50 backdrop-blur-xs flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-2xl border border-slate-200">
            <div className="flex items-center justify-between mb-4">
              <h3 className="font-bold text-lg text-slate-900">New Maintenance Request</h3>
              <button onClick={() => setShowRequestModal(false)} className="text-slate-400 hover:text-slate-600">
                <X className="w-5 h-5" />
              </button>
            </div>
            <form onSubmit={handleCreateRequest} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1">Select Property</label>
                <select
                  value={selectedPropId}
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
                    <option key={u.id} value={u.id}>{u.name}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1">Select Renter</label>
                <select
                  value={renterId}
                  onChange={(e) => setRenterId(e.target.value)}
                  className="w-full px-3 py-2 border border-slate-300 rounded-xl text-sm focus:outline-none focus:border-blue-600"
                >
                  <option value="">{renters.length === 0 ? 'No renters yet — add one on the Leases page' : 'Select a renter'}</option>
                  {renters.map((r) => (
                    <option key={r.id} value={r.id}>{r.fullName} ({r.email})</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1">Priority</label>
                <select
                  value={priority}
                  onChange={(e: any) => setPriority(e.target.value)}
                  className="w-full px-3 py-2 border border-slate-300 rounded-xl text-sm focus:outline-none focus:border-blue-600"
                >
                  <option value="LOW">Low</option>
                  <option value="MEDIUM">Medium</option>
                  <option value="HIGH">High</option>
                  <option value="EMERGENCY">Emergency</option>
                </select>
              </div>
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1">Description</label>
                <textarea
                  required
                  rows={3}
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="Describe the maintenance issue..."
                  className="w-full px-3 py-2 border border-slate-300 rounded-xl text-sm focus:outline-none focus:border-blue-600"
                ></textarea>
              </div>
              <div className="flex justify-end space-x-3 pt-2">
                <button
                  type="button"
                  onClick={() => setShowRequestModal(false)}
                  className="px-4 py-2 border border-slate-300 rounded-xl text-xs font-semibold text-slate-700 hover:bg-slate-50"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 bg-blue-600 hover:bg-blue-500 text-white rounded-xl text-xs font-semibold shadow"
                >
                  Submit Request
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Assign Vendor Modal */}
      {showAssignModal && selectedRequest && (
        <div className="fixed inset-0 bg-slate-950/50 backdrop-blur-xs flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-2xl border border-slate-200">
            <div className="flex items-center justify-between mb-4">
              <h3 className="font-bold text-lg text-slate-900">Assign Vendor to Request</h3>
              <button onClick={() => setShowAssignModal(false)} className="text-slate-400 hover:text-slate-600">
                <X className="w-5 h-5" />
              </button>
            </div>
            <form onSubmit={handleAssignVendor} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1">Select Vendor</label>
                <select
                  value={vendorId}
                  onChange={(e) => setVendorId(e.target.value)}
                  className="w-full px-3 py-2 border border-slate-300 rounded-xl text-sm focus:outline-none focus:border-blue-600"
                >
                  {vendors.map((v) => (
                    <option key={v.id} value={v.id}>{v.name} ({v.serviceType})</option>
                  ))}
                </select>
                <p className="text-[11px] text-slate-400 mt-1">Sends If-Match: v{selectedRequest.version} header</p>
              </div>
              <div className="flex justify-end space-x-3 pt-2">
                <button
                  type="button"
                  onClick={() => setShowAssignModal(false)}
                  className="px-4 py-2 border border-slate-300 rounded-xl text-xs font-semibold text-slate-700 hover:bg-slate-50"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 bg-amber-600 hover:bg-amber-500 text-white rounded-xl text-xs font-semibold shadow"
                >
                  Assign Vendor
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Resolve Request Modal */}
      {showResolveModal && selectedRequest && (
        <div className="fixed inset-0 bg-slate-950/50 backdrop-blur-xs flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-2xl border border-slate-200">
            <div className="flex items-center justify-between mb-4">
              <h3 className="font-bold text-lg text-slate-900">Resolve Maintenance Request</h3>
              <button onClick={() => setShowResolveModal(false)} className="text-slate-400 hover:text-slate-600">
                <X className="w-5 h-5" />
              </button>
            </div>
            <form onSubmit={handleResolve} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1">Resolution Notes</label>
                <textarea
                  rows={3}
                  value={resolveNotes}
                  onChange={(e) => setResolveNotes(e.target.value)}
                  placeholder="Describe how the issue was resolved..."
                  className="w-full px-3 py-2 border border-slate-300 rounded-xl text-sm focus:outline-none focus:border-blue-600"
                ></textarea>
                <p className="text-[11px] text-slate-400 mt-1">Sends If-Match: v{selectedRequest.version} header</p>
              </div>
              <div className="flex justify-end space-x-3 pt-2">
                <button
                  type="button"
                  onClick={() => setShowResolveModal(false)}
                  className="px-4 py-2 border border-slate-300 rounded-xl text-xs font-semibold text-slate-700 hover:bg-slate-50"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 bg-green-600 hover:bg-green-500 text-white rounded-xl text-xs font-semibold shadow"
                >
                  Mark Resolved
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Add Vendor Modal */}
      {showVendorModal && (
        <div className="fixed inset-0 bg-slate-950/50 backdrop-blur-xs flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-2xl border border-slate-200">
            <div className="flex items-center justify-between mb-4">
              <h3 className="font-bold text-lg text-slate-900">Add New Vendor</h3>
              <button onClick={() => setShowVendorModal(false)} className="text-slate-400 hover:text-slate-600">
                <X className="w-5 h-5" />
              </button>
            </div>
            <form onSubmit={handleCreateVendor} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1">Vendor Name</label>
                <input
                  type="text"
                  required
                  value={vendorName}
                  onChange={(e) => setVendorName(e.target.value)}
                  placeholder="Apex Plumbing Services"
                  className="w-full px-3 py-2 border border-slate-300 rounded-xl text-sm focus:outline-none focus:border-blue-600"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1">Service Type</label>
                <input
                  type="text"
                  required
                  value={vendorServiceType}
                  onChange={(e) => setVendorServiceType(e.target.value)}
                  placeholder="Plumbing & Heating"
                  className="w-full px-3 py-2 border border-slate-300 rounded-xl text-sm focus:outline-none focus:border-blue-600"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1">Phone</label>
                <input
                  type="text"
                  required
                  value={vendorPhone}
                  onChange={(e) => setVendorPhone(e.target.value)}
                  placeholder="555-0199"
                  className="w-full px-3 py-2 border border-slate-300 rounded-xl text-sm focus:outline-none focus:border-blue-600"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1">Email</label>
                <input
                  type="email"
                  required
                  value={vendorEmail}
                  onChange={(e) => setVendorEmail(e.target.value)}
                  placeholder="contact@apexplumbing.com"
                  className="w-full px-3 py-2 border border-slate-300 rounded-xl text-sm focus:outline-none focus:border-blue-600"
                />
              </div>
              <div className="flex justify-end space-x-3 pt-2">
                <button
                  type="button"
                  onClick={() => setShowVendorModal(false)}
                  className="px-4 py-2 border border-slate-300 rounded-xl text-xs font-semibold text-slate-700 hover:bg-slate-50"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 bg-slate-900 hover:bg-slate-800 text-white rounded-xl text-xs font-semibold shadow"
                >
                  Create Vendor
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
