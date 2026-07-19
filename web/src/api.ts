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
