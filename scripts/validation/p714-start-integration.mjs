/** Operator/CI only. Starts fresh local synthetic fixture and browsers; never live qualification. */
import { spawn } from 'node:child_process'
import { createWriteStream } from 'node:fs'
import { readFile, writeFile, mkdir, readdir, copyFile, stat } from 'node:fs/promises'
import { dirname, resolve, join, sep } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createHash, randomUUID } from 'node:crypto'

const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..')
const sleep=ms=>new Promise(r=>{const timer=setTimeout(r,ms);timer.unref()})
const sha=data=>createHash('sha256').update(data).digest('hex')
for(const key of ['GEMINI_API_KEY','OLLAMA_API_KEY','SPRING_DATASOURCE_URL','HIPPOCAMPUS_DATASOURCE_URL'])
  if(process.env[key]?.trim())throw new Error('Remove external configuration before isolated validation: '+key)
for(const key of ['HIPPOCAMPUS_GEMINI_AI_ENABLED','HIPPOCAMPUS_OLLAMA_CLOUD_ENABLED','HIPPOCAMPUS_GEMINI_EMBEDDING_ENABLED'])
  if(process.env[key] && process.env[key].toLowerCase()!=='false')throw new Error('Disable live providers before validation: '+key)
if(process.env.DOCKER_HOST && !['unix://','npipe://'].some(prefix=>process.env.DOCKER_HOST.startsWith(prefix)))throw new Error('Remote Docker is not allowed')
if(process.env.SPRING_APPLICATION_JSON || process.env.SPRING_CONFIG_IMPORT || process.env.SPRING_CONFIG_LOCATION)
  throw new Error('Remove external Spring configuration overrides')
