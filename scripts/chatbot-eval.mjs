#!/usr/bin/env node
// Automatic test of the shopping assistant (step 4 of training an AI: test with real buyers' questions, score it,
// keep the failures to fix). Sends every conversation in scripts/demo-data/chatbot-cases.mjs through the real API as a
// buyer and checks the answer: right kind of product, price range kept, policy answered, orders shown, handed to a
// human when asked, nothing unrelated shown when nothing fits.
//
// Works in both modes: without an AI key the rule-based RAG answers; with an OpenAI / Claude key (Admin > AI) the
// agent answers, and the "Hành động" cases (add to cart, recommendations) are scored too.
//
//   node scripts/chatbot-eval.mjs                               buyer31@shoppi.demo, backend on :8081
//   node scripts/chatbot-eval.mjs --email a@b.c --password X --api http://localhost:8081/api
//   node scripts/chatbot-eval.mjs --only typo,changeMind        some groups
//   node scripts/chatbot-eval.mjs --label "sau khi sửa"         name of this run in the history table
//
// Clears the buyer's chat history before each conversation (use a demo account). Writes
// review-ai-service/reports/chatbot-eval.md (scores, every failure with what the bot said) and keeps every run's
// scores in chatbot-eval-history.json, so a change can be compared with the run before.
import { CASES, GROUPS } from './demo-data/chatbot-cases.mjs'
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const args = process.argv.slice(2)
const opt = (name, def) => { const i = args.indexOf(name); return i >= 0 && args[i + 1] ? args[i + 1] : def }
const API = opt('--api', 'http://localhost:8081/api').replace(/\/$/, '')
const EMAIL = opt('--email', 'buyer31@shoppi.demo')
const PASSWORD = opt('--password', 'Demo@1234')
const ONLY = opt('--only', '').split(',').filter(Boolean)
const LABEL = opt('--label', '')
const REPORTS = join(dirname(fileURLToPath(import.meta.url)), '..', 'review-ai-service', 'reports')

const norm = (s) => (s || '').normalize('NFD').replace(/\p{M}/gu, '').replace(/[đĐ]/g, 'd').toLowerCase().replace(/\s+/g, ' ').trim()
const has = (text, phrase) => ` ${norm(text).replace(/[^a-z0-9]+/g, ' ')} `.includes(` ${norm(phrase)} `)
const price = (p) => Number(p.flashSaleActive && p.flashSalePrice != null ? p.flashSalePrice : (p.effectivePrice ?? p.price))
const vnd = (n) => Math.round(n).toLocaleString('vi-VN') + 'đ'

