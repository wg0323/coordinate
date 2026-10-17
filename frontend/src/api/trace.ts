import { http, unwrap } from './http'
import type { SimilarityResult, TraceResponse } from '../types'

export function fetchTrace(bikeId: string, from: number, to: number, threshold: number) {
  return unwrap<TraceResponse>(http.get('/trace', { params: { bikeId, from, to, threshold } }))
}

export function fetchSimilarity(bikeA: string, bikeB: string, from: number, to: number) {
  return unwrap<SimilarityResult>(http.get('/similarity', { params: { bikeA, bikeB, from, to } }))
}
