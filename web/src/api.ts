export type Workspace = { id: string; name: string; type: string }

export type Collection = { id: string; name: string; uid: string }

export type CollectionNode =
  | { type: 'folder'; name: string; children: CollectionNode[] }
  | { type: 'item'; name: string }

export type CollectionVariable = { key: string; value: string; enabled: boolean }

export type CollectionDetail = {
  uid: string
  name: string
  items: CollectionNode[]
  variables: CollectionVariable[]
}

export type Environment = { id: string; name: string; uid: string }

export type EnvironmentValue = { key: string; value: string; enabled: boolean; type: string }

export type EnvironmentDetail = { id: string; uid: string; name: string; values: EnvironmentValue[] }

const BASE_URL = '/api'

export async function getWorkspaces(): Promise<Workspace[]> {
  const response = await fetch(`${BASE_URL}/workspaces`)
  if (!response.ok) throw new Error(`getWorkspaces failed: ${response.status}`)
  return response.json()
}

export async function getCollections(workspaceId: string): Promise<Collection[]> {
  const response = await fetch(`${BASE_URL}/workspaces/${workspaceId}`)
  if (!response.ok) throw new Error(`getCollections failed: ${response.status}`)
  return response.json()
}

export async function getCollectionDetail(uid: string): Promise<CollectionDetail> {
  const response = await fetch(`${BASE_URL}/collections/${uid}`)
  if (!response.ok) throw new Error(`getCollectionDetail failed: ${response.status}`)
  return response.json()
}

export async function getEnvironments(workspaceId: string): Promise<Environment[]> {
  const response = await fetch(`${BASE_URL}/environments?workspaceId=${workspaceId}`)
  if (!response.ok) throw new Error(`getEnvironments failed: ${response.status}`)
  return response.json()
}

export async function getEnvironmentDetail(uid: string): Promise<EnvironmentDetail> {
  const response = await fetch(`${BASE_URL}/environments/${uid}`)
  if (!response.ok) throw new Error(`getEnvironmentDetail failed: ${response.status}`)
  return response.json()
}

export async function updateEnvironment(uid: string, detail: EnvironmentDetail): Promise<void> {
  const response = await fetch(`${BASE_URL}/environments/${uid}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(detail),
  })
  if (!response.ok) throw new Error(`updateEnvironment failed: ${response.status}`)
}

export async function createEnvironment(workspaceId: string, name: string): Promise<Environment> {
  const response = await fetch(`${BASE_URL}/environments`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ workspaceId, name }),
  })
  if (!response.ok) throw new Error(`createEnvironment failed: ${response.status}`)
  return response.json()
}
