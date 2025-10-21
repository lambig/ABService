import type { ApiError, ApiResponse, CircleMember, Role } from '@types';

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
}

export const apiClient = new ApiClient();

// Helper functions for common API operations
export const api = {
	// Contact endpoints
	contact: {
		submit: (data: { name: string; email: string; subject: string; message: string }) =>
			apiClient.post('/contact', data),
	},

	// Newsletter endpoints
	newsletter: {
		subscribe: (email: string) =>
			apiClient.post('/newsletter/subscribe', { email }),
		unsubscribe: (email: string) =>
			apiClient.post('/newsletter/unsubscribe', { email }),
	},

	// Public content endpoints
	content: {
		features: () => apiClient.get('/content/features'),
		pricing: () => apiClient.get('/content/pricing'),
		testimonials: () => apiClient.get('/content/testimonials'),
		faqs: () => apiClient.get('/content/faqs'),
		blog: (params?: { page?: number; limit?: number; tag?: string }) => {
			const searchParams = new URLSearchParams();
			if (params) {
				Object.entries(params).forEach(([key, value]) => {
					if (value !== undefined && value !== null) {
						searchParams.append(key, String(value));
					}
				});
			}
			const query = searchParams.toString();
			return apiClient.get(`/content/blog${query ? `?${query}` : ''}`);
		},
		blogPost: (slug: string) => apiClient.get(`/content/blog/${slug}`),
	},

	// System endpoints
	system: {
		health: () => apiClient.get('/system/health'),
		status: () => apiClient.get('/system/status'),
	},

	// CircleMember API methods (read-only for public)
	async getActiveCircleMembers(): Promise<CircleMember[]> {
		const response = await this.request<CircleMember[]>('/circle-members/active');
		return response.data;
	},

	async getCircleMember(id: number): Promise<CircleMember> {
		const response = await this.request<CircleMember>(`/circle-members/${id}`);
		return response.data;
	},

	async getCircleMemberByUsername(username: string): Promise<CircleMember> {
		const response = await this.request<CircleMember>(`/circle-members/username/${username}`);
		return response.data;
	},

	// Role API methods (read-only for public)
	async getRoles(): Promise<Role[]> {
		const response = await this.request<Role[]>('/roles');
		return response.data;
	},

	async getRole(id: number): Promise<Role> {
		const response = await this.request<Role>(`/roles/${id}`);
		return response.data;
	}
};
