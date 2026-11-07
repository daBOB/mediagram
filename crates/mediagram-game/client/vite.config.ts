import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// `bun run dev` proxies to the Rust host on :8080 for hot-reload UI work.
export default defineConfig({
  plugins: [react()],
  server: {
    host: true,
    proxy: {
      "/ws": { target: "ws://localhost:8080", ws: true },
      "/qr.svg": "http://localhost:8080",
    },
  },
  build: { target: "es2019" },
});
