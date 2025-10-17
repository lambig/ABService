// Common types for the admin frontend

export interface User {
	id: string;
	username: string;
	email: string;
	firstName?: string;
	lastName?: string;
	isActive: boolean;
	isEmailVerified: boolean;
	roles: Role[];
	createdAt: string;
	updatedAt: string;
	lastLoginAt?: string;
}

export interface Role {
	id: string;
	name: string;
	description?: string;
	isActive: boolean;
	permissions: Permission[];
	createdAt: string;
	updatedAt: string;
}

export interface Permission {
	id: string;
	name: string;
	description?: string;
	resource: string;
	action: string;
	isActive: boolean;
	createdAt: string;
	updatedAt: string;
}

export interface ApiResponse<T> {
	data: T;
	message?: string;
	success: boolean;
}

export interface PaginatedResponse<T> {
	data: T[];
	total: number;
	page: number;
	limit: number;
	totalPages: number;
}

export interface ApiError {
	message: string;
	code?: string;
	details?: Record<string, unknown>;
}

// Form types
export interface CreateUserRequest {
	username: string;
	email: string;
	password: string;
	firstName?: string;
	lastName?: string;
	roleIds: string[];
}

export interface UpdateUserRequest {
	username?: string;
	email?: string;
	firstName?: string;
	lastName?: string;
	isActive?: boolean;
	roleIds?: string[];
}

export interface CreateRoleRequest {
	name: string;
	description?: string;
	permissionIds: string[];
}

export interface UpdateRoleRequest {
	name?: string;
	description?: string;
	isActive?: boolean;
	permissionIds?: string[];
}

// UI State types
export interface LoadingState {
	isLoading: boolean;
	error?: string;
}

export interface TableState {
	page: number;
	limit: number;
	sortBy?: string;
	sortOrder: 'asc' | 'desc';
	filters: Record<string, unknown>;
}

// Navigation types
export interface NavItem {
	label: string;
	href: string;
	icon?: string;
	children?: NavItem[];
	permissions?: string[];
}
