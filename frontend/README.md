# EV Monitor - Frontend

The Vue.js PWA client for EV Monitor — charging log management, vehicle tracking, WLTP data crowdsourcing, and statistics.

## 🤖 AI Assistant Context & Tech Stack

When working in this directory, adhere to these constraints:

- **Framework**: **Vue 3** with Composition API — use `<script setup lang="ts">` syntax
- **Build Tool**: **Vite** (with `vite-plugin-pwa` for PWA support)
- **TypeScript**: Strict typing throughout
- **Styling**: **Tailwind CSS v4**
  - Uses `@import "tailwindcss";` in `src/index.css` (NOT legacy `@tailwind` directives)
  - Config lives in `postcss.config.js` via `@tailwindcss/postcss`
  - Do not use Tailwind v3 `@apply` syntax
- **State Management**: Pinia
- **HTTP Client**: Axios with JWT interceptor (auto-attaches `Authorization: Bearer` header)
- **Routing**: Vue Router with `requiresAuth` / `guestOnly` guards

## Prerequisites

- **Node.js 20+**
- **npm**

## Running Locally

```bash
npm install
npm run dev
```

The dev server starts at `http://localhost:5173`.

For API calls to work, the backend must be running on `http://localhost:8080` (or use `./dev.sh` from the root to start everything at once).

## Building for Production

```bash
npm run build
```

Output goes to `dist/` — served by Nginx in production. Runs `vue-tsc` for type checking before bundling.

## Key Notes

- **Location Search**: OpenStreetMap Nominatim API, debounced (300ms). Users can search or use GPS. Coordinates are sent to the backend which converts them to a geohash — exact GPS is never stored.
- **WLTP Flow**: When a user selects a car model, the frontend auto-lookups WLTP data. If none exists, an overlay prompts the user to contribute data and earn coins.
- **Email Verification**: `/verify-email` route has no auth guard — must be accessible without a token.
- **OAuth2**: `OAuth2RedirectHandler.vue` handles the callback and parses the JWT from the URL fragment.

## References

- [Root README](../README.md)
- [Backend README](../backend/README.md)