import axios, { AxiosError } from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';
const GRAPHQL_URL = import.meta.env.VITE_GRAPHQL_URL || 'http://localhost:8080/graphql';

export interface UserProfile {
  userId: string;
  tenantId: string;
  email: string;
  fullName: string;
  roles: string[];
}

export interface PlatformOrganization {
  id: string;
  name: string;
  createdAt: string;
  userCount: number;
  activeUserCount: number;
}

export interface PlatformUser {
  id: string;
  organizationId: string;
  organizationName: string;
  email: string;
  fullName: string;
  active: boolean;
  roles: string[];
  platformAdmin: boolean;
}

export interface TokenResponse {
  accessToken: string;
  expiresIn: number;
  userId: string;
  tenantId: string;
  roles: string[];
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface Property {
  id: string;
  name: string;
  address: string;
  status: string;
  unitCount: number;
  occupiedUnits: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface Unit {
  id: string;
  propertyId: string;
  name: string;
  status: 'AVAILABLE' | 'OCCUPIED' | 'MAINTENANCE';
  bedrooms: number | null;
  bathrooms: number | null;
  monthlyRent: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface Lease {
  id: string;
  unitId: string;
  renterId: string;
  startDate: string;
  endDate: string;
  monthlyRent: number;
  securityDeposit: number;
  status: 'DRAFT' | 'ACTIVE' | 'TERMINATED' | 'EXPIRED';
  version: number;
}

export interface Renter {
  id: string;
  fullName: string;
  email: string;
  phone: string | null;
  createdAt?: string;
  updatedAt?: string;
}

export interface Invoice {
  id: string;
  leaseId: string;
  amount: number;
  dueDate: string;
  status: 'ISSUED' | 'PAID' | 'OVERDUE' | 'CANCELLED';
  version: number;
}

export interface Payment {
  id: string;
  invoiceId: string;
  amount: number;
  paymentMethod: string;
  status: string;
  createdAt?: string;
}

export interface MaintenanceRequest {
  id: string;
  unitId: string;
  renterId: string;
  vendorId?: string | null;
  description: string;
  priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'EMERGENCY';
  status: 'SUBMITTED' | 'ASSIGNED' | 'IN_PROGRESS' | 'RESOLVED';
  version: number;
  requestedDate?: string;
  createdAt?: string;
}

export interface Vendor {
  id: string;
  name: string;
  serviceType: string;
  phone: string | null;
  email: string | null;
  active: boolean;
}

export interface PortfolioDashboard {
  tenantId: string;
  totalProperties: number;
  totalUnits: number;
  occupiedUnits: number;
  occupancyRate: number;
  totalOutstandingAmount: number;
  overdueInvoiceCount: number;
  activeMaintenanceRequests: MaintenanceAlert[];
  generatedAt: string;
}

export interface MaintenanceAlert {
  id: string;
  priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'EMERGENCY';
  status: 'SUBMITTED' | 'ASSIGNED' | 'IN_PROGRESS' | 'RESOLVED';
  unitName: string;
  propertyName: string;
  description: string;
  requestedDate: string;
}

// Axios instance
export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('jwt_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError<{
    message?: string;
    detail?: string;
    title?: string;
    errors?: Array<{ field?: string; message: string }>;
  }>) => {
    const errorMsg =
      error.response?.data?.errors?.map(({ field, message }) =>
        field ? `${field}: ${message}` : message
      ).join(', ') ||
      error.response?.data?.detail ||
      error.response?.data?.message ||
      error.response?.data?.title ||
      error.message ||
      'An unexpected error occurred';
    return Promise.reject(new Error(errorMsg));
  }
);

// Auth API
export const authApi = {
  login: async (credentials: { email: string; password: string }): Promise<TokenResponse> => {
    const res = await apiClient.post<TokenResponse>('/auth/login', credentials);
    return res.data;
  },
  register: async (data: {
    organizationName: string;
    email: string;
    password: string;
    fullName: string;
  }): Promise<TokenResponse> => {
    const res = await apiClient.post<TokenResponse>('/auth/register', data);
    return res.data;
  },
  getCurrentUser: async (): Promise<UserProfile> => {
    const res = await apiClient.get<UserProfile>('/auth/me');
    return res.data;
  },
};

export const platformAdminApi = {
  listOrganizations: async (page = 0, size = 100): Promise<Page<PlatformOrganization>> => {
    const res = await apiClient.get<Page<PlatformOrganization>>('/platform-admin/organizations', {
      params: { page, size },
    });
    return res.data;
  },
  renameOrganization: async (organizationId: string, name: string): Promise<void> => {
    await apiClient.patch(`/platform-admin/organizations/${organizationId}`, { name });
  },
  listUsers: async (organizationId?: string, page = 0, size = 100): Promise<Page<PlatformUser>> => {
    const res = await apiClient.get<Page<PlatformUser>>('/platform-admin/users', {
      params: { organizationId, page, size },
    });
    return res.data;
  },
  setUserActive: async (userId: string, active: boolean): Promise<void> => {
    await apiClient.patch(`/platform-admin/users/${userId}/active`, { active });
  },
  setOrganizationRole: async (userId: string, role: 'ADMIN' | 'MANAGER' | 'TENANT'): Promise<void> => {
    await apiClient.put(`/platform-admin/users/${userId}/role`, { role });
  },
};

// Properties API
export const propertyApi = {
  list: async (page = 0, size = 10, status?: string): Promise<Page<Property>> => {
    const res = await apiClient.get<Page<Property>>('/properties', {
      params: { page, size, status },
    });
    return res.data;
  },
  get: async (id: string): Promise<Property> => {
    const res = await apiClient.get<Property>(`/properties/${id}`);
    return res.data;
  },
  create: async (data: { name: string; address: string }): Promise<Property> => {
    const res = await apiClient.post<Property>('/properties', data);
    return res.data;
  },
  update: async (id: string, data: { name?: string; address?: string }): Promise<Property> => {
    const res = await apiClient.patch<Property>(`/properties/${id}`, data);
    return res.data;
  },
  delete: async (id: string): Promise<void> => {
    await apiClient.delete(`/properties/${id}`);
  },
  listUnits: async (propertyId: string, page = 0, size = 100): Promise<Page<Unit>> => {
    const res = await apiClient.get<Page<Unit>>(`/properties/${propertyId}/units`, {
      params: { page, size },
    });
    return res.data;
  },
  createUnit: async (propertyId: string, data: { name: string; monthlyRent: number }): Promise<Unit> => {
    const res = await apiClient.post<Unit>(`/properties/${propertyId}/units`, data);
    return res.data;
  },
};

// Units API
export const unitApi = {
  get: async (id: string): Promise<Unit> => {
    const res = await apiClient.get<Unit>(`/units/${id}`);
    return res.data;
  },
  update: async (id: string, data: { status?: Unit['status']; monthlyRent?: number }): Promise<Unit> => {
    const res = await apiClient.patch<Unit>(`/units/${id}`, data);
    return res.data;
  },
};

// Renters (tenant contacts) API
export const renterApi = {
  list: async (page = 0, size = 100, q?: string): Promise<Page<Renter>> => {
    const res = await apiClient.get<Page<Renter>>('/tenants', { params: { page, size, q } });
    return res.data;
  },
  create: async (data: { fullName: string; email: string; phone?: string }): Promise<Renter> => {
    const res = await apiClient.post<Renter>('/tenants', data);
    return res.data;
  },
};

// Leases API
export const leaseApi = {
  list: async (page = 0, size = 10): Promise<Page<Lease>> => {
    const res = await apiClient.get<Page<Lease>>('/leases', { params: { page, size } });
    return res.data;
  },
  create: async (data: { unitId: string; renterId: string; startDate: string; endDate: string; monthlyRent: number; securityDeposit: number }): Promise<Lease> => {
    const res = await apiClient.post<Lease>('/leases', data);
    return res.data;
  },
  activate: async (id: string, version: number): Promise<Lease> => {
    const res = await apiClient.post<Lease>(`/leases/${id}/activate`, null, {
      headers: { 'If-Match': `"${version}"` },
    });
    return res.data;
  },
  terminate: async (id: string, version: number): Promise<Lease> => {
    const res = await apiClient.post<Lease>(`/leases/${id}/terminate`, null, {
      headers: { 'If-Match': `"${version}"` },
    });
    return res.data;
  },
};

// Billing & Invoices API
export const invoiceApi = {
  list: async (page = 0, size = 10): Promise<Page<Invoice>> => {
    const res = await apiClient.get<Page<Invoice>>('/invoices', { params: { page, size } });
    return res.data;
  },
  create: async (data: { leaseId: string; amount: number; dueDate: string }): Promise<Invoice> => {
    const res = await apiClient.post<Invoice>('/invoices', data);
    return res.data;
  },
};

// Payments API
export const paymentApi = {
  record: async (data: { invoiceId: string; amount: number; paymentMethod: string; referenceNumber: string }, idempotencyKey: string): Promise<Payment> => {
    const res = await apiClient.post<Payment>('/payments', data, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
    return res.data;
  },
};

// Maintenance & Vendors API
export const maintenanceApi = {
  list: async (page = 0, size = 10): Promise<Page<MaintenanceRequest>> => {
    const res = await apiClient.get<Page<MaintenanceRequest>>('/maintenance-requests', { params: { page, size } });
    return res.data;
  },
  create: async (data: { unitId: string; renterId: string; description: string; priority: string }): Promise<MaintenanceRequest> => {
    const res = await apiClient.post<MaintenanceRequest>('/maintenance-requests', data);
    return res.data;
  },
  assign: async (id: string, vendorId: string, version: number): Promise<MaintenanceRequest> => {
    const res = await apiClient.patch<MaintenanceRequest>(`/maintenance-requests/${id}/assign`, { vendorId }, {
      headers: { 'If-Match': `"${version}"` },
    });
    return res.data;
  },
  start: async (id: string, version: number): Promise<MaintenanceRequest> => {
    const res = await apiClient.post<MaintenanceRequest>(`/maintenance-requests/${id}/start`, null, {
      headers: { 'If-Match': `"${version}"` },
    });
    return res.data;
  },
  resolve: async (id: string, version: number, resolutionNotes: string): Promise<MaintenanceRequest> => {
    const res = await apiClient.post<MaintenanceRequest>(`/maintenance-requests/${id}/resolve`, { resolutionNotes }, {
      headers: { 'If-Match': `"${version}"` },
    });
    return res.data;
  },
};

export const vendorApi = {
  list: async (page = 0, size = 100): Promise<Page<Vendor>> => {
    const res = await apiClient.get<Page<Vendor>>('/vendors', { params: { page, size } });
    return res.data;
  },
  create: async (data: { name: string; serviceType: string; phone: string; email: string }): Promise<Vendor> => {
    const res = await apiClient.post<Vendor>('/vendors', data);
    return res.data;
  },
};

// GraphQL Dashboard
export const graphqlApi = {
  getDashboard: async (): Promise<PortfolioDashboard> => {
    const query = `
      query {
        portfolioDashboard {
          tenantId
          totalProperties
          totalUnits
          occupiedUnits
          occupancyRate
          totalOutstandingAmount
          overdueInvoiceCount
          generatedAt
          activeMaintenanceRequests {
            id
            priority
            status
            unitName
            propertyName
            description
            requestedDate
          }
        }
      }
    `;
    const res = await axios.post(
      GRAPHQL_URL,
      { query },
      {
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${localStorage.getItem('jwt_token') || ''}`,
        },
      }
    );
    if (res.data.errors) {
      throw new Error(res.data.errors[0].message);
    }
    return res.data.data.portfolioDashboard;
  },
};
