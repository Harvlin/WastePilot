import { createLovableConfig } from "lovable-agent-playwright-config/config";

export default createLovableConfig({
  timeout: 30000,
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'on-first-retry',
  },
  testDir: './e2e',
});
