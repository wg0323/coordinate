// 一次性拉取北京中心城区可骑行路网，固化为模拟器资源。运行：node scripts/fetch-roadnet.mjs
import { writeFileSync, mkdirSync } from 'node:fs'

const BBOX = '39.85,116.32,40.01,116.55' // south,west,north,east
const OUT = 'device-simulator/src/main/resources/roadnet/beijing-center.json'
const HIGHWAYS = '^(primary|secondary|tertiary|unclassified|residential|living_street|service|cycleway)$'
const QUERY = `[out:json][timeout:300];way[highway~"${HIGHWAYS}"](${BBOX});out body;>;out skel qt;`
const ENDPOINT = 'https://overpass-api.de/api/interpreter'

async function fetchOverpass() {
  for (let attempt = 0; attempt < 4; attempt++) {
    const res = await fetch(`${ENDPOINT}?data=${encodeURIComponent(QUERY)}`, {
      headers: { 'User-Agent': 'coordinates-demo/1.0 (roadnet snapshot)' }
    })
    if (res.ok) return res.json()
    const wait = 30_000 * Math.pow(2, attempt)
    console.log(`[重试] Overpass ${res.status}，${wait / 1000}s 后重试`)
    await new Promise(r => setTimeout(r, wait))
  }
  throw new Error('Overpass 拉取失败（已重试 4 次）')
}

const raw = await fetchOverpass()
const ways = raw.elements.filter(e => e.type === 'way' && e.nodes && e.tags)
const nodeCoord = new Map()
for (const e of raw.elements) {
  if (e.type === 'node') nodeCoord.set(e.id, [e.lon, e.lat])
}

// 共享节点（≥2 条 way 引用）必须保留，保证简化后路网仍连通
const useCount = new Map()
for (const w of ways) {
  for (const id of w.nodes) useCount.set(id, (useCount.get(id) || 0) + 1)
}

// 距离（米）：小范围平面近似
function distM(a, b) {
  const kx = 111320 * Math.cos(((a[1] + b[1]) / 2) * Math.PI / 180)
  const ky = 111320
  return Math.hypot((a[0] - b[0]) * kx, (a[1] - b[1]) * ky)
}

// Douglas-Peucker：保留首尾；shared=true 的点强制保留
function simplify(coords, shared, toleranceM) {
  if (coords.length <= 2) return coords
  const keep = new Array(coords.length).fill(false)
  keep[0] = true
  keep[coords.length - 1] = true
  for (let i = 1; i < coords.length - 1; i++) {
    if (shared[i]) keep[i] = true
  }
  const kx = 111320 * Math.cos((coords[0][1] * Math.PI) / 180)
  const xy = coords.map(p => [p[0] * kx, p[1] * 111320])
  const rec = (s, e) => {
    if (e <= s + 1) return
    const [ax, ay] = xy[s]
    const [bx, by] = xy[e]
    const dx = bx - ax
    const dy = by - ay
    const norm = Math.hypot(dx, dy) || 1
    let maxD = -1
    let maxI = -1
    for (let i = s + 1; i < e; i++) {
      const [px, py] = xy[i]
      const d = Math.abs(dy * (px - ax) - dx * (py - ay)) / norm
      if (d > maxD) { maxD = d; maxI = i }
    }
    if (maxD >= toleranceM) {
      keep[maxI] = true
      rec(s, maxI)
      rec(maxI, e)
    }
  }
  rec(0, coords.length - 1)
  return coords.filter((_, i) => keep[i])
}

// 1) 过滤 way 并简化，产出片段（保留原 OSM node id）
const segments = []
for (const w of ways) {
  const tags = w.tags
  if (tags.access === 'no' || tags.access === 'private' || tags.bicycle === 'no') continue
  const coords = []
  const ids = []
  for (const id of w.nodes) {
    const c = nodeCoord.get(id)
    if (c) { coords.push(c); ids.push(id) }
  }
  if (coords.length < 2) continue
  const shared = ids.map(id => (useCount.get(id) || 0) >= 2)
  const kept = simplify(coords, shared, 0.5)
  const keptIds = []
  let cursor = 0
  for (const c of kept) {
    while (cursor < coords.length && coords[cursor] !== c) cursor++
    keptIds.push(ids[cursor])
  }
  segments.push({ ids: keptIds, coords: kept })
}

// 2) 建立索引与无向图
const nodeIndex = new Map()
const nodes = []
const edges = []
for (const seg of segments) {
  let prev = -1
  for (let i = 0; i < seg.ids.length; i++) {
    const id = seg.ids[i]
    let idx = nodeIndex.get(id)
    if (idx === undefined) {
      idx = nodes.length
      nodeIndex.set(id, idx)
      nodes.push(seg.coords[i])
    }
    if (prev >= 0) {
      edges.push([prev, idx, distM(nodes[prev], nodes[idx])])
    }
    prev = idx
  }
}

// 3) 最大连通分量
const adj = Array.from({ length: nodes.length }, () => [])
for (const [a, b] of edges) { adj[a].push(b); adj[b].push(a) }
const comp = new Int32Array(nodes.length).fill(-1)
let bestComp = -1
let bestSize = 0
let compId = 0
for (let s = 0; s < nodes.length; s++) {
  if (comp[s] !== -1) continue
  let size = 0
  const queue = [s]
  comp[s] = compId
  while (queue.length) {
    const cur = queue.pop()
    size++
    for (const nb of adj[cur]) {
      if (comp[nb] === -1) { comp[nb] = compId; queue.push(nb) }
    }
  }
  if (size > bestSize) { bestSize = size; bestComp = compId }
  compId++
}

// 4) 重建索引，只保留最大分量
const remap = new Map()
const outNodes = []
const outEdges = []
for (let i = 0; i < nodes.length; i++) {
  if (comp[i] !== bestComp) continue
  remap.set(i, outNodes.length)
  outNodes.push(nodes[i])
}
for (const [a, b, len] of edges) {
  if (remap.has(a) && remap.has(b)) outEdges.push([remap.get(a), remap.get(b), Math.round(len * 10) / 10])
}

const totalKm = outEdges.reduce((s, e) => s + e[2], 0) / 1000
const coverage = ((bestSize / nodes.length) * 100).toFixed(1)
console.log(`路网：节点 ${outNodes.length}，边 ${outEdges.length}，总里程 ${totalKm.toFixed(1)}km，最大连通分量覆盖率 ${coverage}%`)

const out = {
  source: 'OpenStreetMap',
  license: 'ODbL 1.0',
  bbox: [116.32, 39.85, 116.55, 40.01],
  generatedAt: new Date().toISOString().slice(0, 10),
  nodes: outNodes.map(p => [Math.round(p[0] * 1e6) / 1e6, Math.round(p[1] * 1e6) / 1e6]),
  edges: outEdges
}
mkdirSync('device-simulator/src/main/resources/roadnet', { recursive: true })
writeFileSync(OUT, JSON.stringify(out))
console.log(`已写入 ${OUT}，大小 ${(JSON.stringify(out).length / 1024 / 1024).toFixed(2)}MB`)
