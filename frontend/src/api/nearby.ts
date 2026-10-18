import { http, unwrap } from './http'
import type { NearbyBike } from '../types'

/** 视野内车辆点位（大屏 BikeLayer 数据源） */
export function fetchBBox(minLng: number, minLat: number, maxLng: number, maxLat: number) {
  return unwrap<NearbyBike[]>(http.get('/points/bbox', { params: { minLng, minLat, maxLng, maxLat } }))
}
