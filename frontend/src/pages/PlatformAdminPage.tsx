import React, { useEffect, useState } from 'react';
import { Building2, Users } from 'lucide-react';
import {
  platformAdminApi,
  type PlatformOrganization,
  type PlatformUser,
} from '../services/api';

const PAGE_SIZE = 20;
const ORGANIZATION_ROLES = ['ADMIN', 'MANAGER', 'TENANT'] as const;
type OrganizationRole = (typeof ORGANIZATION_ROLES)[number];

const isOrganizationRole = (role: string): role is OrganizationRole =>
  ORGANIZATION_ROLES.some((candidate) => candidate === role);

export const PlatformAdminPage: React.FC = () => {
  const [organizations, setOrganizations] = useState<PlatformOrganization[]>([]);
  const [organizationPage, setOrganizationPage] = useState(0);
  const [organizationCount, setOrganizationCount] = useState(0);
  const [editingOrganizationId, setEditingOrganizationId] = useState<string | null>(null);
  const [organizationNameDraft, setOrganizationNameDraft] = useState('');
  const [selectedOrganization, setSelectedOrganization] = useState('');
  const [users, setUsers] = useState<PlatformUser[]>([]);
  const [userPage, setUserPage] = useState(0);
  const [userCount, setUserCount] = useState(0);
  const [loadingOrganizations, setLoadingOrganizations] = useState(true);
  const [loadingUsers, setLoadingUsers] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const refreshUsers = async () => {
    const result = await platformAdminApi.listUsers(
      selectedOrganization || undefined,
      userPage,
      PAGE_SIZE,
    );
    setUsers(result.content);
    setUserCount(result.totalElements);
  };

  const saveOrganizationName = async (organization: PlatformOrganization) => {
    try {
      setError(null);
      await platformAdminApi.renameOrganization(organization.id, organizationNameDraft);
      const result = await platformAdminApi.listOrganizations(organizationPage, PAGE_SIZE);
      setOrganizations(result.content);
      setEditingOrganizationId(null);
    } catch (cause: unknown) {
      setError(cause instanceof Error ? cause.message : 'Could not rename organization');
    }
  };

  useEffect(() => {
    let current = true;
    platformAdminApi.listOrganizations(organizationPage, PAGE_SIZE)
      .then((result) => {
        if (!current) return;
        setOrganizations(result.content);
        setOrganizationCount(result.totalElements);
      })
      .catch((cause: unknown) => {
        if (current) setError(cause instanceof Error ? cause.message : 'Could not load organizations');
      })
      .finally(() => {
        if (current) setLoadingOrganizations(false);
      });
    return () => {
      current = false;
    };
  }, [organizationPage]);

  useEffect(() => {
    let current = true;
    platformAdminApi.listUsers(selectedOrganization || undefined, userPage, PAGE_SIZE)
      .then((result) => {
        if (!current) return;
        setUsers(result.content);
        setUserCount(result.totalElements);
      })
      .catch((cause: unknown) => {
        if (current) setError(cause instanceof Error ? cause.message : 'Could not load user accounts');
      })
      .finally(() => {
        if (current) setLoadingUsers(false);
      });
    return () => {
      current = false;
    };
  }, [selectedOrganization, userPage]);

  const updateActive = async (user: PlatformUser) => {
    try {
      setError(null);
      await platformAdminApi.setUserActive(user.id, !user.active);
      await refreshUsers();
    } catch (cause: unknown) {
      setError(cause instanceof Error ? cause.message : 'Could not update account status');
    }
  };

  const updateRole = async (userId: string, role: (typeof ORGANIZATION_ROLES)[number]) => {
    try {
      setError(null);
      await platformAdminApi.setOrganizationRole(userId, role);
      await refreshUsers();
    } catch (cause: unknown) {
      setError(cause instanceof Error ? cause.message : 'Could not update organization role');
    }
  };

  const pageCount = (count: number) => Math.max(1, Math.ceil(count / PAGE_SIZE));

  return (
    <div className="space-y-8">
      <header>
        <p className="text-xs font-semibold uppercase tracking-wider text-blue-600">Platform administration</p>
        <h1 className="mt-1 text-2xl font-bold text-slate-900">Organizations and accounts</h1>
        <p className="mt-2 text-sm text-slate-600">
          Manage organization membership and user access. Tenant business records are not shown here.
        </p>
      </header>

      {error && (
        <div role="alert" className="rounded-xl border border-red-200 bg-red-50 p-3 text-sm text-red-700">
          {error}
        </div>
      )}

      <section className="rounded-2xl border border-slate-200 bg-white shadow-sm">
        <div className="flex items-center justify-between border-b border-slate-100 p-5">
          <div className="flex items-center gap-3">
            <Building2 className="h-5 w-5 text-blue-600" />
            <div>
              <h2 className="font-semibold text-slate-900">Organizations</h2>
              <p className="text-xs text-slate-500">{organizationCount} total</p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm disabled:opacity-40"
              disabled={organizationPage === 0 || loadingOrganizations}
              onClick={() => {
                setLoadingOrganizations(true);
                setOrganizationPage((page) => page - 1);
              }}
            >
              Previous
            </button>
            <span className="text-xs text-slate-500">
              {organizationPage + 1} / {pageCount(organizationCount)}
            </span>
            <button
              type="button"
              className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm disabled:opacity-40"
              disabled={(organizationPage + 1) * PAGE_SIZE >= organizationCount || loadingOrganizations}
              onClick={() => {
                setLoadingOrganizations(true);
                setOrganizationPage((page) => page + 1);
              }}
            >
              Next
            </button>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-5 py-3">Organization</th>
                <th className="px-5 py-3">Created</th>
                <th className="px-5 py-3">Accounts</th>
                <th className="px-5 py-3">Active</th>
                <th className="px-5 py-3">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {organizations.map((organization) => (
                <tr key={organization.id} className="text-slate-700">
                  <td className="px-5 py-3 font-medium text-slate-900">
                    {editingOrganizationId === organization.id ? (
                      <input
                        aria-label={`Organization name for ${organization.name}`}
                        value={organizationNameDraft}
                        onChange={(event) => setOrganizationNameDraft(event.target.value)}
                        maxLength={120}
                        className="rounded-lg border border-slate-200 px-2 py-1 font-normal"
                      />
                    ) : organization.name}
                  </td>
                  <td className="px-5 py-3">{new Date(organization.createdAt).toLocaleDateString()}</td>
                  <td className="px-5 py-3">{organization.userCount}</td>
                  <td className="px-5 py-3">{organization.activeUserCount}</td>
                  <td className="px-5 py-3">
                    {editingOrganizationId === organization.id ? (
                      <div className="flex gap-2">
                        <button
                          type="button"
                          onClick={() => void saveOrganizationName(organization)}
                          disabled={!organizationNameDraft.trim()}
                          className="rounded-lg border border-blue-200 px-3 py-1.5 text-xs font-medium text-blue-700 disabled:opacity-40"
                        >
                          Save
                        </button>
                        <button
                          type="button"
                          onClick={() => setEditingOrganizationId(null)}
                          className="rounded-lg border border-slate-200 px-3 py-1.5 text-xs"
                        >
                          Cancel
                        </button>
                      </div>
                    ) : (
                      <button
                        type="button"
                        onClick={() => {
                          setEditingOrganizationId(organization.id);
                          setOrganizationNameDraft(organization.name);
                        }}
                        className="rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-medium"
                      >
                        Rename
                      </button>
                    )}
                  </td>
                </tr>
              ))}
              {!loadingOrganizations && organizations.length === 0 && (
                <tr><td colSpan={5} className="px-5 py-8 text-center text-slate-500">No organizations found.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      <section className="rounded-2xl border border-slate-200 bg-white shadow-sm">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 p-5">
          <div className="flex items-center gap-3">
            <Users className="h-5 w-5 text-blue-600" />
            <div>
              <h2 className="font-semibold text-slate-900">User accounts</h2>
              <p className="text-xs text-slate-500">{userCount} total</p>
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <label htmlFor="organization-filter" className="text-xs text-slate-600">Organization</label>
            <select
              id="organization-filter"
              value={selectedOrganization}
              onChange={(event) => {
                setLoadingUsers(true);
                setSelectedOrganization(event.target.value);
                setUserPage(0);
              }}
              className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm"
            >
              <option value="">All organizations</option>
              {organizations.map((organization) => (
                <option key={organization.id} value={organization.id}>{organization.name}</option>
              ))}
            </select>
            <button
              type="button"
              className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm disabled:opacity-40"
              disabled={userPage === 0 || loadingUsers}
              onClick={() => {
                setLoadingUsers(true);
                setUserPage((page) => page - 1);
              }}
            >
              Previous
            </button>
            <span className="text-xs text-slate-500">{userPage + 1} / {pageCount(userCount)}</span>
            <button
              type="button"
              className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm disabled:opacity-40"
              disabled={(userPage + 1) * PAGE_SIZE >= userCount || loadingUsers}
              onClick={() => {
                setLoadingUsers(true);
                setUserPage((page) => page + 1);
              }}
            >
              Next
            </button>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-5 py-3">User</th>
                <th className="px-5 py-3">Organization</th>
                <th className="px-5 py-3">Role</th>
                <th className="px-5 py-3">Status</th>
                <th className="px-5 py-3">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {users.map((user) => (
                <tr key={user.id} className="text-slate-700">
                  <td className="px-5 py-3">
                    <p className="font-medium text-slate-900">{user.fullName}</p>
                    <p className="text-xs text-slate-500">{user.email}</p>
                  </td>
                  <td className="px-5 py-3">{user.organizationName}</td>
                  <td className="px-5 py-3">
                    <select
                      aria-label={`Role for ${user.email}`}
                      value={user.roles[0] ?? 'TENANT'}
                      onChange={(event) => {
                        if (isOrganizationRole(event.target.value)) {
                          void updateRole(user.id, event.target.value);
                        }
                      }}
                      className="rounded-lg border border-slate-200 bg-white px-2 py-1"
                    >
                      {ORGANIZATION_ROLES.map((role) => <option key={role} value={role}>{role}</option>)}
                    </select>
                  </td>
                  <td className="px-5 py-3">
                    <span className={user.active ? 'text-green-700' : 'text-slate-500'}>
                      {user.active ? 'Active' : 'Disabled'}
                    </span>
                  </td>
                  <td className="px-5 py-3">
                    <button
                      type="button"
                      disabled={user.platformAdmin}
                      onClick={() => void updateActive(user)}
                      className="rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-medium disabled:cursor-not-allowed disabled:opacity-40"
                    >
                      {user.active ? 'Disable' : 'Enable'}
                    </button>
                  </td>
                </tr>
              ))}
              {!loadingUsers && users.length === 0 && (
                <tr><td colSpan={5} className="px-5 py-8 text-center text-slate-500">No user accounts found.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
};
