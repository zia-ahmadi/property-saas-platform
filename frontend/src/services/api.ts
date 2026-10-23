import axios, { AxiosError } from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';
const GRAPHQL_URL = import.meta.env.VITE_GRAPHQL_URL || 'http://localhost:8080/graphql';

export interface UserProfile {
  id: string;
  email: string;
  fullName: string;
  organizationId: string;
  roles: string[];
}

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
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
  totalUnits: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface Unit {
  id: string;
  propertyId: string;
  unitNumber: string;
  floor: number;
  status: 'VACANT' | 'OCCUPIED' | 'MAINTENANCE';
  rentAmount: number;
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
  status: 'PENDING' | 'ACTIVE' | 'TERMINATED' | 'EXPIRED';
  version: number;
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
  paymentDate: string;
  paymentMethod: string;
  referenceNumber: string;
}

export interface MaintenanceRequest {
  id: string;
  unitId: string;
  vendorId?: string;
  description: string;
  priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'EMERGENCY';
  status: 'SUBMITTED' | 'ASSIGNED' | 'IN_PROGRESS' | 'RESOLVED';
  version: number;
  createdAt?: string;
}

export interface Vendor {
  id: string;
  name: string;
  specialty: string;
  phone: string;
  email: string;
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
  (error: AxiosError<{ message?: string; detail?: string; title?: string }>) => {
    const errorMsg =
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
    adminEmail: string;
    adminPassword: string;
    adminFullName: string;
  }): Promise<TokenResponse> => {
    const res = await apiClient.post<TokenResponse>('/auth/register', data);
    return res.data;
  },
  getCurrentUser: async (): Promise<UserProfile> => {
    const res = await apiClient.get<UserProfile>('/auth/me');
    return res.data;
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
  create: async (data: { name: string; address: string; totalUnits: number }): Promise<Property> => {
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
  listUnits: async (propertyId: string): Promise<Unit[]> => {
    const res = await apiClient.get<Unit[]>(`/properties/${propertyId}/units`);
    return res.data;
  },
  createUnit: async (propertyId: string, data: { unitNumber: string; floor: number; rentAmount: number }): Promise<Unit> => {
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
  update: async (id: string, data: { status?: 'VACANT' | 'OCCUPIED' | 'MAINTENANCE'; rentAmount?: number }): Promise<Unit> => {
    const res = await apiClient.patch<Unit>(`/units/${id}`, data);
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
      headers: { 'If-Match': String(version) },
    });
    return res.data;
  },
  terminate: async (id: string, version: number): Promise<Lease> => {
    const res = await apiClient.post<Lease>(`/leases/${id}/terminate`, null, {
      headers: { 'If-Match': String(version) },
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
    const res = await apiClient.get<Page<MaintenanceRequest>>('/maintenance', { params: { page, size } });
    return res.data;
  },
  create: async (data: { unitId: string; description: string; priority: string }): Promise<MaintenanceRequest> => {
    const res = await apiClient.post<MaintenanceRequest>('/maintenance', data);
    return res.data;
  },
  assign: async (id: string, vendorId: string, version: number): Promise<MaintenanceRequest> => {
    const res = await apiClient.post<MaintenanceRequest>(`/maintenance/${id}/assign`, { vendorId }, {
      headers: { 'If-Match': String(version) },
    });
    return res.data;
  },
  resolve: async (id: string, version: number): Promise<MaintenanceRequest> => {
    const res = await apiClient.post<MaintenanceRequest>(`/maintenance/${id}/resolve`, null, {
      headers: { 'If-Match': String(version) },
    });
    return res.data;
  },
};

export const vendorApi = {
  list: async (): Promise<Vendor[]> => {
    const res = await apiClient.get<Vendor[]>('/vendors');
    return res.data;
  },
  create: async (data: { name: string; specialty: string; phone: string; email: string }): Promise<Vendor> => {
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
