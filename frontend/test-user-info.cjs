/* 顶栏「查看信息 / 修改信息」功能测试 */
const puppeteer = require('puppeteer-core')
const fs = require('fs')

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const BASE = 'http://localhost:5173'
const OUT = 'D:\\AAADawn\\project\\LexAgent\\frontend\\.shots'
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

const userInfo = {
  userId: 1,
  identifier: 'dawn',
  phone: '13800138000',
  email: 'dawn@example.com',
  type: 1,
  typeName: '普通管理员',
  createdAt: '2026-10-01 08:00:00',
  updatedAt: '2026-10-30 20:00:00',
  lastActive: '2026-10-30 22:00:00'
}

async function main() {
  if (!fs.existsSync(OUT)) fs.mkdirSync(OUT, { recursive: true })
  const browser = await puppeteer.launch({ executablePath: EDGE, headless: 'new', args: ['--no-sandbox', '--disable-gpu'] })
  const page = await browser.newPage()
  await page.setViewport({ width: 1440, height: 900 })

  const errors = []
  page.on('pageerror', (e) => errors.push('[pageerror] ' + e.message.substring(0, 200)))
  const putBodies = []
  let putCount = 0

  await page.setRequestInterception(true)
  page.on('request', (req) => {
    const p = new URL(req.url()).pathname
    if (p === '/api/user/info' && req.method() === 'PUT') {
      putCount++
      try { putBodies.push(JSON.parse(req.postData() || '{}')) } catch { putBodies.push({}) }
      req.respond({ status: 200, contentType: 'application/json;charset=UTF-8', body: JSON.stringify({ code: 1, msg: '', data: { ...userInfo, email: 'new@example.com', phone: '13900139000' } }) })
    } else if (p === '/api/user/info') {
      req.respond({ status: 200, contentType: 'application/json;charset=UTF-8', body: JSON.stringify({ code: 1, msg: '', data: userInfo }) })
    } else if (p.startsWith('/api/')) {
      req.respond({ status: 200, contentType: 'application/json;charset=UTF-8', body: JSON.stringify({ code: 1, msg: '', data: null }) })
    } else {
      req.continue()
    }
  })

  await page.goto(BASE + '/consult', { waitUntil: 'domcontentloaded' })
  await page.evaluate(() => {
    localStorage.setItem('lexagent_token', 't')
    localStorage.setItem('lexagent_userId', '1')
    localStorage.setItem('lexagent_userType', '1')
    localStorage.setItem('lexagent_identifier', 'dawn')
  })
  await page.reload({ waitUntil: 'domcontentloaded' })
  await sleep(2200)

  // 打开用户下拉菜单 → 查看信息
  await page.click('.user-info')
  await sleep(600)
  await page.evaluate(() => {
    const item = [...document.querySelectorAll('.el-dropdown-menu__item')].find((e) => e.textContent.includes('查看信息'))
    if (item) item.click()
  })
  await sleep(1200)
  const viewInfo = await page.evaluate(() => {
    const dlg = document.querySelector('.el-dialog')
    return dlg ? dlg.textContent.replace(/\s+/g, ' ').slice(0, 300) : '(弹窗未出现)'
  })
  console.log('【查看信息弹窗】', viewInfo)
  await page.screenshot({ path: OUT + '\\11-view-info.png' })
  // 关闭查看弹窗（点 footer 的「关闭」）
  await page.evaluate(() => {
    const dlg = [...document.querySelectorAll('.el-dialog')].pop()
    const btn = [...dlg.querySelectorAll('button')].find((b) => b.textContent.includes('关闭'))
    if (btn) btn.click()
  })
  await sleep(600)

  // 修改信息：预填校验 + 修改保存
  await page.click('.user-info')
  await sleep(600)
  await page.evaluate(() => {
    const item = [...document.querySelectorAll('.el-dropdown-menu__item')].find((e) => e.textContent.includes('修改信息'))
    if (item) item.click()
  })
  await sleep(1200)
  const prefill = await page.evaluate(() => {
    const dlg = [...document.querySelectorAll('.el-dialog')].pop()
    const inputs = [...dlg.querySelectorAll('input')].map((i) => i.value)
    return inputs
  })
  console.log('【修改信息预填】', JSON.stringify(prefill))
  await page.screenshot({ path: OUT + '\\12-edit-info.png' })

  // 修改邮箱，保存（按 el-form-item 的 label 定位输入框）
  await page.evaluate(() => {
    const dlg = [...document.querySelectorAll('.el-dialog')].pop()
    const setVal = (input, val) => {
      const proto = Object.getPrototypeOf(input)
      const setter = Object.getOwnPropertyDescriptor(proto, 'value').set
      setter.call(input, val)
      input.dispatchEvent(new Event('input', { bubbles: true }))
    }
    const item = [...dlg.querySelectorAll('.el-form-item')].find((it) => it.querySelector('label')?.textContent.includes('邮箱'))
    const emailInput = item?.querySelector('input')
    if (emailInput) setVal(emailInput, 'new@example.com')
  })
  await sleep(300)
  await page.evaluate(() => {
    const dlg = [...document.querySelectorAll('.el-dialog')].pop()
    const btn = [...dlg.querySelectorAll('button')].find((b) => b.textContent.includes('保存'))
    if (btn) btn.click()
  })
  await sleep(1200)

  console.log('PUT 请求数=', putCount)
  putBodies.forEach((b, i) => console.log(`  PUT body${i + 1}=`, JSON.stringify(b)))
  const toast = await page.evaluate(() => {
    const msg = document.querySelector('.el-message')
    return msg ? msg.textContent : '(无提示)'
  })
  console.log('保存结果提示=', toast)
  await page.screenshot({ path: OUT + '\\13-after-save.png' })

  console.log('JS 错误:', errors.length ? errors.join('\n') : '(无)')
  await browser.close()
}

main().catch((e) => { console.error('失败:', e); process.exit(1) })
