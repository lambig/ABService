<script lang="ts">
	import { onMount } from 'svelte';

	let mounted = false;

	onMount(() => {
		mounted = true;
	});
</script>

<svelte:head>
	<title>ABService Admin Dashboard</title>
	<meta name="description" content="ABService Administration Dashboard" />
</svelte:head>

<div class="min-h-screen bg-gray-50">
	<div class="container mx-auto px-4 py-8">
		<div class="max-w-4xl mx-auto">
			<div class="text-center mb-8">
				<h1 class="text-4xl font-bold text-gray-900 mb-4">
					ABService Admin Dashboard
				</h1>
				<p class="text-lg text-gray-600">
					Welcome to the ABService administration interface
				</p>
			</div>

			{#if mounted}
				<div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
					<div class="card p-6">
						<h3 class="text-lg font-semibold text-gray-900 mb-2">System Status</h3>
						<p class="text-gray-600">Monitor system health and performance</p>
					</div>

					<div class="card p-6">
						<h3 class="text-lg font-semibold text-gray-900 mb-2">User Management</h3>
						<p class="text-gray-600">Manage users and permissions</p>
					</div>

					<div class="card p-6">
						<h3 class="text-lg font-semibold text-gray-900 mb-2">Configuration</h3>
						<p class="text-gray-600">System configuration and settings</p>
					</div>
				</div>
			{/if}
		</div>
	</div>
</div>
