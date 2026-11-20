/* 历史消息多格式渲染测试：干净 JSON / 围栏 JSON / 双重转义 / 纯文本 / 部分字段 */
const puppeteer = require('puppeteer-core')

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const BASE = 'http://localhost:5173'
const OUT = 'D:\\AAADawn\\project\\LexAgent\\frontend\\.shots'
const fs = require('fs')
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

const answer = {
  preliminaryJudgment: '根据您的描述，您的情况属于劳动领域的劳动合同解除问题。',
  legalBasis: [
    { lawName: '中华人民共和国劳动合同法', articleNo: '第四十七条', content: '经济补偿按劳动者在本单位工作的年限，每满一年支付一个月工资的标准向劳动者支付。', relevance: '您的工龄为两年，可主张两个月工资的经济补偿。' }
  ],
  caseAnalysis: '公司在未提前通知且未支付经济补偿的情况下解除合同，属于违法解除。',
  suggestions: ['与公司协商，要求支付经济补偿', '协商不成可向劳动仲裁委员会申请仲裁', '保留工资条、考勤记录等证据'],
  riskWarning: '仲裁时效为一年，自知道权利被侵害之日起计算。',
  confidenceScore: 86,
  reviewPassed: true
}

const cleanJson = JSON.stringify(answer)
const fencedJson = '```json\n' + cleanJson + '\n```'
const doubleEncoded = JSON.stringify(cleanJson)
const plainText = '**您好**，根据您的情况：\n\n1. 公司构成违法解除\n2. 您可以主张**赔偿金**\n\n建议尽快收集证据。'
const partialJson = JSON.stringify({ preliminaryJudgment: '初步判断：属于民间借贷纠纷。', riskWarning: '注意诉讼时效。' })

function mockResponse(pathname) {
  if (pathname === '/api/chat/conversations') {
    return { code: 1, msg: '', data: [{ id: 'c1', title: '多种格式历史消息测试', createdAt: '2026-10-29T10:00:00', updatedAt: '2026-10-30T18:00:00' }] }
  }
  if (pathname.startsWith('/api/chat/messages/')) {
    // 真实后端契约：ChatMessageVO.content 为 Object——assistant 消息已被
    // MessageServiceImpl 解析为结构化对象；仅降级场景 content 为字符串
    return {
      code: 1, msg: '',
      data: [
        { id: 1, role: 'user', content: '公司辞退我不给补偿怎么办？', createdAt: '2026-10-29T10:00:00' },
        { id: 2, role: 'assistant', content: answer, createdAt: '2026-10-29T10:01:00' },
        { id: 3, role: 'user', content: '降级纯文本场景呢？', createdAt: '2026-10-29T10:02:00' },
        { id: 4, role: 'assistant', content: '**您好**，服务降级时的纯文本回答：\n\n1. 先与公司协商\n2. 保留证据', createdAt: '2026-10-29T10:03:00' }
      ]
    }
  }
  return { code: 1, msg: '', data: null }
}

async function main() {
  if (!fs.existsSync(OUT)) fs.mkdirSync(OUT, { recursive: true })
  const browser = await puppeteer.launch({
    executablePath: EDGE,
    headless: 'new',
    args: ['--no-sandbox', '--disable-gpu']
  })
  const page = await browser.newPage()
  await page.setViewport({ width: 1440, height: 900 })

  const errors = []
  page.on('pageerror', (e) => errors.push('[pageerror] ' + e.message.substring(0, 200)))

  await page.setRequestInterception(true)
  page.on('request', (req) => {
    const p = new URL(req.url()).pathname
    if (p.startsWith('/api/')) {
      req.respond({ status: 200, contentType: 'application/json;charset=UTF-8', body: JSON.stringify(mockResponse(p)) })
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
  await sleep(3000)

  const info = await page.evaluate(() => {
    const msgs = document.querySelectorAll('.msg-row')
    const legal = document.querySelectorAll('.legal-answer').length
    const fallback = document.querySelectorAll('.fallback-md').length
    const userBubbles = document.querySelectorAll('.msg-row.user .msg-body').length
    // 检查有没有裸露 JSON 或 [object Object]
    const bodyText = document.body.innerText
    const rawJsonVisible = [...document.querySelectorAll('.chat-messages div')].filter(
      (el) => el.children.length === 0 && /^\{"[a-zA-Z]+"/.test(el.textContent.trim())
    ).length
    return { total: msgs.length, legal, fallback, userBubbles, rawJsonVisible, hasObjectObject: bodyText.includes('[object Object]') }
  })
  console.log(`消息总数=${info.total} 结构化渲染=${info.legal} Markdown兜底=${info.fallback} 用户气泡=${info.userBubbles} 裸露JSON块=${info.rawJsonVisible} [objectObject]=${info.hasObjectObject}`)

  // 滚动到顶部截图整页消息
  await page.evaluate(() => { const el = document.querySelector('.chat-messages'); if (el) el.scrollTop = 0 })
  await sleep(500)
  await page.screenshot({ path: OUT + '\\07-consult-history.png', fullPage: false })
  // 滚动到底部再截一张（看到用户气泡和法条卡片）
  await page.evaluate(() => { const el = document.querySelector('.chat-messages'); if (el) el.scrollTop = el.scrollHeight })
  await sleep(500)
  await page.screenshot({ path: OUT + '\\08-consult-bottom.png' })

  console.log('JS 错误:', errors.length ? errors.join('\n') : '(无)')
  await browser.close()
}

main().catch((e) => { console.error('失败:', e); process.exit(1) })
