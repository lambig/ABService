import { describe, it, expect, vi, beforeEach } from 'vitest';
import { apiClient } from './api';

// Mock fetch
const mockFetch = vi.fn();
global.fetch = mockFetch;

describe('API Client', () => {
	beforeEach(() => {
		vi.clearAllMocks();
	});

	it('should make GET requests', async () => {
		const mockResponse = {
			ok: true,
			json: () => Promise.resolve({ data: [{ id: 1, name: 'Test' }] })
		};
		mockFetch.mockResolvedValueOnce(mockResponse);

		const result = await apiClient.get('/test');

		expect(mockFetch).toHaveBeenCalledWith('/api/v1/test', {
			method: 'GET',
			headers: {
				'Content-Type': 'application/json'
			}
		});
		expect(result).toEqual({ data: [{ id: 1, name: 'Test' }] });
	});

	it('should make POST requests', async () => {
		const mockResponse = {
			ok: true,
			json: () => Promise.resolve({ data: { id: 1, name: 'Created' } })
		};
		mockFetch.mockResolvedValueOnce(mockResponse);

		const data = { name: 'Test' };
		const result = await apiClient.post('/test', data);

		expect(mockFetch).toHaveBeenCalledWith('/api/v1/test', {
			method: 'POST',
			headers: {
				'Content-Type': 'application/json'
			},
			body: JSON.stringify(data)
		});
		expect(result).toEqual({ data: { id: 1, name: 'Created' } });
	});

	it('should handle errors', async () => {
		const mockResponse = {
			ok: false,
			status: 404,
			json: () => Promise.resolve({ error: 'Not found' })
		};
		mockFetch.mockResolvedValueOnce(mockResponse);

		await expect(apiClient.get('/test')).rejects.toThrow('API request failed');
	});
});








