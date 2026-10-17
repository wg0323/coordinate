import { http, unwrap } from './http'
import type { NearbyBike } from '../types'

export function fetchNearby(lng: number, lat: number, radiusM = 1000, count = 100) {
  return unwrap<NearbyBike[]>(http.get('/nearby', { params: { lng, lat, radiusM, count } }))
}

export function fetchBBox(minLng: number, minLat: number, maxLng: number, maxLat: number) {
  return unwrap<NearbyBike[]>(http.get('/points/bbox', { params: { minLng, minLat, maxLng, maxLat } }))
}
