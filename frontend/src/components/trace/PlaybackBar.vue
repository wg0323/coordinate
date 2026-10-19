<template>
  <div class="playback">
    <button class="btn" @click="toggle">{{ playing ? '暂停' : '播放' }}</button>
    <input
      class="range"
      type="range"
      min="0"
      max="1000"
      :value="progress"
      @input="onInput"
    />
    <span class="num time">{{ timeText }}</span>
    <span class="num speed">{{ speedText }}</span>
    <select v-model.number="factor" class="factor">
      <option :value="1">1x</option>
      <option :value="30">30x</option>
      <option :value="120">120x</option>
      <option :value="600">600x</option>
    </select>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import type { TracePoint } from '../../types'
import { positionAt } from '../../utils/trace'

const props = defineProps<{ points: TracePoint[]; ts: number }>()
const emit = defineEmits<{ 'update:ts': [number] }>()

const playing = ref(false)
const factor = ref(600)
let timer: number | null = null

const startTs = computed(() => props.points[0]?.ts ?? 0)
const endTs = computed(() => props.points[props.points.length - 1]?.ts ?? 0)
const progress = computed(() =>
  endTs.value > startTs.value ? ((props.ts - startTs.value) / (endTs.value - startTs.value)) * 1000 : 0
)
const pos = computed(() => positionAt(props.points, props.ts))
const timeText = computed(() => new Date(props.ts).toLocaleString('zh-CN', { hour12: false }))
const speedText = computed(() => `${(pos.value.speed * 3.6).toFixed(1)} km/h`)

function onInput(e: Event) {
  const value = Number((e.target as HTMLInputElement).value)
  emit('update:ts', startTs.value + (value / 1000) * (endTs.value - startTs.value))
}

function stop() {
  playing.value = false
  if (timer !== null) {
    clearInterval(timer)
    timer = null
  }
}

function toggle() {
  if (playing.value) {
    stop()
    return
  }
  if (props.ts >= endTs.value) {
    emit('update:ts', startTs.value)
  }
  playing.value = true
  timer = window.setInterval(() => {
    const next = props.ts + 100 * factor.value
    if (next >= endTs.value) {
      emit('update:ts', endTs.value)
      stop()
    } else {
      emit('update:ts', next)
    }
  }, 100)
}

watch(() => props.points, stop)
onBeforeUnmount(stop)
</script>

<style scoped>
.playback {
  display: flex;
  align-items: center;
  gap: 16px;
  width: 100%;
}
.btn {
  width: 80px;
  padding: 8px 0;
  background: rgba(0, 212, 255, 0.15);
  border: 1px solid var(--accent);
  border-radius: 3px;
  color: var(--accent);
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 1px;
  cursor: pointer;
}
.btn:hover {
  background: rgba(0, 212, 255, 0.25);
}
.range {
  flex: 1;
  accent-color: var(--accent);
}
.time {
  font-size: 15px;
  color: var(--text-main);
  min-width: 160px;
}
.speed {
  font-size: 15px;
  color: var(--accent);
  min-width: 100px;
}
.factor {
  background: rgba(0, 0, 0, 0.3);
  border: 1px solid var(--panel-border);
  border-radius: 3px;
  color: var(--text-main);
  padding: 6px;
  font-size: 14px;
  color-scheme: dark;
}
</style>
