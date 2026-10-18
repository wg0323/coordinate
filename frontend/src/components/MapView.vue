<template>
  <div id="map" class="map-view" :class="{ 'map-dark': dark }"></div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'
import L from 'leaflet'
import { setMap } from '../mapStore'

// dark: 大屏深色模式（CSS 滤镜方案——天地图无官方深色样式）
const props = defineProps<{ dark?: boolean }>()

let map: L.Map | null = null
const onWinResize = () => map?.invalidateSize()

onMounted(() => {
  map = L.map('map', { zoomControl: true }).setView([39.9, 116.4], 12)
  // 天地图（CGCS2000 工程上当 WGS84 用，无偏移）：vec_w 矢量底图 + cva_w 中文注记两层叠加
  // OSM 瓦片在国内网络不可达；高德为 GCJ-02 会与 WGS84 数据错位
  const tk = 'fed715233e077afd01167f04904d6575'
  const tdtLayer = (type: string) =>
    L.tileLayer(`https://t{s}.tianditu.gov.cn/DataServer?T=${type}&x={x}&y={y}&l={z}&tk=${tk}`, {
      subdomains: ['0', '1', '2', '3', '4', '5', '6', '7'],
      maxZoom: 18,
      attribution: '&copy; 天地图'
    })
  tdtLayer('vec_w').addTo(map)
  tdtLayer('cva_w').addTo(map)
  setMap(map)
  // 容器尺寸随窗口/面板变化后 Leaflet 需显式重算
  window.addEventListener('resize', onWinResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onWinResize)
  // 销毁地图实例：移除所有图层/瓦片/画布（HMR 或整页重建时避免旧实例滞留）
  map?.remove()
  map = null
})
</script>

<style scoped>
.map-view {
  height: 100%;
  width: 100%;
}
/* 深色大屏：仅对底图瓦片反转亮度 + 色相旋转，覆盖物（散点/围栏/热力）保持真实颜色 */
.map-dark :deep(.leaflet-tile-pane) {
  filter: invert(1) hue-rotate(180deg) brightness(0.9) contrast(1.1);
}
</style>
