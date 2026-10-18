/** 千分位格式化：84840 → 84,840 */
export function fmtNum(n: number | null | undefined): string {
  if (n === null || n === undefined || Number.isNaN(n)) {
    return '-'
  }
  return n.toLocaleString('en-US')
}

/** 百分比：0.985 → 98.5% */
export function fmtPct(rate: number | null | undefined): string {
  if (rate === null || rate === undefined || Number.isNaN(rate)) {
    return '-'
  }
  return `${(rate * 100).toFixed(1)}%`
}
