import { useEffect, useState } from 'react'
import { Box, Button, Typography } from '@mui/material'
import {
  createEnvironment,
  getCollectionDetail,
  getCollections,
  getEnvironmentDetail,
  getEnvironments,
  getWorkspaces,
  updateEnvironment,
} from './api'
import type { Collection, CollectionDetail, Environment, EnvironmentDetail, Workspace } from './api'
import { WorkspaceSelector } from './components/WorkspaceSelector'
import { TabBar } from './components/TabBar'
import type { AppTab } from './components/TabBar'
import { CollectionTree } from './components/CollectionTree'
import { EnvironmentList } from './components/EnvironmentList'
import { DetailPanel } from './components/DetailPanel'
import type { DetailContent } from './components/DetailPanel'
import { CreateEnvironmentDialog } from './components/CreateEnvironmentDialog'

export default function App() {
  const [workspaces, setWorkspaces] = useState<Workspace[]>([])
  const [selectedWorkspaceId, setSelectedWorkspaceId] = useState<string | null>(null)
  const [activeTab, setActiveTab] = useState<AppTab>('collections')

  const [collections, setCollections] = useState<Collection[]>([])
  const [collectionDetails, setCollectionDetails] = useState<Map<string, CollectionDetail>>(new Map())

  const [environments, setEnvironments] = useState<Environment[]>([])
  const [highlightedEnvironmentId, setHighlightedEnvironmentId] = useState<string | null>(null)
  const [markedEnvironmentIds, setMarkedEnvironmentIds] = useState<Set<string>>(new Set())

  const [detailContent, setDetailContent] = useState<DetailContent>({ kind: 'none' })
  const [statusMessage, setStatusMessage] = useState<string | null>(null)
  const [createDialogOpen, setCreateDialogOpen] = useState(false)

  useEffect(() => {
    getWorkspaces()
      .then((ws) => {
        setWorkspaces(ws)
        if (ws.length > 0) setSelectedWorkspaceId(ws[0].id)
      })
      .catch((e) => setStatusMessage(`Error: ${e.message}`))
  }, [])

  useEffect(() => {
    if (!selectedWorkspaceId) return
    getCollections(selectedWorkspaceId)
      .then(async (cols) => {
        setCollections(cols)
        const results = await Promise.all(
          cols.map(async (c) => {
            try {
              return [c.uid, await getCollectionDetail(c.uid)] as const
            } catch {
              return null
            }
          })
        )
        setCollectionDetails(new Map(results.filter((r): r is readonly [string, CollectionDetail] => r !== null)))
      })
      .catch((e) => setStatusMessage(`Error: ${e.message}`))

    getEnvironments(selectedWorkspaceId)
      .then(setEnvironments)
      .catch((e) => setStatusMessage(`Error: ${e.message}`))
  }, [selectedWorkspaceId])

  useEffect(() => {
    const targetIds = markedEnvironmentIds.size >= 2
      ? markedEnvironmentIds
      : highlightedEnvironmentId
        ? new Set([highlightedEnvironmentId])
        : new Set<string>()

    if (targetIds.size === 0) {
      setDetailContent({ kind: 'none' })
      return
    }

    const targets = environments.filter((e) => targetIds.has(e.id))
    setDetailContent({ kind: 'loading' })
    Promise.all(
      targets.map(async (env) => {
        try {
          return await getEnvironmentDetail(env.uid)
        } catch {
          return null
        }
      })
    ).then((results) => {
      const details = results.filter((d): d is EnvironmentDetail => d !== null)
      setDetailContent({ kind: 'environments', details })
    })
  }, [highlightedEnvironmentId, markedEnvironmentIds, environments])

  const handleValueChange = async (environmentUid: string, key: string, newValue: string) => {
    if (detailContent.kind !== 'environments') return
    const detail = detailContent.details.find((d) => d.uid === environmentUid)
    if (!detail) return
    const idx = detail.values.findIndex((v) => v.key === key)
    const newValues = idx >= 0
      ? detail.values.map((v, i) => (i === idx ? { ...v, value: newValue } : v))
      : [...detail.values, { key, value: newValue, enabled: true, type: 'default' }]
    const updated = { ...detail, values: newValues }
    try {
      await updateEnvironment(environmentUid, updated)
      setDetailContent({
        kind: 'environments',
        details: detailContent.details.map((d) => (d.uid === environmentUid ? updated : d)),
      })
    } catch (e) {
      setStatusMessage(`Error: ${(e as Error).message}`)
    }
  }

  const handleEnabledToggle = async (environmentUid: string, key: string) => {
    if (detailContent.kind !== 'environments') return
    const detail = detailContent.details.find((d) => d.uid === environmentUid)
    if (!detail) return
    const idx = detail.values.findIndex((v) => v.key === key)
    if (idx < 0) return
    const newValues = detail.values.map((v, i) => (i === idx ? { ...v, enabled: !v.enabled } : v))
    const updated = { ...detail, values: newValues }
    try {
      await updateEnvironment(environmentUid, updated)
      setDetailContent({
        kind: 'environments',
        details: detailContent.details.map((d) => (d.uid === environmentUid ? updated : d)),
      })
    } catch (e) {
      setStatusMessage(`Error: ${(e as Error).message}`)
    }
  }

  const handleAddKey = async (key: string) => {
    if (detailContent.kind !== 'environments') return
    const updates = detailContent.details.map((d) => ({
      ...d,
      values: [...d.values, { key, value: '', enabled: true, type: 'default' }],
    }))
    try {
      await Promise.all(updates.map((u) => updateEnvironment(u.uid, u)))
      setDetailContent({ kind: 'environments', details: updates })
    } catch (e) {
      setStatusMessage(`Error: ${(e as Error).message}`)
    }
  }

  const handleDeleteKey = async (key: string) => {
    if (detailContent.kind !== 'environments') return
    const updates = detailContent.details.map((d) => ({
      ...d,
      values: d.values.filter((v) => v.key !== key),
    }))
    try {
      await Promise.all(updates.map((u) => updateEnvironment(u.uid, u)))
      setDetailContent({ kind: 'environments', details: updates })
    } catch (e) {
      setStatusMessage(`Error: ${(e as Error).message}`)
    }
  }

  const handleCreateEnvironment = async (name: string) => {
    if (!selectedWorkspaceId) return
    try {
      const env = await createEnvironment(selectedWorkspaceId, name)
      setEnvironments((prev) => [...prev, env])
      setCreateDialogOpen(false)
    } catch (e) {
      setStatusMessage(`Error: ${(e as Error).message}`)
    }
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100vh' }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, p: 1 }}>
        <WorkspaceSelector workspaces={workspaces} selectedId={selectedWorkspaceId} onSelect={setSelectedWorkspaceId} />
        <TabBar active={activeTab} onChange={setActiveTab} />
      </Box>
      {statusMessage && <Typography color="error">{statusMessage}</Typography>}
      <Box sx={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
        <Box sx={{ width: '30%', overflow: 'auto', display: 'flex', flexDirection: 'column' }}>
          {activeTab === 'collections' && (
            <Box
              component="fieldset"
              sx={{ borderColor: 'divider', borderRadius: 1, m: 1, flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}
            >
              <Box component="legend" sx={{ px: 1 }}>
                Collections
              </Box>
              <Box sx={{ overflow: 'auto', flex: 1 }}>
                {collections.map((c) => {
                  const detail = collectionDetails.get(c.uid)
                  return detail ? (
                    <CollectionTree
                      key={c.uid}
                      detail={detail}
                      onSelectVariables={(variables) => setDetailContent({ kind: 'collectionVariables', variables })}
                    />
                  ) : null
                })}
              </Box>
            </Box>
          )}
          {activeTab === 'environments' && (
            <>
              <Button onClick={() => setCreateDialogOpen(true)}>New Environment</Button>
              <EnvironmentList
                environments={environments}
                highlightedId={highlightedEnvironmentId}
                markedIds={markedEnvironmentIds}
                onHighlight={setHighlightedEnvironmentId}
                onToggleMark={(id) =>
                  setMarkedEnvironmentIds((prev) => {
                    const next = new Set(prev)
                    if (next.has(id)) next.delete(id)
                    else next.add(id)
                    return next
                  })
                }
              />
            </>
          )}
        </Box>
        <Box sx={{ flex: 1, overflow: 'auto', display: 'flex', flexDirection: 'column' }}>
          <DetailPanel
            content={detailContent}
            onValueChange={handleValueChange}
            onEnabledToggle={handleEnabledToggle}
            onAddKey={handleAddKey}
            onDeleteKey={handleDeleteKey}
          />
        </Box>
      </Box>
      <CreateEnvironmentDialog
        open={createDialogOpen}
        onCreate={handleCreateEnvironment}
        onClose={() => setCreateDialogOpen(false)}
      />
    </Box>
  )
}
