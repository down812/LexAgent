/* 重构后全页面截图走查 */
const puppeteer = require('puppeteer-core')
const fs = require('fs')

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const BASE = 'http://localhost:5173'
const OUT = 'D:\\AAADawn\\project\\LexAgent\\frontend\\.shots'
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function main() {
  if (!fs.existsSync(OUT)) fs.mkdirSync(OUT, { recursive: true })
  const browser = await puppeteer.launch({
    executablePath: EDGE,
    headless: 'new',
    args: ['--no-sandbox', '--disable-gpu']
  })
  const page = await browser.newPage()
  await page.setViewport({ width: 1440, height: 900, deviceScaleFactor: 1 })

  await page.setRequestInterception(true)
  page.on('request', (req) => {
    const p = new URL(req.url()).pathname
    if (p === '/api' || p.startsWith('/api/')) req.abort()
    else req.continue()
  })

  const errors = []
  page.on('pageerror', (e) => errors.push(e.message))

  // 1. 登录页
  await page.goto(BASE + '/login', { waitUntil: 'domcontentloaded' })
  await sleep(1800)
  await page.screenshot({ path: OUT + '\\01-login.png' })
  console.log('01-login.png OK')

  // 2. 注册页
  await page.goto(BASE + '/register', { waitUntil: 'domcontentloaded' })
  await sleep(1200)
  await page.screenshot({ path: OUT + '\\02-register.png' })
  console.log('02-register.png OK')

  // 注入登录态
  await page.evaluate(() => {
    localStorage.setItem('lexagent_token', 't')
    localStorage.setItem('lexagent_userId', '1')
    localStorage.setItem('lexagent_userType', '0')
    localStorage.setItem('lexagent_identifier', '001')
  })

  // 3. 问诊页
  await page.goto(BASE + '/consult', { waitUntil: 'domcontentloaded' })
  await sleep(2200)
  await page.screenshot({ path: OUT + '\\03-consult.png' })
  console.log('03-consult.png OK')

  // 4. 知识库
  await page.goto(BASE + '/knowledge', { waitUntil: 'domcontentloaded' })
  await sleep(1800)
  await page.screenshot({ path: OUT + '\\04-knowledge.png' })
  console.log('04-knowledge.png OK')

  // 5. 检索测试
  await page.goto(BASE + '/search', { waitUntil: 'domcontentloaded' })
  await sleep(1800)
  await page.screenshot({ path: OUT + '\\05-search.png' })
  console.log('05-search.png OK')

  // 6. 子账号管理
  await page.goto(BASE + '/admin', { waitUntil: 'domcontentloaded' })
  await sleep(1800)
  await page.screenshot({ path: OUT + '\\06-admin.png' })
  console.log('06-admin.png OK')

  console.log('\n页面 JS 错误:', errors.length ? errors.join('\n') : '(无)')
  await browser.close()
}

main().catch((e) => {
  console.error('截图失败:', e)
  process.exit(1)
})
