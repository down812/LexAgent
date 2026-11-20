/* 带真实数据的导航复现测试：mock API 返回列表数据，触发模板格式化函数调用 */
const puppeteer = require('puppeteer-core')

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const BASE = 'http://localhost:5173'
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

const ok = (data) => ({ code: 1, msg: '', data })

function mockResponse(pathname) {
  if (pathname === '/api/chat/conversations') {
    return ok([
      { id: 'c1', title: '劳动合同纠纷咨询', createdAt: '2026-10-29T10:00:00', updatedAt: '2026-10-30T09:00:00' },
      { id: 'c2', title: '租房押金退还问题', createdAt: '2026-10-28T14:00:00', updatedAt: '2026-10-28T15:00:00' }
    ])
  }
  if (pathname.startsWith('/api/chat/messages/')) {
    return ok([
      { id: 1, role: 'user', content: '公司突然辞退我，没有补偿怎么办？', createdAt: '2026-10-29T10:01:00' },
      { id: 2, role: 'assistant', content: '根据劳动合同法，公司违法解除劳动合同应支付赔偿金……', createdAt: '2026-10-29T10:02:00' }
    ])
  }
  if (pathname === '/api/knowledge/dataset/page') {
    return ok({
      total: 2,
      rows: [
        { id: 1, name: '劳动法知识库', domain: '劳动', description: '劳动合同、工伤、社保相关法条', fileCount: 3, createdAt: '2026-10-01T08:00:00' },
        { id: 2, name: '民法典知识库', domain: '民事', description: '民法典各编条款', fileCount: 5, createdAt: '2026-10-02T08:00:00' }
      ]
    })
  }
  if (pathname === '/api/knowledge/file/page') {
    return ok({
      total: 1,
      rows: [
        { id: 1, fileName: '劳动合同法.pdf', fileType: 'PDF', status: 'SUCCESS', chunkCount: 42, errorMsg: null, uploadTime: '2026-10-02T09:00:00' }
      ]
    })
  }
  if (pathname === '/api/knowledge/ocr/status') {
    return ok({ enabled: true, available: true, message: 'OCR 服务运行正常' })
  }
  if (pathname === '/api/user/subaccountList') {
    return ok({
      total: 2,
      rows: [
        { subaccountId: 1, identifier: 'admin01', type: 1, createdAt: '2026-10-01T08:00:00' },
        { subaccountId: 2, identifier: 'user01', type: 2, createdAt: '2026-10-03T08:00:00' }
      ]
    })
  }
  if (pathname.startsWith('/api/knowledge/search')) {
    return ok([
      {
        lawName: '中华人民共和国劳动合同法',
        articleNo: '第四十七条',
        chapter: '第四章',
        domain: '劳动',
        content: '经济补偿按劳动者在本单位工作的年限，每满一年支付一个月工资的标准向劳动者支付……',
        verified: true,
        matchType: 'HYBRID',
        vectorScore: 0.85,
        keywordScore: 0.72,
        rrfScore: 0.9,
        rerankScore: 0.88,
        score: 0.85,
        verifyDetail: '条款核验通过'
      }
    ])
  }
  return ok(null)
}

async function snapshot(page, label) {
  const info = await page.evaluate(() => ({
    url: location.pathname,
    view: document.querySelector('.main')?.firstElementChild?.className || '(空)',
    rows: document.querySelectorAll('.el-table__row').length,
    convs: document.querySelectorAll('.conv-item').length
  }))
  console.log(`[${label}] URL=${info.url} 视图=${info.view} 表格行=${info.rows} 会话数=${info.convs}`)
}

async function clickMenu(page, text) {
  await page.evaluate((t) => {
    const el = [...document.querySelectorAll('.el-menu-item')].find((e) => e.textContent.includes(t))
    if (el) el.click()
  }, text)
}

async function main() {
  const browser = await puppeteer.launch({
    executablePath: EDGE,
    headless: 'new',
    args: ['--no-sandbox', '--disable-gpu']
  })
  const page = await browser.newPage()
  await page.setViewport({ width: 1440, height: 900 })

  const errors = []
  page.on('pageerror', (e) => errors.push('[pageerror] ' + e.message.substring(0, 200)))
  page.on('console', (m) => {
    if (m.type() === 'error' && !m.text().includes('Failed to load resource')) {
      errors.push('[console.error] ' + m.text().substring(0, 300))
    }
  })

  // mock 所有 API，返回带数据的响应
  await page.setRequestInterception(true)
  page.on('request', (req) => {
    const p = new URL(req.url()).pathname
    if (p.startsWith('/api/')) {
      req.respond({
        status: 200,
        contentType: 'application/json;charset=UTF-8',
        body: JSON.stringify(mockResponse(p))
      })
    } else {
      req.continue()
    }
  })

  await page.goto(BASE + '/consult', { waitUntil: 'domcontentloaded' })
  await page.evaluate(() => {
    localStorage.setItem('lexagent_token', 't')
    localStorage.setItem('lexagent_userId', '1')
    localStorage.setItem('lexagent_userType', '0')
    localStorage.setItem('lexagent_identifier', '001')
  })
  await page.reload({ waitUntil: 'domcontentloaded' })
  await sleep(2500)
  await snapshot(page, '初始 /consult（会话列表带数据）')

  for (const t of ['知识库管理', '检索测试', '子账号管理', '法律问诊', '知识库管理']) {
    await clickMenu(page, t)
    await sleep(1400)
    await snapshot(page, `点击「${t}」`)
  }

  // 检索测试：触发一次检索渲染结果卡片（toPercent）
  await clickMenu(page, '检索测试')
  await sleep(1000)
  await page.evaluate(() => {
    const btns = [...document.querySelectorAll('button')]
    const b = btns.find((x) => x.textContent.includes('检索') && !x.textContent.includes('混合'))
    // 直接点主检索按钮
    const searchBtn = btns.find((x) => x.textContent.trim() === '检索')
    if (searchBtn) searchBtn.click()
  })
  await page.evaluate(() => {
    // 先输入查询词
    const ta = document.querySelector('.search-box textarea')
    if (ta) {
      ta.value = '公司辞退员工不支付补偿'
      ta.dispatchEvent(new Event('input', { bubbles: true }))
    }
  })
  await sleep(300)
  await page.evaluate(() => {
    const b = [...document.querySelectorAll('.search-controls button')].find((x) => x.textContent.includes('检索'))
    if (b) b.click()
  })
  await sleep(1500)
  const searchInfo = await page.evaluate(() => ({
    cards: document.querySelectorAll('.result-card').length
  }))
  console.log(`[检索执行] 结果卡片数=${searchInfo.cards}`)

  console.log('\n===== 捕获的 JS 错误 =====')
  if (!errors.length) console.log('(无)')
  else [...new Set(errors)].slice(0, 15).forEach((e) => console.log(e))

  await browser.close()
}

main().catch((e) => {
  console.error('测试失败:', e)
  process.exit(1)
})
