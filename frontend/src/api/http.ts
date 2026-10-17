import axios from 'axios'
import type { ApiResult } from '../types'

/** 统一 axios 实例：开发期由 Vite proxy 转发 /api（免 CORS） */
export const http = axios.create({ baseURL: '/api', timeout: 15000 })

export async function unwrap<T>(p: Promise<{ data: ApiResult<T> }>): Promise<T> {
  const resp = await p
  if (resp.data.code !== 0) {
    throw new Error(resp.data.msg)
  }
  return resp.data.data
}
