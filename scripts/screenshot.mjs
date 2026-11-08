// 用途：通过 Chrome DevTools Protocol 登录并截取导师总览与学生工作台。
// 前置：后端 8080、Vite 5173、Chrome 远程调试 9222 已启动，数据库已走通 stu001 六步。
// 运行：node scripts/screenshot.mjs
import fs from 'node:fs'
import { fileURLToPath } from 'node:url'

const DEBUG = 'http://127.0.0.1:9222'
const APP = 'http://127.0.0.1:5173'
const OUT = fileURLToPath(new URL('../docs/screenshots', import.meta.url))

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

const targets = await (await fetch(`${DEBUG}/json/list`)).json()
const page = targets.find((target) => target.type === 'page')
if (!page) throw new Error('no page target')

const ws = new WebSocket(page.webSocketDebuggerUrl)
await new Promise((resolve, reject) => {
  ws.onopen = resolve
  ws.onerror = reject
})

let nextId = 0
const pending = new Map()
const errors = []

ws.onmessage = (event) => {
  const message = JSON.parse(event.data)
  if (message.id) {
    const handler = pending.get(message.id)
    if (handler) {
      pending.delete(message.id)
      handler(message)
    }
    return
  }
  if (message.method === 'Runtime.exceptionThrown') {
    errors.push(message.params.exceptionDetails?.text || 'exception')
  }
  if (message.method === 'Runtime.consoleAPICalled'
      && message.params.type === 'error'
      && !String(message.params.args?.[0]?.value || '').includes('404')) {
    errors.push(String(message.params.args?.[0]?.value || 'console error'))
  }
}

function send(method, params = {}) {
  return new Promise((resolve, reject) => {
    const id = ++nextId
    pending.set(id, (message) => {
      if (message.error) {
        reject(new Error(`CDP ${method}: ${JSON.stringify(message.error)}`))
      } else {
        resolve(message.result)
      }
    })
    ws.send(JSON.stringify({ id, method, params }))
  })
}

async function evaluate(expression, awaitPromise = false) {
  const result = await send('Runtime.evaluate', {
    expression,
    awaitPromise,
    returnByValue: true,
    userGesture: true
  })
  if (result.exceptionDetails) {
    throw new Error(result.exceptionDetails.text || 'evaluate failed')
  }
  return result.result?.value
}

async function navigate(url, wait = 2500) {
  await send('Page.navigate', { url })
  await sleep(wait)
}

async function capture(name) {
  const metrics = await send('Page.getLayoutMetrics')
  const content = metrics.cssContentSize || metrics.contentSize
  const width = 1440
  const height = Math.min(Math.ceil(content.height), 2200)
  const shot = await send('Page.captureScreenshot', {
    format: 'png',
    captureBeyondViewport: true,
    clip: { x: 0, y: 0, width, height, scale: 1 }
  })
  fs.writeFileSync(`${OUT}/${name}`, Buffer.from(shot.data, 'base64'))
  console.log(`saved ${name} (${width}x${height})`)
}

async function loginAndGo(username, path) {
  const result = await evaluate(`
    (async () => {
      const response = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: '${username}', password: '123456' })
      })
      const body = await response.json()
      if (!body.data?.token) throw new Error('login failed: ' + JSON.stringify(body))
      localStorage.setItem('thesis.token', body.data.token)
      localStorage.setItem('thesis.user', JSON.stringify(body.data.user))
      return body.data.user.role
    })()
  `, true)
  console.log(`${username} login -> ${result}`)
  await navigate(`${APP}${path}`, 2800)
}

await send('Page.enable')
await send('Runtime.enable')
await send('Emulation.setDeviceMetricsOverride', {
  width: 1440,
  height: 1100,
  deviceScaleFactor: 1,
  mobile: false
})

// 制造一条待批阅周报，让总览同时展示待办与已完成两种状态。
await navigate(`${APP}/login`, 1200)
await evaluate(`
  (async () => {
    const login = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: 'stu002', password: '123456' })
    }).then((response) => response.json())
    const headers = {
      'Content-Type': 'application/json',
      'Authorization': 'Bearer ' + login.data.token
    }
    const list = await fetch('/api/student/weekly-reports', { headers })
      .then((response) => response.json())
    if (list.data.some((item) => item.status === 'SUBMITTED')) {
      return 'already pending'
    }
    const result = await fetch('/api/student/weekly-reports', {
      method: 'POST',
      headers,
      body: JSON.stringify({ weekNo: 1, content: '完成需求梳理与页面原型，正在准备开发环境。' })
    }).then((response) => response.json())
    if (result.code !== 0) throw new Error(JSON.stringify(result))
    return result.data.status
  })()
`, true)

await evaluate('localStorage.clear(); true')
await navigate(`${APP}/login`, 1200)
await loginAndGo('stu001', '/student')
await capture('week6-student-complete.png')

await evaluate('localStorage.clear(); true')
await navigate(`${APP}/login`, 1200)
await loginAndGo('tea001', '/advisor')
await capture('week5-advisor-overview.png')

console.log(`runtime errors: ${errors.length}`)
if (errors.length) console.log(errors.join('\n'))
ws.close()
