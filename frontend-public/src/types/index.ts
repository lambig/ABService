// Common types for the public frontend

export interface ContactForm {
	name: string;
	email: string;
	subject: string;
	message: string;
}

export interface NewsletterSubscription {
	email: string;
}

export interface ApiResponse<T> {
	data: T;
	message?: string;
	success: boolean;
}

export interface ApiError {
	message: string;
	code?: string;
	details?: Record<string, unknown>;
}

// Feature types
export interface Feature {
	id: string;
	title: string;
	description: string;
	icon: string;
	benefits: string[];
}

export interface PricingPlan {
	id: string;
	name: string;
	description: string;
	price: number;
	currency: string;
	period: 'month' | 'year';
	features: string[];
	isPopular?: boolean;
	ctaText: string;
	ctaLink: string;
}

// Blog types
export interface BlogPost {
	id: string;
	title: string;
	excerpt: string;
	content: string;
	author: string;
	publishedAt: string;
	updatedAt: string;
	tags: string[];
	slug: string;
	featuredImage?: string;
}

// FAQ types
export interface FAQ {
	id: string;
	question: string;
	answer: string;
	category: string;
	order: number;
}

// Testimonial types
export interface Testimonial {
	id: string;
	name: string;
	company: string;
	role: string;
	content: string;
	avatar?: string;
	rating: number;
}

// Navigation types
export interface NavItem {
	label: string;
	href: string;
	children?: NavItem[];
}

export interface FooterLink {
	label: string;
	href: string;
	external?: boolean;
}

export interface FooterSection {
	title: string;
	links: FooterLink[];
}

// SEO types
export interface SEOData {
	title: string;
	description: string;
	keywords?: string[];
	ogImage?: string;
	ogType?: string;
	canonical?: string;
}

// Form validation types
export interface ValidationError {
	field: string;
	message: string;
}

export interface FormState<T> {
	data: T;
	errors: ValidationError[];
	isSubmitting: boolean;
	isValid: boolean;
}
