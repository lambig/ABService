import type { ApiResponse, ApiError } from '$types';

const API_BASE_URL = '/api/v1';

class ApiClient {
	private baseUrl: string;

	constructor(baseUrl: string = API_BASE_URL) {
		this.baseUrl = baseUrl;
	}

	private async request<T>(
		endpoint: string,
		options: RequestInit = {}
	): Promise<ApiResponse<T>> {
		const url = `${this.baseUrl}${endpoint}`;
		
		const defaultHeaders = {
			'Content-Type': 'application/json',
		};

		const config: RequestInit = {
			...options,
			headers: {
				...defaultHeaders,
				...options.headers,
			},
		};

		try {
			const response = await fetch(url, config);
			
			if (!response.ok) {
				const errorData: ApiError = await response.json().catch(() => ({
					message: `HTTP ${response.status}: ${response.statusText}`,
					code: response.status.toString()
				}));
				throw new Error(errorData.message);
			}

			const data: ApiResponse<T> = await response.json();
			return data;
		} catch (error) {
			if (error instanceof Error) {
				throw error;
			}
			throw new Error('An unexpected error occurred');
		}
	}

	async get<T>(endpoint: string, options?: RequestInit): Promise<ApiResponse<T>> {
		return this.request<T>(endpoint, { ...options, method: 'GET' });
	}

	async post<T>(endpoint: string, data?: unknown, options?: RequestInit): Promise<ApiResponse<T>> {
		return this.request<T>(endpoint, {
			...options,
			method: 'POST',
			body: data ? JSON.stringify(data) : undefined,
		});
	}

	async put<T>(endpoint: string, data?: unknown, options?: RequestInit): Promise<ApiResponse<T>> {
		return this.request<T>(endpoint, {
			...options,
			method: 'PUT',
			body: data ? JSON.stringify(data) : undefined,
		});
	}

	async patch<T>(endpoint: string, data?: unknown, options?: RequestInit): Promise<ApiResponse<T>> {
		return this.request<T>(endpoint, {
			...options,
			method: 'PATCH',
			body: data ? JSON.stringify(data) : undefined,
		});
	}

	async delete<T>(endpoint: string, options?: RequestInit): Promise<ApiResponse<T>> {
		return this.request<T>(endpoint, { ...options, method: 'DELETE' });
	}
}

export const apiClient = new ApiClient();

// Helper functions for common API operations
export const api = {
	// Auth endpoints
	auth: {
		login: (credentials: { username: string; password: string }) =>
			apiClient.post('/auth/login', credentials),
		logout: () => apiClient.post('/auth/logout'),
		me: () => apiClient.get('/auth/me'),
		refresh: () => apiClient.post('/auth/refresh'),
	},

	// User endpoints
	users: {
		list: (params?: Record<string, unknown>) => {
			const searchParams = new URLSearchParams();
			if (params) {
				Object.entries(params).forEach(([key, value]) => {
					if (value !== undefined && value !== null) {
						searchParams.append(key, String(value));
					}
				});
			}
			const query = searchParams.toString();
			return apiClient.get(`/users${query ? `?${query}` : ''}`);
		},
		get: (id: string) => apiClient.get(`/users/${id}`),
		create: (data: unknown) => apiClient.post('/users', data),
		update: (id: string, data: unknown) => apiClient.put(`/users/${id}`, data),
		delete: (id: string) => apiClient.delete(`/users/${id}`),
	},

	// Role endpoints
	roles: {
		list: () => apiClient.get('/roles'),
		get: (id: string) => apiClient.get(`/roles/${id}`),
		create: (data: unknown) => apiClient.post('/roles', data),
		update: (id: string, data: unknown) => apiClient.put(`/roles/${id}`, data),
		delete: (id: string) => apiClient.delete(`/roles/${id}`),
	},

	// Permission endpoints
	permissions: {
		list: () => apiClient.get('/permissions'),
		get: (id: string) => apiClient.get(`/permissions/${id}`),
	},

	// System endpoints
	system: {
		health: () => apiClient.get('/system/health'),
		status: () => apiClient.get('/system/status'),
		config: () => apiClient.get('/system/config'),
	},
};
