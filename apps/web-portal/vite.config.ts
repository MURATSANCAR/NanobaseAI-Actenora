import path from "node:path";
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

/** Production at portal.nanobase.ai is served under /easymeeting/ (see scripts/deploy-actenora-portal.sh). */
export default defineConfig({
  base: process.env.VITE_BASE ?? "/",
  plugins: [react()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "src"),
    },
  },
  server: {
    host: "127.0.0.1",
    port: 3000,
  },
  build: {
    outDir: "dist",
    sourcemap: true,
  },
});
