import {defineConfig,devices} from '@playwright/test';
export default defineConfig({
  testDir:'./tests/browser',fullyParallel:false,workers:1,retries:0,
  reporter:[['list'],['html',{open:'never'}]],
  use:{baseURL:'http://127.0.0.1:4201',trace:'retain-on-failure',screenshot:'only-on-failure',...devices['Desktop Chrome'],channel:'msedge'},
  webServer:{command:'node node_modules/@angular/cli/bin/ng.js serve --host 127.0.0.1 --port 4201',url:'http://127.0.0.1:4201',reuseExistingServer:!process.env['CI'],timeout:120000}
});
