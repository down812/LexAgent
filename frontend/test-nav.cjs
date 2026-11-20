/* 自动化复现侧边栏跳转问题：无头 Edge + 假登录态 + 屏蔽 API */
const puppeteer = require('puppeteer-core')

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const BASE = 'http://localhost:5173'

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function snapshot(page, label) {
  const info = await page.evaluate(() => {
    const mainChild = document.querySelector('.main')?.firstElementChild
    return {
      url: location.pathname,
      headerTitle: document.querySelector('.page-name')?.textContent?.trim() || '(无)',
      activeMenu: document.querySelector('.el-menu-item.is-active')?.textContent?.trim() || '(无)',
      mainViewClass: mainChild ? mainChild.className : '(空)'
    }
  })
  console.log(`\n[${label}]`)
  console.log(`  URL        : ${info.url}`)
  console.log(`  顶栏标题   : ${info.headerTitle}`)
  console.log(`  菜单高亮   : ${info.activeMenu}`)
  console.log(`  渲染的视图 : ${info.mainViewClass}`)
  return info
}

async function clickMenu(page, text) {
  return page.evaluate((t) => {
    const els = Array.from(document.querySelectorAll('.el-menu-item'))
    const target = els.find((el) => el.textContent.includes(t))
    if (!target) return false
    target.click()
    return true
  }, text)
}

async function main() {
  const browser = await puppeteer.launch({
    executablePath: EDGE,
    headless: 'new',
    args: ['--no-sandbox', '--disable-gpu', '--window-size=1440,900']
  })
  const page = await browser.newPage()
  await page.setViewport({ width: 1440, height: 900 })

  const logs = []
  page.on('console', (msg) => {
    if (msg.type() === 'error' || msg.type() === 'warning') {
      logs.push(`[console.${msg.type()}] ${msg.text().substring(0, 300)}`)
    }
  })
  page.on('pageerror', (err) => logs.push(`[pageerror] ${err.message.substring(0, 500)}`))

  // 屏蔽走 Vite 代理的 API 请求（精确匹配路径前缀 /api/，不能误伤 /src/api/*.js 源码模块），
  // 防止假 token 从 11650 后端拿到真实 401 触发登出跳转干扰测试
  await page.setRequestInterception(true)
  page.on('request', (req) => {
    let pathname = ''
    try {
      pathname = new URL(req.url()).pathname
    } catch {
      /* ignore */
    }
    if (pathname === '/api' || pathname.startsWith('/api/')) req.abort()
    else req.continue()
  })

  // 1. 打开页面并注入假登录态（超管 type=0）
  await page.goto(BASE + '/consult', { waitUntil: 'domcontentloaded' })
  await page.evaluate(() => {
    localStorage.setItem('lexagent_token', 'test-fake-token')
    localStorage.setItem('lexagent_userId', '1')
    localStorage.setItem('lexagent_userType', '0')
    localStorage.setItem('lexagent_identifier', '001')
  })
  await page.reload({ waitUntil: 'domcontentloaded' })
  await sleep(2500)
  await snapshot(page, '初始状态 /consult')

  // 2. 依次点击菜单，观察每次跳转
  const steps = ['知识库管理', '检索测试', '子账号管理', '法律问诊', '知识库管理', '子账号管理']
  for (const label of steps) {
    const ok = await clickMenu(page, label)
    if (!ok) {
      console.log(`\n[点击 ${label}] 菜单项未找到!`)
      continue
    }
    await sleep(1200)
    await snapshot(page, `点击「${label}」后`)
  }

  // 3. 再测一轮快速连续点击
  await sleep(500)
  await clickMenu(page, '检索测试')
  await sleep(300)
  await clickMenu(page, '法律问诊')
  await sleep(1500)
  await snapshot(page, '快速连点：检索测试→法律问诊')

  console.log('\n===== 浏览器控制台错误/警告 =====')
  if (logs.length === 0) console.log('(无)')
  else logs.slice(0, 30).forEach((l) => console.log(l))

  await browser.close()
}

main().catch((e) => {
  console.error('测试脚本失败:', e)
  process.exit(1)
})
