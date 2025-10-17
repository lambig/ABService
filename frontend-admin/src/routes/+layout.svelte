<script lang="ts">
	import '../app.css';
	import { onMount } from 'svelte';
	import { page } from '$app/stores';

	// Initialize safe-chain security
	onMount(async () => {
		try {
			// Import and initialize safe-chain
			const { default: safeChain } = await import('safe-chain');
			await safeChain.init();
			console.log('Safe-chain security initialized');
		} catch (error) {
			console.warn('Safe-chain initialization failed:', error);
		}
	});
</script>

<main class="min-h-screen bg-gray-50">
	<slot />
</main>
