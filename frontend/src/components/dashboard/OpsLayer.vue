<template><span></span></template>

<script setup lang="ts">
import { onMounted } from 'vue'
import L from 'leaflet'
import { getMapAsync } from '../../mapStore'
import { OPS_VEHICLES } from '../../mock/dashboard'

// 运维调度车辆静态占位层：卡车图标 + tooltip
onMounted(async () => {
  const map = await getMapAsync()
  const group = L.layerGroup()
  for (const v of OPS_VEHICLES) {
    L.marker([v.lat, v.lng], {
      icon: L.divIcon({
        className: 'ops-marker',
        html: '<div class="ops-icon">🚚</div>',
        iconSize: [24, 24],
        iconAnchor: [12, 12]
      })
    })
      .bindTooltip(`${v.id} ${v.label}`)
      .addTo(group)
  }
  group.addTo(map)
})
</script>

<style>
.ops-marker .ops-icon {
  font-size: 18px;
  line-height: 24px;
  text-align: center;
  filter: drop-shadow(0 0 4px rgba(0, 212, 255, 0.8));
}
</style>
