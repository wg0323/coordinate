import { onBeforeUnmount, onMounted } from 'vue'

/**
 * 轮询组合式函数：立即执行一次 + 定时执行。
 * 异常吞掉由调用方在 fn 内处理（保留旧值策略由各面板自定）。
 */
export function usePolling(fn: () => Promise<void> | void, intervalMs: number) {
  let timer: number | null = null
  onMounted(() => {
    fn()
    timer = window.setInterval(fn, intervalMs)
  })
  onBeforeUnmount(() => {
    if (timer) {
      clearInterval(timer)
    }
  })
}