const output=resolve(process.argv[2]??join(root,'.validation','p714-start-integration-'+randomUUID()))
const expected=process.argv[3]
if(!expected || !/^[0-9a-f]{64}$/.test(expected))throw new Error('Exact reviewed source manifest SHA-256 is required as second argument')
try {await stat(join(root,'backend/target/surefire-reports/TEST-com.hippocampus.learning.StudyMissionStartBrowserFixtureTests.xml'));
  throw new Error('Retained fixture report exists; use a fresh exact-source workspace, never overwrite prior evidence')
} catch(error){if(error.code!=='ENOENT')throw error}
await mkdir(output,{recursive:false,mode:0o700})
const readyPath=join(output,'ready.json')
async function inventory(dir=root,rel=''){
  const lines=[]
  for(const item of await readdir(join(dir,rel),{withFileTypes:true})){
    if(['.git','target','node_modules','dist','test-results','playwright-report'].includes(item.name))continue
    const name=join(rel,item.name)
    if(item.isDirectory()){
      if(item.name==='.validation'){
        try{lines.push(sha(await readFile(join(dir,name,'.gitignore')))+'  '+join(name,'.gitignore').split(sep).join('/'))}
        catch(error){if(error.code!=='ENOENT')throw error}
      } else lines.push(...await inventory(dir,name))
    }
    else if(item.isFile())lines.push(sha(await readFile(join(dir,name)))+'  '+name.split(sep).join('/'))
  }
  return lines.sort((a,b)=>a.slice(66)<b.slice(66)?-1:a.slice(66)>b.slice(66)?1:0)
}
const before=(await inventory()).join('\n')+'\n'
await writeFile(join(output,'source-before.sha256'),before,{flag:'wx'})
if(sha(before)!==expected)throw new Error('Source identity differs from reviewed candidate; no process started')
const results={schemaVersion:1,syntheticOnly:true,startedAt:new Date().toISOString(),sourceBeforeSha256:sha(before),children:{}}
function psQuote(value){return "'"+value.replaceAll("'","''")+"'"}
function launch(name,args,cwd,env={}){
  const log=createWriteStream(join(output,name+'.log'),{flags:'wx',mode:0o600})
  let captureError=null,spawnError=null
  log.on('error',error=>{captureError=error.message})
  const windows=process.platform==='win32'
  const command=windows?'powershell.exe':args[0]
  const actual=windows?['-NoProfile','-NonInteractive','-Command','& '+args.map(psQuote).join(' ')+'; exit $LASTEXITCODE']:args.slice(1)
  const child=spawn(command,actual,{cwd,env:{...process.env,...env},windowsHide:true,stdio:['ignore','pipe','pipe']})
  child.stdout.pipe(log,{end:false});child.stderr.pipe(log,{end:false})
  child.stdout.on('error',error=>{captureError=error.message});child.stderr.on('error',error=>{captureError=error.message})
  child.once('error',error=>{spawnError=error.message})
  const state={child,closed:false,done:null}
  state.done=new Promise(resolveDone=>child.once('close',(exitCode,signal)=>{
    state.closed=true
    let recorded=false
    const finish=async()=>{
      if(recorded)return;recorded=true
      const record={exitCode,signal,spawnError,logCaptureExitCode:captureError?1:0,captureError}
      results.children[name]=record
      await writeFile(join(output,name+'.exit.json'),JSON.stringify(record,null,2),{flag:'wx'})
      resolveDone(record)
    }
    if(log.destroyed)void finish();else {log.end();log.once('finish',()=>void finish());log.once('error',()=>void finish())}
  }))
  return state
}
let fixture,browser,failed=null
try {
  const wrapper=process.platform==='win32'?'./mvnw.cmd':'./mvnw'
  fixture=launch('backend-fixture',[wrapper,'-B','-ntp','-Dtest=StudyMissionStartBrowserFixtureTests',
    '-Dhippocampus.p714.browser-fixture=true','-Dhippocampus.p714.evidence='+output,'test'],join(root,'backend'))
  const until=Date.now()+300000
  let ready
  while(Date.now()<until){
    if(fixture.closed)throw new Error('Backend fixture exited before readiness; inspect retained log')
    try{ready=JSON.parse(await readFile(readyPath,'utf8'));break}catch(error){if(error.code!=='ENOENT' && !(error instanceof SyntaxError))throw error}
    await sleep(250)
  }
  if(!ready)throw new Error('Backend fixture readiness deadline exceeded')
  const api=new URL(ready.apiBaseUrl)
  if(ready.schemaVersion!==1 || ready.syntheticOnly!==true || api.hostname!=='127.0.0.1' || api.protocol!=='http:' || api.username || api.password || api.pathname!=='/' || api.search || api.hash)
    throw new Error('Invalid loopback fixture readiness')
  browser=launch('playwright',[process.execPath,'node_modules/@playwright/test/cli.js','test','--config=playwright.p714-integration.config.ts'],join(root,'frontend'),{
    HIPPOCAMPUS_P714_READY_FILE:readyPath,HIPPOCAMPUS_P714_BROWSER_REPORT:join(output,'playwright-results.json'),
    HIPPOCAMPUS_P714_BROWSER_OUTPUT:join(output,'browser-artifacts')})
  const result=await browser.done
  if(result.exitCode!==0 || result.logCaptureExitCode!==0)throw new Error('Browser validation failed; inspect retained logs/reports')
} catch(error){failed=error.message}
finally {
  await writeFile(join(output,'stop'),'operator-supervisor-finished',{flag:'wx'})
  if(fixture){
    const graceful=await Promise.race([fixture.done,sleep(45000).then(()=>null)])
    if(!graceful){
      failed??='Backend fixture did not terminate within shutdown limit'
      if(process.platform==='win32')await new Promise(done=>{const killer=spawn('taskkill.exe',['/PID',String(fixture.child.pid),'/T','/F'],{windowsHide:true,stdio:'ignore'});killer.once('close',done);killer.once('error',done)})
      else fixture.child.kill('SIGTERM')
    } else if(graceful.exitCode!==0 || graceful.logCaptureExitCode!==0)failed??='Backend fixture acceptance failed'
  }
  for(const name of ['TEST-com.hippocampus.learning.StudyMissionStartBrowserFixtureTests.xml','com.hippocampus.learning.StudyMissionStartBrowserFixtureTests.txt']) {
    try { await copyFile(join(root,'backend/target/surefire-reports',name),join(output,name)) }
    catch(error){if(error.code!=='ENOENT')throw error;failed??='Missing backend fixture Surefire report'}
  }
  const after=(await inventory()).join('\n')+'\n'
  await writeFile(join(output,'source-after.sha256'),after,{flag:'wx'})
  results.sourceAfterSha256=sha(after);results.sourceUnchanged=before===after
  if(!results.sourceUnchanged)failed??='Source identity changed during validation'
  results.finishedAt=new Date().toISOString();results.failure=failed
  await writeFile(join(output,'execution.json'),JSON.stringify(results,null,2),{flag:'wx'})
}
console.log(JSON.stringify({evidenceDirectory:output,passed:failed===null,failure:failed}))
process.exitCode=failed?1:0
