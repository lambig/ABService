import { defineConfig } from 'astro/config';
import svelte from '@astrojs/svelte';
import tailwind from '@astrojs/tailwind';

// https://astro.build/config
export default defineConfig({
	integrations: [
		svelte(),
		tailwind({
			applyBaseStyles: false,
		}),
	],
	output: 'static',
	site: 'https://abservice.com',
	base: '/',
	server: {
		port: 4321,
		host: true,
	},
	preview: {
		port: 4321,
		host: true,
	},
	vite: {
		server: {
			proxy: {
				'/api': {
					target: 'http://localhost:8080',
					changeOrigin: true,
					secure: false,
				},
			},
		},
	},
});
