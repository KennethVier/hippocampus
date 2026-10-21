import { defineConfig, devices } from '@playwright/test'
import { readFileSync } from 'node:fs'

const recordPath=process.env.HIPPOCAMPUS_P714_READY_FILE
if(!recordPath)throw new Error('Operator fixture readiness file is required')
const ready=JSON.parse(readFileSync(recordPath,'utf8')) as {schemaVersion?:number;apiBaseUrl?:string;frontendBaseUrl?:string;syntheticOnly?:boolean}
if(ready.schemaVersion!==1 || ready.syntheticOnly!==true || ready.frontendBaseUrl!=='http://127.0.0.1:4175')throw new Error('Unexpected fixture record')
const api=new URL(ready.apiBaseUrl??'')
if(api.protocol!=='http:' || api.hostname!=='127.0.0.1' || !api.port || api.pathname!=='/' || api.username || api.password || api.search || api.hash)
  throw new Error('Only the fresh local synthetic API is allowed')
export default defineConfig({
  testDir:'./e2e',testMatch:'study-mission-start.integration.spec.ts',workers:1,fullyParallel:false,retries:0,forbidOnly:true,
  timeout:60000,globalTimeout:300000,reporter:[['list'],['json',{outputFile:process.env.HIPPOCAMPUS_P714_BROWSER_REPORT??'test-results/p714-results.json'}]],
  outputDir:process.env.HIPPOCAMPUS_P714_BROWSER_OUTPUT??'test-results/p714-integration',
  use:{baseURL:ready.frontendBaseUrl,trace:'retain-on-failure',screenshot:'only-on-failure'},
  projects:[{name:'chromium',use:{...devices['Desktop Chrome']}},{name:'mobile-chromium',use:{...devices['Pixel 7']}}],
  webServer:{command:'npm run dev -- --host 127.0.0.1 --port 4175 --strictPort',url:ready.frontendBaseUrl,reuseExistingServer:false,
    env:{VITE_API_BASE_URL:api.origin}},
})
