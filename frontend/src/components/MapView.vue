<template>
  <div id="map" class="map-view" :class="{ 'map-dark': dark }"></div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import L from 'leaflet'
import { setMap } from '../mapStore'

// dark: 大屏深色模式（CSS 滤镜方案——天地图无官方深色样式）
const props = defineProps<{ dark?: boolean }>()

onMounted(() => {
  const map = L.map('map', { zoomControl: true }).setView([39.9, 116.4], 12)
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
})
</script>

<style scoped>
.map-view {
  height: 100%;
  width: 100%;
}
/* 深色大屏：反转亮度 + 色相旋转，输出近似暗夜蓝底图 */
.map-dark {
  filter: invert(1) hue-rotate(180deg) brightness(0.9) contrast(1.1);
}
</style>
