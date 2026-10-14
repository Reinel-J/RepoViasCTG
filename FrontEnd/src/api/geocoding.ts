import client from './client'
import type { GeocodingInversoResponse } from './types'

export async function inverso(lat: number, lng: number): Promise<GeocodingInversoResponse> {
  const response = await client.get<GeocodingInversoResponse>('/geocoding/inverso', { params: { lat, lng } })
  return response.data
}
