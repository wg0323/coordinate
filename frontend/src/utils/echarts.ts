/**
 * ECharts 按需注册（共享模块）：所有面板统一从此引入，避免整包 1.2MB。
 * 新图表类型在此追加注册。
 */
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([PieChart, BarChart, LineChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

export default echarts
export type EChartsType = ReturnType<typeof echarts.init>
