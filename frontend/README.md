# EV Monitor - Frontend

This directory manages the visual interface encompassing a Progressive Web App (PWA) client for EV Monitor. It delivers an optimized layout catering to logging live telemetry values intuitively.

## 🤖 AI Assistant Context & Tech Stack
When operating across this frontend directory and implementing features, continuously synchronize against these explicit technological bindings limiting uncompatible code generation behaviors:

- **Framework Module**: **Vue 3** built upon the Composition API design strictly referencing the concise `<script setup lang="ts">` setup tags.
- **Build Chain**: **Vite** bundled internally communicating natively with the **Vite PWA Plugin** (`vite-plugin-pwa`).
- **Typing Framework**: **TypeScript** enforcing explicit type schemas.
- **Visual Styling Protocol**: **Tailwind CSS v4**.
  - **CRITICAL COMPLIANCE FIX**: Tailwind v4 executes via `@tailwindcss/postcss` located distinctly within `postcss.config.js`. It explicitly strips the capability to utilize historically aged `@tailwind` directives. As shown within `src/index.css`, components rely only on root-level `@import "tailwindcss";` hooks. Do not generate or interleave legacy Tailwind v3 syntax variants utilizing rigid `@apply` class applications unless fully conforming to v4 implementation guidelines.

## Prerequisites Requirement

- Operating **Node.js** architecture v20+ optimally alongside **NPM**.

## Installation Initialization

Traverse to the `frontend` origin directory and provision the exact dependencies specified lockfile:
```bash
npm install
```

## Running Dev Server Locally

Trigger the rapid localized Vite development server:
```bash
npm run dev
```
The application spins up responsively mounted at `http://localhost:5173`. 
*Functional Note: To guarantee accurate execution of API polling and submissions without encountering CORS drops, ensure your backend server operates cleanly parallel. Direct queries flow against standard URL endpoints expected locally on `localhost:8080` unless proxying overrides intervene.*

## Creating Production Bundles

Optimize static file chunk generation encapsulating JavaScript objects perfectly alongside the crucial active configuration bindings to build Service Worker cache maps establishing offline-enabled PWA rules logic:
```bash
npm run build
```
The command systematically traverses `vue-tsc` catching strict structural type leaks progressing successfully toward injecting artifacts directly into Vite's optimized `dist/` manifest sequence folder safely preparing them to be directly served structurally statically.

## References
- Return backwards referring sequentially to the [Root README](../README.md).
- Proceed into checking explicitly [Backend API Specifications](../backend/README.md).
