export type Workspace = { id: string; name: string; type: string }

const BASE_URL = '/api'

export async function getWorkspaces(): Promise<Workspace[]> {
  const response = await fetch(`${BASE_URL}/workspaces`)
  if (!response.ok) throw new Error(`getWorkspaces failed: ${response.status}`)
  return response.json()
}
