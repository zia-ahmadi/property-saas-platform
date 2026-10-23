import React, { useEffect, useState } from 'react';
import { invoiceApi, paymentApi, leaseApi, type Invoice, type Lease } from '../services/api';
import { DollarSign, Plus, X } from 'lucide-react';

export const BillingPage: React.FC = () => {
  const [invoices, setInvoices] = useState<Invoice[]>([]);
  const [loading, setLoading] = useState(true);

  // Modals state
  const [showInvoiceModal, setShowInvoiceModal] = useState(false);
  const [showPaymentModal, setShowPaymentModal] = useState(false);
  const [selectedInvoice, setSelectedInvoice] = useState<Invoice | null>(null);

  // Leases for invoice creation
  const [leases, setLeases] = useState<Lease[]>([]);
  const [leaseId, setLeaseId] = useState('');
  const [amount, setAmount] = useState(1500);
  const [dueDate, setDueDate] = useState('');

  // Payment form state
  const [paymentAmount, setPaymentAmount] = useState(1500);
  const [paymentMethod, setPaymentMethod] = useState('BANK_TRANSFER');
  const [referenceNumber, setReferenceNumber] = useState('');

  const loadInvoices = async () => {
    try {
      setLoading(true);
      const page = await invoiceApi.list(0, 50);
      setInvoices(page.content);
    } catch (err: any) {
      console.error(err.message || 'Failed to load invoices');
    } finally {
      setLoading(false);
    }
  };

  const loadLeases = async () => {
    try {
      const page = await leaseApi.list(0, 50);
      // Invoices can only be raised against active leases.
      const activeLeases = page.content.filter((l) => l.status === 'ACTIVE');
      setLeases(activeLeases);
      if (activeLeases.length > 0) setLeaseId(activeLeases[0].id);
      else setLeaseId('');
    } catch (e) {
      console.error('Failed to load leases for invoice modal', e);
    }
  };

  useEffect(() => {
    loadInvoices();
  }, []);

  const handleCreateInvoice = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await invoiceApi.create({ leaseId, amount, dueDate });
      setShowInvoiceModal(false);
      setAmount(1500);
      setDueDate('');
      loadInvoices();
    } catch (err: any) {
      alert(err.message || 'Failed to create invoice');
    }
  };

  const handleRecordPayment = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedInvoice) return;
    try {
      // Generate a client-side UUID v4 for Idempotency-Key
      const idempotencyKey = crypto.randomUUID();
      await paymentApi.record(
        {
          invoiceId: selectedInvoice.id,
          amount: paymentAmount,
          paymentMethod,
          referenceNumber: referenceNumber || 'REF-' + Math.floor(Math.random() * 100000),
        },
        idempotencyKey
      );
      setShowPaymentModal(false);
      setSelectedInvoice(null);
      setReferenceNumber('');
      loadInvoices();
    } catch (err: any) {
      alert(err.message || 'Failed to record payment');
    }
  };

  if (loading && invoices.length === 0) {
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
          <h1 className="text-2xl font-bold text-slate-950 tracking-tight">Billing & Payments</h1>
          <p className="text-sm text-slate-600">Manage invoices and idempotent payment recording</p>
        </div>
        <button
          onClick={() => {
            setShowInvoiceModal(true);
            loadLeases();
          }}
          className="flex items-center justify-center space-x-2 px-4 py-2.5 bg-blue-600 hover:bg-blue-500 text-white rounded-xl font-semibold shadow-lg shadow-blue-600/35 transition-all text-sm"
        >
          <Plus className="w-4 h-4" />
          <span>Generate Invoice</span>
        </button>
      </div>

      <div className="bg-white rounded-2xl border border-slate-200 shadow-xs overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse text-sm">
            <thead>
              <tr className="bg-slate-50 text-slate-400 text-xs uppercase tracking-wider border-b border-slate-200">
                <th className="py-3 px-6 font-semibold">Invoice ID</th>
                <th className="py-3 px-6 font-semibold">Lease ID</th>
                <th className="py-3 px-6 font-semibold">Amount</th>
                <th className="py-3 px-6 font-semibold">Due Date</th>
                <th className="py-3 px-6 font-semibold">Status</th>
                <th className="py-3 px-6 font-semibold text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {invoices.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-12 text-center text-slate-400 text-sm">
                    No invoices generated yet.
                  </td>
                </tr>
              ) : (
                invoices.map((inv) => (
                  <tr key={inv.id} className="hover:bg-slate-50/80">
                    <td className="py-4 px-6 font-mono text-xs text-slate-700">{inv.id.substring(0, 8)}...</td>
                    <td className="py-4 px-6 font-mono text-xs text-slate-500">{inv.leaseId.substring(0, 8)}...</td>
                    <td className="py-4 px-6 font-semibold text-slate-900">${inv.amount.toLocaleString()}</td>
                    <td className="py-4 px-6 text-slate-600 text-xs font-mono">{inv.dueDate}</td>
                    <td className="py-4 px-6">
                      <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-bold ${
                        inv.status === 'PAID' ? 'bg-green-100 text-green-700' :
                        inv.status === 'OVERDUE' ? 'bg-red-100 text-red-700' :
                        inv.status === 'CANCELLED' ? 'bg-slate-100 text-slate-600' :
                        'bg-blue-100 text-blue-700'
                      }`}>
                        {inv.status}
                      </span>
                    </td>
                    <td className="py-4 px-6 text-right">
                      {inv.status !== 'PAID' && inv.status !== 'CANCELLED' && (
                        <button
                          onClick={() => {
                            setSelectedInvoice(inv);
                            setPaymentAmount(inv.amount);
                            setShowPaymentModal(true);
                          }}
                          className="px-3 py-1 bg-green-600 hover:bg-green-500 text-white rounded-lg text-xs font-semibold shadow transition-all inline-flex items-center space-x-1"
                        >
                          <DollarSign className="w-3.5 h-3.5" />
                          <span>Record Payment</span>
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

      {/* Generate Invoice Modal */}
      {showInvoiceModal && (
        <div className="fixed inset-0 bg-slate-950/50 backdrop-blur-xs flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-2xl border border-slate-200">
            <div className="flex items-center justify-between mb-4">
              <h3 className="font-bold text-lg text-slate-900">Generate Invoice</h3>
              <button onClick={() => setShowInvoiceModal(false)} className="text-slate-400 hover:text-slate-600">
                <X className="w-5 h-5" />
              </button>
            </div>
            <form onSubmit={handleCreateInvoice} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1">Select Lease</label>
                <select
                  value={leaseId}
                  onChange={(e) => setLeaseId(e.target.value)}
                  className="w-full px-3 py-2 border border-slate-300 rounded-xl text-sm focus:outline-none focus:border-blue-600"
                >
                  {leases.map((l) => (
                    <option key={l.id} value={l.id}>Lease {l.id.substring(0, 8)} (${l.monthlyRent}/mo)</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1">Amount ($)</label>
                <input
                  type="number"
                  required
                  min={0}
                  value={amount}
                  onChange={(e) => setAmount(parseFloat(e.target.value))}
                  className="w-full px-3 py-2 border border-slate-300 rounded-xl text-sm focus:outline-none focus:border-blue-600"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1">Due Date</label>
                <input
                  type="date"
                  required
                  value={dueDate}
                  onChange={(e) => setDueDate(e.target.value)}
                  className="w-full px-3 py-2 border border-slate-300 rounded-xl text-sm focus:outline-none focus:border-blue-600"
                />
              </div>
              <div className="flex justify-end space-x-3 pt-2">
                <button
                  type="button"
                  onClick={() => setShowInvoiceModal(false)}
                  className="px-4 py-2 border border-slate-300 rounded-xl text-xs font-semibold text-slate-700 hover:bg-slate-50"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 bg-blue-600 hover:bg-blue-500 text-white rounded-xl text-xs font-semibold shadow"
                >
                  Create Invoice
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Record Payment Modal */}
      {showPaymentModal && selectedInvoice && (
        <div className="fixed inset-0 bg-slate-950/50 backdrop-blur-xs flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-2xl border border-slate-200">
            <div className="flex items-center justify-between mb-4">
              <h3 className="font-bold text-lg text-slate-900">Record Payment for Invoice</h3>
              <button onClick={() => setShowPaymentModal(false)} className="text-slate-400 hover:text-slate-600">
                <X className="w-5 h-5" />
              </button>
            </div>
            <form onSubmit={handleRecordPayment} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1">Payment Amount ($)</label>
                <input
                  type="number"
                  required
                  min={0}
                  value={paymentAmount}
                  onChange={(e) => setPaymentAmount(parseFloat(e.target.value))}
                  className="w-full px-3 py-2 border border-slate-300 rounded-xl text-sm focus:outline-none focus:border-blue-600"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1">Payment Method</label>
                <select
                  value={paymentMethod}
                  onChange={(e) => setPaymentMethod(e.target.value)}
                  className="w-full px-3 py-2 border border-slate-300 rounded-xl text-sm focus:outline-none focus:border-blue-600"
                >
                  <option value="BANK_TRANSFER">Bank Transfer</option>
                  <option value="CREDIT_CARD">Credit Card</option>
                  <option value="CASH">Cash</option>
                </select>
              </div>
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1">Reference Number</label>
                <input
                  type="text"
                  value={referenceNumber}
                  onChange={(e) => setReferenceNumber(e.target.value)}
                  placeholder="TXN-987654"
                  className="w-full px-3 py-2 border border-slate-300 rounded-xl text-sm focus:outline-none focus:border-blue-600"
                />
                <p className="text-[11px] text-slate-400 mt-1">Sent with Idempotency-Key header</p>
              </div>
              <div className="flex justify-end space-x-3 pt-2">
                <button
                  type="button"
                  onClick={() => setShowPaymentModal(false)}
                  className="px-4 py-2 border border-slate-300 rounded-xl text-xs font-semibold text-slate-700 hover:bg-slate-50"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 bg-green-600 hover:bg-green-500 text-white rounded-xl text-xs font-semibold shadow"
                >
                  Submit Payment
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
