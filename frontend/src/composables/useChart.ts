import { onBeforeUnmount, onMounted, type Ref } from 'vue'
import echarts from '../utils/echarts'

type Chart = ReturnType<typeof echarts.init>

/**
 * ECharts 容器尺寸自适应：惰性 init（容器有尺寸时才创建）+ ResizeObserver 跟随容器变化 resize。
 * 解决两个问题：①组件挂载早于布局完成时容器 0 尺寸导致画布空白；②窗口/面板尺寸变化后画布不跟随。
 */
export function useChart(el: Ref<HTMLElement | undefined>) {
  let chart: Chart | null = null
  let observer: ResizeObserver | null = null
  let readyCallbacks: ((c: Chart) => void)[] = []

  function tryInit(): Chart | null {
    if (chart || !el.value) {
      return chart
    }
    if (el.value.clientWidth === 0 || el.value.clientHeight === 0) {
      return null
    }
    chart = echarts.init(el.value)
    const cbs = readyCallbacks
    readyCallbacks = []
    for (const cb of cbs) {
      cb(chart)
    }
    return chart
  }

  /** 惰性获取实例；容器尚无尺寸时返回 null（由观察器在首次有尺寸时触发 onReady） */
  function ensure(): Chart | null {
    return tryInit()
  }

  /** 首次可绘制时回调（容器拿到非 0 尺寸），只触发一次 */
  function onReady(cb: (c: Chart) => void): void {
    const c = tryInit()
    if (c) {
      cb(c)
    } else {
      readyCallbacks.push(cb)
    }
  }

  onMounted(() => {
    observer = new ResizeObserver(() => {
      if (!chart) {
        tryInit()
        return
      }
      chart.resize()
    })
    if (el.value) {
      observer.observe(el.value)
    }
  })

  onBeforeUnmount(() => {
    observer?.disconnect()
    chart?.dispose()
    chart = null
  })

  return { ensure, onReady }
}
