import type { CircleMember, User } from '$types';
import { writable } from 'svelte/store';

interface AuthState {
	user: User | null;
	circleMember: CircleMember | null;
	isAuthenticated: boolean;
	isLoading: boolean;
	error: string | null;
}

const initialState: AuthState = {
	user: null,
	circleMember: null,
	isAuthenticated: false,
	isLoading: false,
	error: null
};

function createAuthStore() {
	const { subscribe, set, update } = writable<AuthState>(initialState);

	return {
		subscribe,
		login: async (username: string, password: string) => {
			update(state => ({ ...state, isLoading: true, error: null }));

			try {
				// TODO: Implement actual login logic
				// const response = await fetch('/api/auth/login', {
				//   method: 'POST',
				//   headers: { 'Content-Type': 'application/json' },
				//   body: JSON.stringify({ username, password })
				// });

				// Mock user for now
				const mockUser: User = {
					id: '1',
					username: 'admin',
					email: 'admin@abservice.com',
					firstName: 'System',
					lastName: 'Administrator',
					isActive: true,
					isEmailVerified: true,
					roles: [],
					createdAt: new Date().toISOString(),
					updatedAt: new Date().toISOString()
				};

				update(state => ({
					...state,
					user: mockUser,
					isAuthenticated: true,
					isLoading: false
				}));
			} catch (error) {
				update(state => ({
					...state,
					error: error instanceof Error ? error.message : 'Login failed',
					isLoading: false
				}));
			}
		},
		logout: async () => {
			update(state => ({ ...state, isLoading: true }));

			try {
				// TODO: Implement actual logout logic
				// await fetch('/api/auth/logout', { method: 'POST' });

				set(initialState);
			} catch (error) {
				update(state => ({
					...state,
					error: error instanceof Error ? error.message : 'Logout failed',
					isLoading: false
				}));
			}
		},
		checkAuth: async () => {
			update(state => ({ ...state, isLoading: true }));

			try {
				// TODO: Implement actual auth check
				// const response = await fetch('/api/auth/me');
				// const user = await response.json();

				// For now, assume not authenticated
				set(initialState);
			} catch (error) {
				set(initialState);
			}
		},
		clearError: () => {
			update(state => ({ ...state, error: null }));
		}
	};
}

export const authStore = createAuthStore();
