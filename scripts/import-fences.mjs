/**
 * 真实围栏导入脚本：解析 docs/b_fence.sql（sbike 平台导出）→ POST /api/fence
 *
 * 用法：node scripts/import-fences.mjs [--limit 100] [--offset 0] [--type-all 停放]
 *   --limit N    只导入前 N 条（小批量目测坐标系用）
 *   --offset N   跳过前 N 条
 *   --all        忽略 limit 全量导入
 *
 * 字段映射：f_id→日志, f_name→名称(加禁停/停放前缀), f_fence→多边形, f_limit/f_fence_type→编码进名称
 * 注意：fenceId 传 null 由 api-server 自增分配；导入即经 Kafka 广播给 FenceJob
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const sqlFile = path.join(root, 'docs', 'b_fence.sql')
const API = 'http://localhost:8080/api/fence'

const args = process.argv.slice(2)
const getArg = (k, d) => {
  const i = args.indexOf(k)
  return i >= 0 ? args[i + 1] : d
}
const hasFlag = k => args.includes(k)
const limit = hasFlag('--all') ? Infinity : parseInt(getArg('--limit', '100'), 10)
const offset = parseInt(getArg('--offset', '0'), 10)

const LINE_RE =
  /^INSERT INTO `b_fence` VALUES \((\d+),\s*'((?:[^'\\]|\\.)*)',\s*'((?:[^'\\]|\\.)*)',\s*(?:'(?:[^'\\]|\\.)*'|NULL),\s*(?:'(?:[^'\\]|\\.)*'|NULL),\s*(\d+),\s*(?:'(?:[^'\\]|\\.)*'|NULL),\s*(-?\d+),/

/** 还原 MySQL 字符串转义；部分历史数据是双重转义，循环还原到不再含反斜杠 */
const unescapeSql = s => {
  let prev
  while (prev !== s) {
    prev = s
    s = s.replace(/\\(.)/g, '$1')
  }
  return s
}

// ---------- GCJ-02 → WGS-84（迭代近似，手册 3.1.2：无解析精确逆） ----------
const PI = Math.PI
const A = 6378245.0
const EE = 0.00669342162296594323
const tLat = (x, y) => {
  let r = -100 + 2 * x + 3 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x))
  r += ((20 * Math.sin(6 * x * PI) + 20 * Math.sin(2 * x * PI)) * 2) / 3
  r += ((20 * Math.sin(y * PI) + 40 * Math.sin((y / 3) * PI)) * 2) / 3
  r += ((160 * Math.sin((y / 12) * PI) + 320 * Math.sin((y * PI) / 30)) * 2) / 3
  return r
}
const tLng = (x, y) => {
  let r = 300 + x + 2 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x))
  r += ((20 * Math.sin(6 * x * PI) + 20 * Math.sin(2 * x * PI)) * 2) / 3
  r += ((20 * Math.sin(x * PI) + 40 * Math.sin((x / 3) * PI)) * 2) / 3
  r += ((150 * Math.sin((x / 12) * PI) + 300 * Math.sin((x / 30) * PI)) * 2) / 3
  return r
}
const delta = (lng, lat) => {
  const radLat = (lat / 180) * PI
  let magic = Math.sin(radLat)
  magic = 1 - EE * magic * magic
  const sq = Math.sqrt(magic)
  const dLat = (tLat(lng - 105, lat - 35) * 180) / (((A * (1 - EE)) / (magic * sq)) * PI)
  const dLng = (tLng(lng - 105, lat - 35) * 180) / ((A / sq) * Math.cos(radLat) * PI)
  return [dLng, dLat]
}
const gcj2wgs = (lng, lat) => {
  let [wLng, wLat] = [lng, lat]
  for (let i = 0; i < 3; i++) {
    const [dLng, dLat] = delta(wLng, wLat)
    wLng = lng - dLng
    wLat = lat - dLat
  }
  return [wLng, wLat]
}
const gcj2wgsMode = hasFlag('--gcj2wgs')

const sql = fs.readFileSync(sqlFile, 'utf8')
const candidates = []
let regexMiss = 0
let missSample = ''
for (const line of sql.split('\n')) {
  if (!line.startsWith('INSERT INTO `b_fence` VALUES')) continue
  const m = LINE_RE.exec(line)
  if (!m) {
    regexMiss++
    if (!missSample) missSample = line.slice(0, 200)
    continue
  }
  const [, id, name, fenceJson, fenceLimit, status] = m
  if (status !== '1') continue // -1 已删除
  // f_fence_type 解析：行尾 `, <type>, '<area_code>');`（type 在 f_update_time 之后）
  const typeMatch = /, (\d+), '\d{6}'\);$/.exec(line.trim())
  const fenceType = typeMatch ? Number(typeMatch[1]) : 1
  candidates.push({
    id: Number(id),
    name: unescapeSql(name),
    fenceJson: unescapeSql(fenceJson),
    fenceLimit: Number(fenceLimit),
    fenceType
  })
}
if (regexMiss > 0) {
  console.log(`[诊断] ${regexMiss} 行未匹配 LINE_RE，首例：${missSample}`)
}

