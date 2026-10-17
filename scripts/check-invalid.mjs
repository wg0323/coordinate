/** 对导入失败的围栏做几何体检：重复相邻点 / 自相交 / 面积分布 */
import fs from 'node:fs'

const LINE_RE =
  /^INSERT INTO `b_fence` VALUES \((\d+),\s*'((?:[^'\\]|\\.)*)',\s*'((?:[^'\\]|\\.)*)',\s*(?:'(?:[^'\\]|\\.)*'|NULL),\s*(?:'(?:[^'\\]|\\.)*'|NULL),\s*(\d+),\s*(?:'(?:[^'\\]|\\.)*'|NULL),\s*(-?\d+),/
const unescapeSql = s => {
  let prev
  while (prev !== s) {
    prev = s
    s = s.replace(/\\(.)/g, '$1')
  }
  return s
}
const PI = Math.PI
const areaM2 = pts => {
  let s = 0
  for (let i = 0; i < pts.length; i++) {
    const a = pts[i]
    const b = pts[(i + 1) % pts.length]
    s += a[0] * b[1] - b[0] * a[1]
  }
  return (Math.abs(s) / 2) * 111320 * 111320 * Math.cos((pts[0][1] * PI) / 180)
}
// 线段相交（不含共享端点）
const segCross = (p1, p2, p3, p4) => {
  const d = (a, b, c) => (b[0] - a[0]) * (c[1] - a[1]) - (c[0] - a[0]) * (b[1] - a[1])
  const d1 = d(p3, p4, p1)
  const d2 = d(p3, p4, p2)
  const d3 = d(p1, p2, p3)
  const d4 = d(p1, p2, p4)
  return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
}
const selfIntersect = pts => {
  const n = pts.length
  for (let i = 0; i < n; i++) {
    for (let j = i + 2; j < n; j++) {
      if (i === 0 && j === n - 1) continue // 首尾边相邻
      if (segCross(pts[i], pts[(i + 1) % n], pts[j], pts[(j + 1) % n])) return true
    }
  }
  return false
}
const hasDupAdj = pts => {
  for (let i = 0; i < pts.length; i++) {
    const a = pts[i]
    const b = pts[(i + 1) % pts.length]
    if (a[0] === b[0] && a[1] === b[1]) return true
  }
  return false
}

const sql = fs.readFileSync('docs/b_fence.sql', 'utf8')
const stats = { dup: 0, cross: 0, tiny: 0, ok: 0 }
const crossSamples = []
for (const line of sql.split('\n')) {
  if (!line.startsWith('INSERT INTO `b_fence` VALUES')) continue
  const m = LINE_RE.exec(line)
  if (!m) continue
  const points = JSON.parse(unescapeSql(m[3])).map(p => [p.lng, p.lat])
  if (areaM2(points) < 0.5) {
    stats.tiny++
  } else if (hasDupAdj(points)) {
    stats.dup++
  } else if (selfIntersect(points)) {
    stats.cross++
    if (crossSamples.length < 5) crossSamples.push(`${m[1]} ${unescapeSql(m[2])}`)
  } else {
    stats.ok++
  }
}
console.log('面积<0.5m²:', stats.tiny)
console.log('相邻重复点:', stats.dup)
console.log('自相交:', stats.cross, crossSamples.length ? `样例: ${crossSamples.join(' | ')}` : '')
console.log('几何正常:', stats.ok)
