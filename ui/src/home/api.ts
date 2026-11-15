export type ServiceStatus = { id: 'notion' | 'db'; name: string; up: boolean }

/** Asks the gateway which services are running. Null means the gateway itself did not answer. */
export async function fetchServices(): Promise<ServiceStatus[] | null> {
  try {
    const res = await fetch('/api/services')
    if (!res.ok) return null
    return (await res.json()) as ServiceStatus[]
  } catch {
    return null
  }
}
