import { http, unwrap } from './http'
import type { HeatCellDto } from '../types'

export function fetchHeat() {
  return unwrap<HeatCellDto[]>(http.get('/heat'))
}