console.log(`SQL 内有效围栏共 ${candidates.length} 条，本次导入 offset=${offset} limit=${limit === Infinity ? 'ALL' : limit}`)

let ok = 0
let fail = 0
const batch = candidates.slice(offset, offset + limit)
const t0 = Date.now()
for (let i = 0; i < batch.length; i++) {
  const { id, name, fenceJson, fenceLimit, fenceType } = batch[i]
  let points
  try {
    points = JSON.parse(fenceJson)
  } catch {
    console.log(`[跳过] #${id} ${name}: f_fence JSON 解析失败`)
    fail++
    continue
  }
  if (!Array.isArray(points) || points.length < 3) {
    console.log(`[跳过] #${id} ${name}: 顶点数 ${Array.isArray(points) ? points.length : 0} < 3`)
    fail++
    continue
  }
  // 统一为 [lng, lat] 数组格式（后续几何检查均按下标取值）
  points = points.map(p => [p.lng, p.lat])
  // 退化围栏过滤：按鞋垫公式算实际面积（外包框会误杀几米宽的真实停车位围栏）。
  // GPS 来回走会产出"有外包框、零面积"的拉锯形碎片，MongoDB 2dsphere 写入时 500
  const lngs = points.map(p => p[0])
  const lats = points.map(p => p[1])
  let shoe = 0
  for (let i = 0; i < points.length; i++) {
    const a = points[i]
    const b = points[(i + 1) % points.length]
    shoe += a[0] * b[1] - b[0] * a[1]
  }
  const areaM2 = (Math.abs(shoe) / 2) * 111320 * 111320 * Math.cos((points[0][1] * PI) / 180)
  // 相邻重复点：GeoJSON 环不允许
  let dupAdj = false
  for (let i = 0; i < points.length; i++) {
    const a = points[i]
    const b = points[(i + 1) % points.length]
    if (a[0] === b[0] && a[1] === b[1]) {
      dupAdj = true
      break
    }
  }
  if (dupAdj) {
    console.log(`[跳过] #${id} ${name}: 相邻重复点`)
    fail++
    continue
  }
  // 自相交（拉锯形/蝴蝶形）：MongoDB 2dsphere 校验环必须简单，自相交直接 500
  let selfCross = false
  outer: for (let i = 0; i < points.length; i++) {
    for (let j = i + 2; j < points.length; j++) {
      if (i === 0 && j === points.length - 1) continue // 首尾边共享端点
      const p1 = points[i]
      const p2 = points[(i + 1) % points.length]
      const p3 = points[j]
      const p4 = points[(j + 1) % points.length]
      const d = (a, b, c) => (b[0] - a[0]) * (c[1] - a[1]) - (c[0] - a[0]) * (b[1] - a[1])
      const d1 = d(p3, p4, p1)
      const d2 = d(p3, p4, p2)
      const d3 = d(p1, p2, p3)
      const d4 = d(p1, p2, p4)
      if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) {
        selfCross = true
        break outer
      }
    }
  }
  if (selfCross) {
    console.log(`[跳过] #${id} ${name}: 自相交多边形`)
    fail++
    continue
  }
  if (areaM2 < 0.5) {
    console.log(`[跳过] #${id} ${name}: 面积仅 ${areaM2.toFixed(3)} m²（退化碎片）`)
    fail++
    continue
  }
  const tag = fenceType === 0 ? '[禁停' : '[停放'
  const pts = points.map(p => (gcj2wgsMode ? gcj2wgs(p[0], p[1]) : p))
  if (i === 0 && gcj2wgsMode) {
    console.log(
      `[样例] #${id} 首点 GCJ02(${points[0][0]}, ${points[0][1]}) → WGS84(${pts[0][0].toFixed(6)}, ${pts[0][1].toFixed(6)})`
    )
  }
  const dto = {
    fenceId: null,
    name: `${tag}${fenceLimit}] ${name}`,
    coordinates: pts
  }
  try {
    const res = await fetch(API, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(dto)
    })
    const body = await res.json()
    if (body.code !== 0) throw new Error(body.msg)
    ok++
  } catch (e) {
    console.log(`[失败] #${id} ${name}: ${e.message}`)
    fail++
  }
  if ((i + 1) % 100 === 0) console.log(`... 进度 ${i + 1}/${batch.length}`)
}

console.log(`完成：成功 ${ok}，失败/跳过 ${fail}，耗时 ${((Date.now() - t0) / 1000).toFixed(1)}s`)
