import { config } from "zod";

/** Strict CSP forbids Zod's optional JIT probe; configure before schemas load. */
config({ jitless: true });
