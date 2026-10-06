# ADR-007: Vite + React + Tailwind + shadcn/ui Frontend

## Status
Accepted

## Context
The Finance Tracker frontend needs:
- File upload for CSV and receipt images (drag-and-drop + camera)
- Editable preview grid for reviewing and confirming transactions
- Clean, modern UI with fast development feedback
- REST API client to communicate with Spring Boot backend on `:8080`

Key forces:
- Need fast development feedback (HMR, TypeScript checking)
- Need accessible, customizable UI components
- Must proxy API calls to Spring Boot backend during development
- Must produce optimized production bundle

## Decision
Use **Vite** as the build tool with **React 18+** and **TypeScript**, styled with **Tailwind CSS**, and componentized with **shadcn/ui**.

### Dependencies

| Dependency | Purpose |
|-----------|---------|
| `react` + `react-dom` (18+) | UI framework |
| `typescript` | Type safety |
| `vite` | Build tool, dev server, HMR |
| `tailwindcss` + `@tailwindcss/vite` | Utility-first CSS |
| `shadcn/ui` | Accessible UI components (Table, Button, Input, Card, Dialog, etc.) |
| `react-dropzone` | Drag-and-drop file upload |
| `lucide-react` | Icon library (used by shadcn/ui) |
| `@tanstack/react-table` | Table component (used by shadcn/ui Table) |

## Consequences

### Positive
- **Vite HMR** — sub-second hot module replacement during development
- **Tailwind CSS** — rapid UI prototyping with utility classes; no context-switching to CSS files
- **shadcn/ui** — copy-paste components (not a dependency), fully customizable, accessible (Radix primitives)
- **Single language** — TypeScript end-to-end in the frontend
- **Vite proxy** — seamless backend integration:
  ```typescript
  // vite.config.ts
  export default defineConfig({
    server: {
      proxy: {
        '/api': 'http://localhost:8080'
      }
    }
  });
  ```
- **Tree-shaking** — Vite + ES modules produce minimal production bundles
- **Type sharing** — can share DTO types between frontend and backend (optional)

### Negative
- **React learning curve** — hooks, effects, memoization patterns
- **shadcn/ui manual setup** — each component must be individually installed
- **Vite dev server on :5173** — must configure CORS on backend or use proxy
- **No SSR** — Vite is SPA-only by default (acceptable for an app-like tool)

### Neutral
- shadcn/ui components live in your source tree — they're yours to modify
- Tailwind requires learning utility class names (fast once familiar)
- Vite is the standard for new React projects (replaced CRA)

## Alternatives Considered

**Create React App (CRA)**
- Rejected: Deprecated by React team. Slower dev server, no native ESM, heavier config.

**Next.js (previous plan)**
- Rejected for revised stack: Backend is Spring Boot, so Next.js server features (API routes, SSR) are unused. Vite is lighter for a pure SPA frontend.

**Vue.js + Tailwind**
- Considered: Excellent DX but user explicitly chose React + shadcn/ui.

**Angular**
- Rejected: Too heavy for this project. Steeper learning curve, larger bundle size.

**Material UI (MUI)**
- Considered: Comprehensive component library. Rejected because shadcn/ui gives more control and smaller bundles (only copy what you use).

## Setup Commands

```bash
npm create vite@latest frontend -- --template react-ts
cd frontend
npm install
npm install -D tailwindcss @tailwindcss/vite

# Initialize shadcn/ui
npx shadcn@latest init

# Add components as needed
npx shadcn@latest add table button input card dialog
```

## Vite Proxy Config

```typescript
// vite.config.ts
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import path from 'path';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
```

## References
- [Vite Documentation](https://vitejs.dev/)
- [shadcn/ui Documentation](https://ui.shadcn.com/)
- [Tailwind CSS Documentation](https://tailwindcss.com/)