async function call(method, path, token, body) {
  const r = await fetch(API + path, {
    method,
    headers: { ...(body === undefined ? {} : { 'Content-Type': 'application/json' }), ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  const text = await r.text()
  let data = text
  try { data = JSON.parse(text) } catch { /* plain text */ }
  if (!r.ok) throw new Error(`${method} ${path} -> ${r.status} ${typeof data === 'string' ? data : data.message || ''}`)
  return data
}

/** [failed check, ...]; empty = the turn passed. */
function judge(expect, res) {
  const fails = []
  const cards = res.products || []
  const names = cards.map((p) => p.name)
  const want = expect.product === undefined ? null : [].concat(expect.product)
  const matches = (name) => want.some((w) => has(name, w))
  if (want) {
    if (!cards.length) fails.push(`không đưa sản phẩm nào (cần "${want.join('" / "')}")`)
    else if (!matches(names[0])) fails.push(`sản phẩm đầu "${names[0]}" không phải "${want.join('" / "')}"`)
    else if (expect.productsAll && !names.every(matches)) fails.push(`có sản phẩm lạc: ${names.filter((n) => !matches(n)).join(', ')}`)
  }
  if (expect.notProduct && cards.length && has(names[0], expect.notProduct)) fails.push(`vẫn đưa "${names[0]}" (khách đã đổi ý)`)
  const outside = cards.filter((p) => (expect.maxPrice != null && price(p) > expect.maxPrice) || (expect.minPrice != null && price(p) < expect.minPrice))
  if (outside.length) fails.push(`ngoài khoảng giá: ${outside.map((p) => `${p.name} ${vnd(price(p))}`).join(', ')}`)
  if (expect.reply && !expect.reply.some((w) => has(res.reply, w))) fails.push(`câu trả lời không nhắc "${expect.reply.join('" / "')}"`)
  if (expect.orders && !(res.orders || []).length) fails.push('không đưa đơn hàng')
  if (expect.support && !res.suggestSupport) fails.push('không đề nghị chuyển hỗ trợ')
  if (expect.noProducts && cards.length) fails.push(`đưa sản phẩm không liên quan: ${names.slice(0, 3).join(', ')}`)
  if (expect.action && !(res.actions || []).some((a) => a.type === expect.action)) fails.push(`không thực hiện ${expect.action}`)
  if (expect.step && !(res.steps || []).includes(expect.step)) fails.push(`không dùng "${expect.step}"`)
  return fails
}

async function main() {
  const login = await call('POST', '/auth/login', null, { email: EMAIL, password: PASSWORD })
  const token = login.accessToken || login.token
  const cases = CASES.filter((c) => !ONLY.length || ONLY.includes(c.group))
  const results = []
  let engine = null
  console.log(`Kiểm thử trợ lý mua sắm: ${cases.length} hội thoại, tài khoản ${EMAIL}\n`)
  for (const c of cases) {
    await call('DELETE', '/ai/history', token)
    const transcript = []
    let fails = []
    let scored = false
    let skipped = false
    for (const turn of c.turns) {
      const t0 = Date.now()
      const res = await call('POST', '/ai/chat', token, { message: turn.say })
      const usedAgent = (res.steps || []).length > 0
      engine = engine === 'AGENT' || usedAgent ? 'AGENT' : 'RAG'
      transcript.push({ say: turn.say, reply: res.reply, products: (res.products || []).map((p) => `${p.name} (${vnd(price(p))})`),
        orders: (res.orders || []).length, steps: res.steps || [], ms: Date.now() - t0 })
      if (turn.expect) {
        scored = true
        if (c.agentOnly && !usedAgent) { skipped = true; continue }
        fails = fails.concat(judge(turn.expect, res))
      }
    }
    const r = { group: c.group, heldOut: !!c.heldOut, say: c.turns.map((t) => t.say), pass: scored && !skipped && !fails.length, skipped, fails, transcript }
    results.push(r)
    const mark = skipped ? '–' : r.pass ? '✓' : '✗'
    console.log(`${mark} [${GROUPS[c.group]}${c.heldOut ? ', giữ lại' : ''}] ${r.say.join(' → ')}${fails.length ? '\n     ' + fails.join('\n     ') : ''}`)
  }
  await call('DELETE', '/ai/history', token)

  // ---------- scores ----------
  const counted = results.filter((r) => !r.skipped)
  const byGroup = {}
  for (const r of counted) {
    const g = (byGroup[r.group] ||= { pass: 0, total: 0 })
    g.total++
    if (r.pass) g.pass++
  }
  const total = counted.filter((r) => r.pass).length
  const pct = (a, b) => (b ? Math.round((100 * a) / b) : 0)
  console.log(`\nKết quả (${engine === 'AGENT' ? 'agent có API key' : 'RAG theo luật, chưa có API key'}): ${total}/${counted.length} đạt (${pct(total, counted.length)}%)`)
  for (const [g, s] of Object.entries(byGroup)) console.log(`  ${GROUPS[g].padEnd(28)} ${String(s.pass).padStart(2)}/${s.total}  ${pct(s.pass, s.total)}%`)
  // held-out: written after the bot was fixed on the other cases, never used to fix it: how it does on NEW questions
  const held = counted.filter((r) => r.heldOut)
  const heldPass = held.filter((r) => r.pass).length
  const known = counted.filter((r) => !r.heldOut)
  const knownPass = known.filter((r) => r.pass).length
  if (held.length) console.log(`  Bộ đã dùng để sửa: ${knownPass}/${known.length} (${pct(knownPass, known.length)}%)   bộ giữ lại (câu mới): ${heldPass}/${held.length} (${pct(heldPass, held.length)}%)`)
  const skipped = results.filter((r) => r.skipped).length
  if (skipped) console.log(`  (${skipped} hội thoại cần agent có API key, không tính)`)

  // ---------- report + history ----------
  mkdirSync(REPORTS, { recursive: true })
  const historyFile = join(REPORTS, 'chatbot-eval-history.json')
  let history = []
  try { history = JSON.parse(readFileSync(historyFile, 'utf8')) } catch { /* first run */ }
  const run = { at: new Date().toISOString(), label: LABEL, engine, pass: total, total: counted.length,
    groups: Object.fromEntries(Object.entries(byGroup).map(([g, s]) => [g, [s.pass, s.total]])),
    known: [knownPass, known.length], heldOut: [heldPass, held.length] }
  if (!ONLY.length) history.push(run)
  writeFileSync(historyFile, JSON.stringify(history, null, 1))

  const lines = [`# Kiểm thử trợ lý mua sắm (${new Date().toLocaleString('vi-VN')})`, '',
    `Chế độ: **${engine === 'AGENT' ? 'agent (mô hình ngôn ngữ có công cụ)' : 'RAG theo luật (chưa có API key)'}**. ` +
    `${counted.length} hội thoại, đạt **${total} (${pct(total, counted.length)}%)**.`, '',
    ...(held.length ? [`Bộ đã dùng để sửa bot: ${knownPass}/${known.length} (${pct(knownPass, known.length)}%). ` +
      `**Bộ giữ lại** (câu mới, chưa từng dùng để sửa): **${heldPass}/${held.length} (${pct(heldPass, held.length)}%)**: con số này mới cho biết bot làm tốt với câu khách thật hỏi.`, ''] : []),
    '| Nhóm câu hỏi | Đạt | Tỉ lệ |', '|---|---|---|',
    ...Object.entries(byGroup).map(([g, s]) => `| ${GROUPS[g]} | ${s.pass}/${s.total} | ${pct(s.pass, s.total)}% |`), '']
  const full = history.filter((h) => h.total >= 10).slice(-8)
  if (full.length > 1) {
    const groups = Object.keys(GROUPS).filter((g) => full.some((h) => h.groups[g]))
    lines.push('## Các lần chạy', '', `| Lần chạy | Chế độ | Tổng | Bộ giữ lại | ${groups.map((g) => GROUPS[g]).join(' | ')} |`,
      `|---|---|---|---|${groups.map(() => '---').join('|')}|`)
    for (const h of full) {
      lines.push(`| ${new Date(h.at).toLocaleString('vi-VN')}${h.label ? ' ' + h.label : ''} | ${h.engine} | ${pct(h.pass, h.total)}% | ` +
        `${h.heldOut && h.heldOut[1] ? `${h.heldOut[0]}/${h.heldOut[1]}` : ''} | ` +
        groups.map((g) => (h.groups[g] ? `${h.groups[g][0]}/${h.groups[g][1]}` : '')).join(' | ') + ' |')
    }
    lines.push('')
  }
  const failed = results.filter((r) => !r.pass && !r.skipped)
  lines.push(`## Ca trả lời sai (${failed.length}): dữ liệu để sửa tiếp`, '')
  for (const r of failed) {
    lines.push(`### [${GROUPS[r.group]}${r.heldOut ? ', giữ lại' : ''}] ${r.say.join(' → ')}`, '', ...r.fails.map((f) => `- ❌ ${f}`), '')
    for (const t of r.transcript) {
      lines.push(`> **Khách:** ${t.say}  `, `> **Bot:** ${t.reply.replace(/\n+/g, ' ').slice(0, 300)}  `)
      if (t.products.length) lines.push(`> *Thẻ sản phẩm:* ${t.products.slice(0, 5).join('; ')}`)
      lines.push('')
    }
  }
  writeFileSync(join(REPORTS, 'chatbot-eval.md'), lines.join('\n'))
  console.log(`\n→ báo cáo: review-ai-service/reports/chatbot-eval.md`)
}

main().catch((e) => { console.error(e.message); process.exit(1) })
