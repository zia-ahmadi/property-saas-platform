import React, { useEffect, useState } from 'react';
import { propertyApi, type Property, type Unit } from '../services/api';
import { Plus, Home, ChevronRight, X } from 'lucide-react';

export const PropertiesPage: React.FC = () => {
  const [properties, setProperties] = useState<Property[]>([]);
  const [selectedProperty, setSelectedProperty] = useState<Property | null>(null);
  const [units, setUnits] = useState<Unit[]>([]);
  const [loading, setLoading] = useState(true);

  // Modals state
  const [showPropertyModal, setShowPropertyModal] = useState(false);
  const [showUnitModal, setShowUnitModal] = useState(false);

  // Form states
  const [name, setName] = useState('');
  const [address, setAddress] = useState('');
  const [totalUnits, setTotalUnits] = useState(10);

  const [unitNumber, setUnitNumber] = useState('');
  const [floor, setFloor] = useState(1);
  const [rentAmount, setRentAmount] = useState(1500);

  const loadProperties = async () => {
    try {
      setLoading(true);
      const page = await propertyApi.list(0, 50);
      setProperties(page.content);
      if (page.content.length > 0 && !selectedProperty) {
        handleSelectProperty(page.content[0]);
      }
    } catch (err: any) {
      console.error(err.message || 'Failed to load properties');
    } finally {
      setLoading(false);
    }
  };

  const handleSelectProperty = async (prop: Property) => {
    setSelectedProperty(prop);
    try {
      const propertyUnits = await propertyApi.listUnits(prop.id);
      setUnits(propertyUnits);
    } catch (err) {
      console.error('Failed to load units for property:', err);
      setUnits([]);
    }
  };

  useEffect(() => {
    loadProperties();
  }, []);

  const handleCreateProperty = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await propertyApi.create({ name, address, totalUnits });
      setShowPropertyModal(false);
      setName('');
      setAddress('');
      setTotalUnits(10);
      loadProperties();
    } catch (err: any) {
      alert(err.message || 'Failed to create property');
    }
  };

  const handleCreateUnit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedProperty) return;
    try {
      await propertyApi.createUnit(selectedProperty.id, { unitNumber, floor, rentAmount });
      setShowUnitModal(false);
      setUnitNumber('');
      setFloor(1);
      setRentAmount(1500);
      handleSelectProperty(selectedProperty);
    } catch (err: any) {
      alert(err.message || 'Failed to create unit');
    }
  };

  if (loading && properties.length === 0) {
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
          <h1 className="text-2xl font-bold text-slate-950 tracking-tight">Properties & Units Management</h1>
          <p className="text-sm text-slate-600">Manage real estate portfolios and tenant-scoped units</p>
        </div>
        <button
          onClick={() => setShowPropertyModal(true)}
          className="flex items-center justify-center space-x-2 px-4 py-2.5 bg-blue-600 hover:bg-blue-500 text-white rounded-xl font-semibold shadow-lg shadow-blue-600/35 transition-all text-sm"
        >
          <Plus className="w-4 h-4" />
          <span>Add Property</span>
        </button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Properties List */}
        <div className="bg-white rounded-2xl border border-slate-200 shadow-xs overflow-hidden flex flex-col">
          <div className="p-4 border-b border-slate-100 bg-slate-50">
            <h3 className="font-bold text-xs uppercase tracking-wider text-slate-500">Properties ({properties.length})</h3>
          </div>
          <div className="divide-y divide-slate-100 overflow-y-auto max-h-[600px]">
            {properties.length === 0 ? (
              <p className="p-6 text-center text-slate-400 text-xs">No properties registered.</p>
            ) : (
              properties.map((prop) => {
                const isSelected = selectedProperty?.id === prop.id;
                return (
                  <div
                    key={prop.id}
                    onClick={() => handleSelectProperty(prop)}
                    className={`p-4 cursor-pointer transition-all flex items-center justify-between ${
                      isSelected ? 'bg-blue-50/70 border-l-4 border-blue-600' : 'hover:bg-slate-50'
                    }`}
                  >
                    <div>
                      <h4 className="font-bold text-sm text-slate-900">{prop.name}</h4>
                      <p className="text-xs text-slate-500 truncate mt-0.5">{prop.address}</p>
                      <span className="inline-block mt-2 text-[10px] font-semibold bg-slate-100 text-slate-600 px-2 py-0.5 rounded-full">
                        {prop.totalUnits} Units Allocated
                      </span>
                    </div>
                    <ChevronRight className={`w-4 h-4 ${isSelected ? 'text-blue-600' : 'text-slate-300'}`} />
                  </div>
                );
              })
            )}
          </div>
        </div>

        {/* Units Details */}
        <div className="lg:col-span-2 bg-white rounded-2xl border border-slate-200 shadow-xs overflow-hidden flex flex-col">
          {selectedProperty ? (
            <>
              <div className="p-6 border-b border-slate-100 flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-slate-50/50">
                <div>
                  <h3 className="font-bold text-lg text-slate-900">{selectedProperty.name}</h3>
                  <p className="text-xs text-slate-500">{selectedProperty.address}</p>
                </div>
                <button
                  onClick={() => setShowUnitModal(true)}
                  className="flex items-center justify-center space-x-1.5 px-3.5 py-2 bg-slate-900 hover:bg-slate-800 text-white rounded-xl text-xs font-semibold shadow transition-all"
                >
                  <Plus className="w-3.5 h-3.5" />
                  <span>Add Unit</span>
                </button>
              </div>

              <div className="p-6 flex-1 overflow-y-auto">
                <h4 className="font-bold text-xs uppercase tracking-wider text-slate-400 mb-4">Units in Property ({units.length})</h4>
                {units.length === 0 ? (
                  <div className="text-center py-12 text-slate-400 text-sm border-2 border-dashed border-slate-200 rounded-2xl">
                    No units added for this property yet.
                  </div>
                ) : (
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    {units.map((unit) => (
                      <div key={unit.id} className="p-4 rounded-xl border border-slate-200 bg-white shadow-xs hover:border-slate-300 transition-all flex items-center justify-between">
                        <div>
                          <div className="flex items-center space-x-2">
                            <span className="font-bold text-slate-900 text-base">Unit {unit.unitNumber}</span>
                            <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${
                              unit.status === 'OCCUPIED' ? 'bg-green-100 text-green-700' :
                              unit.status === 'MAINTENANCE' ? 'bg-amber-100 text-amber-700' :
                              'bg-slate-100 text-slate-600'
                            }`}>
                              {unit.status}
                            </span>
                          </div>
                          <p className="text-xs text-slate-500 mt-1">Floor {unit.floor} • Rent: <span className="font-semibold text-slate-900">${unit.rentAmount.toLocaleString()}</span>/mo</p>
                        </div>
                        <Home className="w-5 h-5 text-slate-400" />
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </>
          ) : (
            <div className="flex-1 flex items-center justify-center p-12 text-slate-400 text-sm">
              Select a property to view its units.
            </div>
          )}
        </div>
      </div>

      {/* Add Property Modal */}
      {showPropertyModal && (
        <div className="fixed inset-0 bg-slate-950/50 backdrop-blur-xs flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-2xl border border-slate-200">
            <div className="flex items-center justify-between mb-4">
              <h3 className="font-bold text-lg text-slate-900">Add New Property</h3>
              <button onClick={() => setShowPropertyModal(false)} className="text-slate-400 hover:text-slate-600">
                <X className="w-5 h-5" />
              </button>
            </div>
            <form onSubmit={handleCreateProperty} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1">Property Name</label>
                <input
                  type="text"
                  required
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="Sunset Apartments"
                  className="w-full px-3 py-2 border border-slate-300 rounded-xl text-sm focus:outline-none focus:border-blue-600"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1">Address</label>
                <input
                  type="text"
                  required
                  value={address}
                  onChange={(e) => setAddress(e.target.value)}
                  placeholder="123 Main St, City"
                  className="w-full px-3 py-2 border border-slate-300 rounded-xl text-sm focus:outline-none focus:border-blue-600"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1">Total Units</label>
                <input
                  type="number"
                  required
                  min={1}
                  value={totalUnits}
                  onChange={(e) => setTotalUnits(parseInt(e.target.value))}
                  className="w-full px-3 py-2 border border-slate-300 rounded-xl text-sm focus:outline-none focus:border-blue-600"
                />
              </div>
              <div className="flex justify-end space-x-3 pt-2">
                <button
                  type="button"
                  onClick={() => setShowPropertyModal(false)}
                  className="px-4 py-2 border border-slate-300 rounded-xl text-xs font-semibold text-slate-700 hover:bg-slate-50"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 bg-blue-600 hover:bg-blue-500 text-white rounded-xl text-xs font-semibold shadow"
                >
                  Create Property
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Add Unit Modal */}
      {showUnitModal && (
        <div className="fixed inset-0 bg-slate-950/50 backdrop-blur-xs flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-2xl border border-slate-200">
            <div className="flex items-center justify-between mb-4">
              <h3 className="font-bold text-lg text-slate-900">Add Unit to {selectedProperty?.name}</h3>
              <button onClick={() => setShowUnitModal(false)} className="text-slate-400 hover:text-slate-600">
                <X className="w-5 h-5" />
              </button>
            </div>
            <form onSubmit={handleCreateUnit} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1">Unit Number</label>
                <input
                  type="text"
                  required
                  value={unitNumber}
                  onChange={(e) => setUnitNumber(e.target.value)}
                  placeholder="101"
                  className="w-full px-3 py-2 border border-slate-300 rounded-xl text-sm focus:outline-none focus:border-blue-600"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1">Floor</label>
                <input
                  type="number"
                  required
                  value={floor}
                  onChange={(e) => setFloor(parseInt(e.target.value))}
                  className="w-full px-3 py-2 border border-slate-300 rounded-xl text-sm focus:outline-none focus:border-blue-600"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1">Monthly Rent Amount ($)</label>
                <input
                  type="number"
                  required
                  min={0}
                  step={50}
                  value={rentAmount}
                  onChange={(e) => setRentAmount(parseFloat(e.target.value))}
                  className="w-full px-3 py-2 border border-slate-300 rounded-xl text-sm focus:outline-none focus:border-blue-600"
                />
              </div>
              <div className="flex justify-end space-x-3 pt-2">
                <button
                  type="button"
                  onClick={() => setShowUnitModal(false)}
                  className="px-4 py-2 border border-slate-300 rounded-xl text-xs font-semibold text-slate-700 hover:bg-slate-50"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 bg-blue-600 hover:bg-blue-500 text-white rounded-xl text-xs font-semibold shadow"
                >
                  Create Unit
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
