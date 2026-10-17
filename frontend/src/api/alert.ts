import { http, unwrap } from './http'
import type { FenceAlert } from '../types'

export function listAlerts() {
  return unwrap<FenceAlert[]>(http.get('/alerts/list'))
}

/** SSE 告警流（EventSource 订阅） */
export function subscribeAlerts(onAlert: (a: FenceAlert) => void): EventSource {
  const es = new EventSource('/api/alerts/stream')
  es.addEventListener('alert', e => {
    onAlert(JSON.parse((e as MessageEvent).data) as FenceAlert)
  })
  return es
}
