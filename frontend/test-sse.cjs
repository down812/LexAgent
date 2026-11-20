/* SSE 流式问诊完整流程复现：mock 后端 SSE 流，观察 AI 回复渲染 */
const puppeteer = require('puppeteer-core')

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const BASE = 'http://localhost:5173'
const OUT = 'D:\\AAADawn\\project\\LexAgent\\frontend\\.shots'
const fs = require('fs')
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

const finalAnswer = {
  preliminaryJudgment: '根据您的描述，您的情况属于劳动领域的劳动合同解除问题。',
  legalBasis: [
    { lawName: '中华人民共和国劳动合同法', articleNo: '第四十七条', content: '经济补偿按劳动者在本单位工作的年限，每满一年支付一个月工资的标准向劳动者支付。', relevance: '您的工龄为一年，可主张一个月工资的经济补偿。' }
  ],
  caseAnalysis: '公司在未提前通知且未支付经济补偿的情况下解除合同，属于违法解除。',
  suggestions: ['与公司协商，要求支付经济补偿', '协商不成可申请劳动仲裁'],
  riskWarning: '仲裁时效为一年。',
  confidenceScore: 86,
  reviewPassed: true
}

function sseEvent(event, dataObj) {
  const payload = JSON.stringify({ event, timestamp: Date.now(), ...dataObj })
  return `event:${event}\ndata:${payload}\nid:${Date.now()}\n\n`
}

const sseStream =
  sseEvent('agent_start', { agentType: 'CASE_ANALYSIS', agentName: '案件分析', message: '案件分析 开始执行' }) +
  sseEvent('agent_start', { agentType: 'LEGAL_SEARCH', agentName: '法律检索', message: '法律检索 开始执行' }) +
  sseEvent('agent_start', { agentType: 'LEGAL_ANALYSIS', agentName: '法律分析', message: '法律分析 开始执行' }) +
  sseEvent('agent_start', { agentType: 'REVIEWER', agentName: '核验审核', message: '核验审核 开始执行' }) +
  sseEvent('agent_result', { agentType: 'CASE_ANALYSIS', agentName: '案件分析', message: '案件分析完成：劳动 · 劳动合同解除' }) +
  sseEvent('agent_result', { agentType: 'LEGAL_SEARCH', agentName: '法律检索', message: '法律法规检索完成，命中 1 条' }) +
  sseEvent('agent_result', { agentType: 'LEGAL_ANALYSIS', agentName: '法律分析', message: '法律分析完成' }) +
  sseEvent('agent_result', { agentType: 'REVIEWER', agentName: '核验审核', message: '法律依据核验完成，可信度：86' }) +
  sseEvent('citation', { data: [{ lawName: '中华人民共和国劳动合同法', articleNo: '第四十七条' }] }) +
  sseEvent('content', { data: finalAnswer, message: '正在生成回答……' }) +
  sseEvent('done', { message: '处理完成，耗时 3000ms' })

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
  page.on('pageerror', (e) => errors.push('[pageerror] ' + e.message.substring(0, 300)))
  page.on('console', (m) => {
    if (m.type() === 'error' && !m.text().includes('Failed to load resource')) {
      errors.push('[console.error] ' + m.text().substring(0, 300))
    }
  })

  await page.setRequestInterception(true)
  const consultBodies = []
  page.on('request', (req) => {
    const p = new URL(req.url()).pathname
    if (p === '/api/chat/consult') {
      try { consultBodies.push(JSON.parse(req.postData() || '{}')) } catch { consultBodies.push({}) }
      req.respond({ status: 200, contentType: 'text/event-stream', body: sseStream })
    } else if (p.startsWith('/api/')) {
      req.respond({ status: 200, contentType: 'application/json;charset=UTF-8', body: JSON.stringify({ code: 1, msg: '', data: p === '/api/chat/conversations' ? [] : null }) })
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
  await sleep(2200)

  // 输入问题并发送（入口1：初始空态直接提问）
  await page.type('.chat-input textarea', '我在公司工作一年，公司辞退我，我能得到什么补偿')
  await sleep(300)
  await page.evaluate(() => {
    const btn = document.querySelector('.send-btn')
    if (btn) btn.click()
  })

  await sleep(6000)

  // 入口2：点「新问诊」后再发送
  await page.evaluate(() => {
    const btn = [...document.querySelectorAll('button')].find((b) => b.textContent.includes('新问诊'))
    if (btn) btn.click()
  })
  await sleep(400)
  await page.type('.chat-input textarea', '第二个问题')
  await sleep(200)
  await page.evaluate(() => {
    const btn = document.querySelector('.send-btn')
    if (btn) btn.click()
  })
  await sleep(2500)

  // 校验所有 consult 请求的 conversationId 均为非空 UUID
  const uuidRe = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
  console.log('consult 请求数=', consultBodies.length)
  consultBodies.forEach((b, i) => {
    const ok = uuidRe.test(b.conversationId || '')
    console.log(`  请求${i + 1}: conversationId=${b.conversationId} ${ok ? '✓ UUID 合法' : '✗ 非法/为空'}`)
  })
  const distinctIds = new Set(consultBodies.map((b) => b.conversationId)).size
  console.log(`不同会话ID数=${distinctIds}（两个入口应各生成一个，期望=2）`)

  const info = await page.evaluate(() => {
    const aiMsgs = [...document.querySelectorAll('.msg-row.assistant')]
    const lastAi = aiMsgs[aiMsgs.length - 1]
    return {
      aiCount: aiMsgs.length,
      lastAiText: lastAi ? lastAi.textContent.replace(/\s+/g, ' ').slice(0, 200) : '(无)',
      legalAnswerEls: document.querySelectorAll('.legal-answer').length,
      hasObjectObject: document.body.innerText.includes('[object Object]')
    }
  })
  console.log('AI 消息数=', info.aiCount)
  console.log('最后一条 AI 文本=', info.lastAiText)
  console.log('.legal-answer 元素数=', info.legalAnswerEls)
  console.log('页面包含 [object Object]?', info.hasObjectObject)
  await page.evaluate(() => { const el = document.querySelector('.chat-messages'); if (el) el.scrollTop = el.scrollHeight })
  await sleep(400)
  await page.screenshot({ path: OUT + '\\10-sse-final.png' })

  console.log('JS 错误:', errors.length ? errors.join('\n') : '(无)')
  await browser.close()
}

main().catch((e) => { console.error('失败:', e); process.exit(1) })
