<script lang="ts">
	import { onMount } from 'svelte';
	import { circleMembersStore } from '$lib/stores/circleMembers';
	import type { CircleMember } from '$types';

	let members: CircleMember[] = [];
	let isLoading = false;
	let error: string | null = null;

	// Subscribe to store
	$: {
		members = $circleMembersStore.members;
		isLoading = $circleMembersStore.isLoading;
		error = $circleMembersStore.error;
	}

	onMount(() => {
		circleMembersStore.loadMembers();
	});

	function handleRefresh() {
		circleMembersStore.loadMembers();
	}

	function handleDelete(id: number) {
		if (confirm('このメンバーを削除しますか？')) {
			circleMembersStore.deleteMember(id);
		}
	}
</script>

<svelte:head>
	<title>Circle Members - ABService Admin</title>
</svelte:head>

<div class="container mx-auto px-4 py-8">
	<div class="flex justify-between items-center mb-6">
		<h1 class="text-3xl font-bold text-gray-900">Circle Members</h1>
		<button
			on:click={handleRefresh}
			disabled={isLoading}
			class="bg-blue-600 hover:bg-blue-700 disabled:bg-blue-400 text-white px-4 py-2 rounded-lg transition-colors"
		>
			{isLoading ? 'Loading...' : 'Refresh'}
		</button>
	</div>

	{#if error}
		<div class="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded mb-4">
			{error}
		</div>
	{/if}

	{#if isLoading && members.length === 0}
		<div class="flex justify-center items-center py-12">
			<div class="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
			<span class="ml-2 text-gray-600">Loading members...</span>
		</div>
	{:else if members.length === 0}
		<div class="text-center py-12">
			<p class="text-gray-500 text-lg">No circle members found.</p>
		</div>
	{:else}
		<div class="bg-white shadow overflow-hidden sm:rounded-md">
			<ul class="divide-y divide-gray-200">
				{#each members as member (member.id)}
					<li class="px-6 py-4">
						<div class="flex items-center justify-between">
							<div class="flex items-center">
								{#if member.avatarUrl}
									<img
										class="h-10 w-10 rounded-full"
										src={member.avatarUrl}
										alt={member.displayName}
									/>
								{:else}
									<div class="h-10 w-10 rounded-full bg-gray-300 flex items-center justify-center">
										<span class="text-gray-600 font-medium">
											{member.displayName.charAt(0).toUpperCase()}
										</span>
									</div>
								{/if}
								<div class="ml-4">
									<div class="flex items-center">
										<p class="text-sm font-medium text-gray-900">
											{member.displayName}
										</p>
										{#if member.isActive}
											<span class="ml-2 inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-800">
												Active
											</span>
										{:else}
											<span class="ml-2 inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-red-100 text-red-800">
												Inactive
											</span>
										{/if}
									</div>
									<p class="text-sm text-gray-500">@{member.username}</p>
									<p class="text-sm text-gray-500">{member.email}</p>
									{#if member.bio}
										<p class="text-sm text-gray-600 mt-1">{member.bio}</p>
									{/if}
									<div class="flex items-center mt-1">
										<span class="text-xs text-gray-400">Role: {member.roleName}</span>
									</div>
								</div>
							</div>
							<div class="flex items-center space-x-2">
								<a
									href="/circle-members/{member.id}"
									class="text-blue-600 hover:text-blue-900 text-sm font-medium"
								>
									View
								</a>
								<a
									href="/circle-members/{member.id}/edit"
									class="text-indigo-600 hover:text-indigo-900 text-sm font-medium"
								>
									Edit
								</a>
								<button
									on:click={() => handleDelete(member.id)}
									class="text-red-600 hover:text-red-900 text-sm font-medium"
								>
									Delete
								</button>
							</div>
						</div>
					</li>
				{/each}
			</ul>
		</div>
	{/if}
</div>

