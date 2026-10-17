import { http, unwrap } from './http'
import type { FenceDto } from '../types'

export function listFences() {
  return unwrap<FenceDto[]>(http.get('/fence/list'))
}

export function createFence(fence: FenceDto) {
  return unwrap<FenceDto>(http.post('/fence', fence))
}

export function deleteFence(fenceId: number) {
  return unwrap<void>(http.delete(`/fence/${fenceId}`))
}
