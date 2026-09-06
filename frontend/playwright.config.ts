import { defineConfig } from "@playwright/test";

export default defineConfig({
  timeout: 30000,
  use: {
    baseURL: "http://localhost:5173",
    trace: "on-first-retry",
  },
  testDir: './e2e',
});
