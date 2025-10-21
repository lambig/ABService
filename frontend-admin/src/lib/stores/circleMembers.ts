import { apiClient } from '$lib/utils/api';
import type { CircleMember, CreateCircleMemberDto, UpdateCircleMemberDto } from '$types';
import { writable } from 'svelte/store';

interface CircleMembersState {
	members: CircleMember[];
	isLoading: boolean;
	error: string | null;
}

const initialState: CircleMembersState = {
	members: [],
	isLoading: false,
	error: null
};

function createCircleMembersStore() {
	const { subscribe, set, update } = writable<CircleMembersState>(initialState);

	return {
		subscribe,

		// Load all circle members
		loadMembers: async () => {
			update(state => ({ ...state, isLoading: true, error: null }));

			try {
				const members = await apiClient.getCircleMembers();
				update(state => ({ ...state, members, isLoading: false }));
			} catch (error) {
				update(state => ({
					...state,
					isLoading: false,
					error: error instanceof Error ? error.message : 'Failed to load members'
				}));
			}
		},

		// Load active circle members only
		loadActiveMembers: async () => {
			update(state => ({ ...state, isLoading: true, error: null }));

			try {
				const members = await apiClient.getActiveCircleMembers();
				update(state => ({ ...state, members, isLoading: false }));
			} catch (error) {
				update(state => ({
					...state,
					isLoading: false,
					error: error instanceof Error ? error.message : 'Failed to load active members'
				}));
			}
		},

		// Get member by ID
		getMember: async (id: number): Promise<CircleMember | null> => {
			try {
				return await apiClient.getCircleMember(id);
			} catch (error) {
				update(state => ({
					...state,
					error: error instanceof Error ? error.message : 'Failed to get member'
				}));
				return null;
			}
		},

		// Create new member
		createMember: async (data: CreateCircleMemberDto) => {
			update(state => ({ ...state, isLoading: true, error: null }));

			try {
				const newMember = await apiClient.createCircleMember(data);
				update(state => ({
					...state,
					members: [...state.members, newMember],
					isLoading: false
				}));
				return newMember;
			} catch (error) {
				update(state => ({
					...state,
					isLoading: false,
					error: error instanceof Error ? error.message : 'Failed to create member'
				}));
				throw error;
			}
		},

		// Update member
		updateMember: async (id: number, data: UpdateCircleMemberDto) => {
			update(state => ({ ...state, isLoading: true, error: null }));

			try {
				const updatedMember = await apiClient.updateCircleMember(id, data);
				update(state => ({
					...state,
					members: state.members.map(member =>
						member.id === id ? updatedMember : member
					),
					isLoading: false
				}));
				return updatedMember;
			} catch (error) {
				update(state => ({
					...state,
					isLoading: false,
					error: error instanceof Error ? error.message : 'Failed to update member'
				}));
				throw error;
			}
		},

		// Delete member
		deleteMember: async (id: number) => {
			update(state => ({ ...state, isLoading: true, error: null }));

			try {
				await apiClient.deleteCircleMember(id);
				update(state => ({
					...state,
					members: state.members.filter(member => member.id !== id),
					isLoading: false
				}));
			} catch (error) {
				update(state => ({
					...state,
					isLoading: false,
					error: error instanceof Error ? error.message : 'Failed to delete member'
				}));
				throw error;
			}
		},

		// Clear error
		clearError: () => {
			update(state => ({ ...state, error: null }));
		},

		// Reset store
		reset: () => {
			set(initialState);
		}
	};
}

export const circleMembersStore = createCircleMembersStore();


