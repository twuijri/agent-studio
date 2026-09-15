#!/usr/bin/env node
// Only disposable, labelled containers/volumes. Never reads an existing instance.
import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { randomUUID } from 'node:crypto'
import { readFileSync } from 'node:fs'
import { createHash } from 'node:crypto'

const image = process.argv[2]
if (!image || image.startsWith('-')) throw new Error('Usage: node scripts/smoke-personal-image.mjs IMAGE')
const id = `agent-studio-smoke-${randomUUID()}`
const volumes = [`${id}-hermes`, `${id}-studio`]
const marker = randomUUID()
let containerExists = false
const createdVolumes = []
const docker = (...args) => execFileSync('docker', args, { encoding: 'utf8', timeout: 120_000 }).trim()
const inside = (code) => docker('exec', id, 'node', '--input-type=module', '-e', code)
const pause = (ms) => new Promise(resolve => setTimeout(resolve, ms))

function launch() {
  docker('run', '-d', '--name', id, '--label', `agent-studio.smoke=${id}`,
    '-p', '127.0.0.1::6060',
    '-v', `${volumes[0]}:/home/agent/.hermes`,
    '-v', `${volumes[1]}:/home/agent/.hermes-web-ui`,
    '-e', 'HERMES_WEB_UI_HOME=/home/agent/.hermes-web-ui',
    '-e', 'HERMES_WEBUI_STATE_DIR=/home/agent/.hermes-web-ui',
    '-e', 'HERMES_WEB_UI_DISABLE_UPDATE_CHECK=1',
    '-e', 'HERMES_ALLOW_ROOT_GATEWAY=1',
    '-e', 'HERMES_WEB_UI_MANAGED_GATEWAY=1', image)
  containerExists = true
}

async function checkHttp() {
  const address = docker('port', id, '6060/tcp')
  assert.match(address, /^127\.0\.0\.1:\d+$/)
  const base = `http://${address}`
  let ready = false
  for (let i = 0; i < 90; i++) {
    try {
      const response = await fetch(`${base}/health/ready`, { signal: AbortSignal.timeout(2000) })
      if (response.ok) { ready = true; break }
    } catch { /* Startup may still be in progress. */ }
    await pause(1000)
  }
  assert.ok(ready, 'Server must become ready within the startup deadline')
  assert.equal((await fetch(base)).status, 200)
  assert.equal((await fetch(`${base}/api/auth/me`)).status, 401, 'Private API requires authentication')
  const login = await fetch(`${base}/api/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    // Upstream bootstrap credentials; this instance has disposable empty data only.
    body: JSON.stringify({ username: 'admin', password: '123456' }),
  })
  assert.equal(login.status, 200)
  const { token } = await login.json()
  assert.equal(typeof token, 'string')
  const me = await fetch(`${base}/api/auth/me`, { headers: { Authorization: `Bearer ${token}` } })
  assert.equal(me.status, 200)
  const account = await me.json()
  assert.equal(account.user.username, 'admin')
  return account.user.id
}

try {
  for (const volume of volumes) {
    docker('volume', 'create', '--label', `agent-studio.smoke=${id}`, volume)
    createdVolumes.push(volume)
  }
  launch()
  const userId = await checkHttp()
  // Verify both full runtimes exist, without invoking any paid model or channel.
  docker('exec', id, '/opt/hermes/.venv/bin/python', '-c', 'import sys; import hermes_cli; assert sys.version_info >= (3, 10)')
  inside(`import {existsSync} from 'node:fs'; if (!existsSync('/opt/hermes/.venv/bin/hermes')) throw Error('Hermes CLI missing')`)
  inside(`import net from 'node:net';
    const socket=net.createConnection('/tmp/hermes-agent-bridge.sock',()=>socket.write(JSON.stringify({action:'ping'})+'\\n'));
    let response='',verified=false;
    socket.setTimeout(5000,()=>socket.destroy(new Error('Bridge ping timed out')));
    socket.on('data',chunk=>{response+=chunk.toString();if(response.includes('\\n')){const result=JSON.parse(response.split('\\n')[0]);if(!result.ok||!result.pong) throw Error('Bridge not ready');verified=true;socket.end()}});
    socket.on('close',()=>{if(!verified)process.exitCode=1});
    socket.on('error',error=>{console.error(error.message);process.exitCode=1});`)
  const license = inside(`import {readFileSync} from 'node:fs'; import {createHash} from 'node:crypto'; console.log(createHash('sha256').update(readFileSync('/app/LICENSE')).digest('hex'))`)
  assert.equal(license, createHash('sha256').update(readFileSync(new URL('../LICENSE', import.meta.url))).digest('hex'))
  inside(`import {mkdirSync,writeFileSync} from 'node:fs';
    const bin='/home/agent/.hermes-web-ui/coding-agent/npm/bin';
    if(!process.env.PATH.split(':').includes(bin)) throw Error('Persistent CLI bin missing from PATH');
    mkdirSync(bin,{recursive:true});
    writeFileSync(bin+'/agent-studio-smoke-marker',${JSON.stringify(marker)});
    writeFileSync('/home/agent/.hermes/agent-studio-smoke-marker',${JSON.stringify(marker)});`)
  docker('rm', '-f', id)
  containerExists = false
  launch()
  assert.equal(await checkHttp(), userId, 'Account survives container replacement')
  inside(`import {readFileSync} from 'node:fs';
    for(const p of ['/home/agent/.hermes/agent-studio-smoke-marker','/home/agent/.hermes-web-ui/coding-agent/npm/bin/agent-studio-smoke-marker'])
      if(readFileSync(p,'utf8')!==${JSON.stringify(marker)}) throw Error('Volume did not persist');`)
  console.log('PASS: readiness, authentication, Hermes runtime/bridge ping, license, both data volumes and persistent CLI directory across replacement.')
  console.log('Not tested: actual model calls, channel delivery, user production data, sysbox, or desktop/mobile packaging.')
} finally {
  if (containerExists) docker('rm', '-f', id)
  for (const volume of createdVolumes) {
    assert.equal(docker('volume', 'inspect', volume, '--format', '{{index .Labels "agent-studio.smoke"}}'), id)
    docker('volume', 'rm', volume)
  }
}
