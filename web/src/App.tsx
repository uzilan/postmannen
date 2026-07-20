import { useEffect, useState } from 'react'
import { Box, Button, CircularProgress, Typography } from '@mui/material'
import {
  createCollection,
  createEnvironment,
  deleteCollection,
  deleteEnvironment,
  getCollectionDetail,
  getCollections,
  getEnvironmentDetail,
  getEnvironments,
  getWorkspaces,
  isWriteTool,
  refreshAllWorkspaces,
  refreshWorkspace,
  renameCollection,
  renameEnvironment,
  sendChatMessage,
  updateEnvironment,
} from './api'
import type { ChatMessage, Collection, CollectionDetail, Environment, EnvironmentDetail, Workspace } from './api'
import { WorkspaceSelector } from './components/WorkspaceSelector'
import { TabBar } from './components/TabBar'
import type { AppTab } from './components/TabBar'
import { CollectionTree } from './components/CollectionTree'
import { EnvironmentList } from './components/EnvironmentList'
import { DetailPanel, detailContentLabel } from './components/DetailPanel'
import type { DetailContent } from './components/DetailPanel'
import { CreateEnvironmentDialog } from './components/CreateEnvironmentDialog'
import { CreateCollectionDialog } from './components/CreateCollectionDialog'
import { ConfirmDialog } from './components/ConfirmDialog'
import { ChatPanel } from './components/ChatPanel'
import { ResizableDivider } from './components/ResizableDivider'

const COLUMN_WIDTHS_KEY = 'postmannen.columnWidths'
const MIN_COLUMN_WIDTH = 150

function loadColumnWidths(): { leftWidth: number; rightWidth: number } {
  const saved = localStorage.getItem(COLUMN_WIDTHS_KEY)
  if (saved) {
    try {
      return JSON.parse(saved)
    } catch {
      // fall through to defaults
    }
  }
  return { leftWidth: window.innerWidth * 0.3, rightWidth: window.innerWidth * 0.3 }
}

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
  const [createCollectionDialogOpen, setCreateCollectionDialogOpen] = useState(false)
  const [newlyCreatedCollectionUid, setNewlyCreatedCollectionUid] = useState<string | null>(null)
  const [collectionPendingDelete, setCollectionPendingDelete] = useState<{ uid: string; name: string } | null>(null)
  const [environmentPendingDelete, setEnvironmentPendingDelete] = useState<{ uid: string; name: string } | null>(null)

  const [{ leftWidth, rightWidth }, setColumnWidths] = useState(loadColumnWidths)

  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([])
  const [chatSessionId, setChatSessionId] = useState<string | null>(null)
  const [chatSending, setChatSending] = useState(false)
  const [refreshingWorkspace, setRefreshingWorkspace] = useState(false)
  const [refreshingAllWorkspaces, setRefreshingAllWorkspaces] = useState(false)
  const [loadingWorkspaceData, setLoadingWorkspaceData] = useState(false)

  useEffect(() => {
    setDetailContent({ kind: 'none' })
    setHighlightedEnvironmentId(null)
    setMarkedEnvironmentIds(new Set())
  }, [activeTab])

  useEffect(() => {
    getWorkspaces()
      .then((ws) => {
        setWorkspaces(ws)
        if (ws.length > 0) setSelectedWorkspaceId(ws[0].id)
      })
      .catch((e) => setStatusMessage(`Error: ${e.message}`))
  }, [])

  const loadWorkspaceData = async (workspaceId: string) => {
    setLoadingWorkspaceData(true)
    try {
      try {
        const cols = await getCollections(workspaceId)
        setCollections([...cols].sort((a, b) => a.name.localeCompare(b.name)))
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
      } catch (e) {
        setStatusMessage(`Error: ${(e as Error).message}`)
      }

      try {
        setEnvironments((await getEnvironments(workspaceId)).sort((a, b) => a.name.localeCompare(b.name)))
      } catch (e) {
        setStatusMessage(`Error: ${(e as Error).message}`)
      }
    } finally {
      setLoadingWorkspaceData(false)
    }
  }

  useEffect(() => {
    if (!selectedWorkspaceId) return
    loadWorkspaceData(selectedWorkspaceId)
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
      setEnvironments((prev) => [...prev, env].sort((a, b) => a.name.localeCompare(b.name)))
      setCreateDialogOpen(false)
    } catch (e) {
      setStatusMessage(`Error: ${(e as Error).message}`)
    }
  }

  const handleCreateCollection = async (name: string) => {
    if (!selectedWorkspaceId) return
    try {
      const col = await createCollection(selectedWorkspaceId, name)
      setNewlyCreatedCollectionUid(col.uid)
      setCreateCollectionDialogOpen(false)
      await loadWorkspaceData(selectedWorkspaceId)
    } catch (e) {
      setStatusMessage(`Error: ${(e as Error).message}`)
    }
  }

  const handleDeleteCollection = async () => {
    if (!collectionPendingDelete) return
    const { uid } = collectionPendingDelete
    try {
      await deleteCollection(uid)
      setCollections((prev) => prev.filter((c) => c.uid !== uid))
      setCollectionDetails((prev) => {
        const next = new Map(prev)
        next.delete(uid)
        return next
      })
      setDetailContent({ kind: 'none' })
    } catch (e) {
      setStatusMessage(`Error: ${(e as Error).message}`)
    } finally {
      setCollectionPendingDelete(null)
    }
  }

  const handleDeleteEnvironment = async () => {
    if (!environmentPendingDelete) return
    const { uid } = environmentPendingDelete
    try {
      await deleteEnvironment(uid)
      const id = environments.find((e) => e.uid === uid)?.id
      setEnvironments((prev) => prev.filter((e) => e.uid !== uid))
      if (id) {
        setHighlightedEnvironmentId((prev) => (prev === id ? null : prev))
        setMarkedEnvironmentIds((prev) => {
          const next = new Set(prev)
          next.delete(id)
          return next
        })
      }
    } catch (e) {
      setStatusMessage(`Error: ${(e as Error).message}`)
    } finally {
      setEnvironmentPendingDelete(null)
    }
  }

  const handleRenameCollection = async (uid: string, name: string) => {
    const previousName = collections.find((c) => c.uid === uid)?.name
    setCollections((prev) => prev.map((c) => (c.uid === uid ? { ...c, name } : c)))
    setCollectionDetails((prev) => {
      const existing = prev.get(uid)
      if (!existing) return prev
      const next = new Map(prev)
      next.set(uid, { ...existing, name })
      return next
    })
    try {
      await renameCollection(uid, name)
    } catch (e) {
      setStatusMessage(`Error: ${(e as Error).message}`)
      if (previousName !== undefined) {
        setCollections((prev) => prev.map((c) => (c.uid === uid ? { ...c, name: previousName } : c)))
        setCollectionDetails((prev) => {
          const existing = prev.get(uid)
          if (!existing) return prev
          const next = new Map(prev)
          next.set(uid, { ...existing, name: previousName })
          return next
        })
      }
    }
  }

  const handleRenameEnvironment = async (uid: string, name: string) => {
    const previousName = environments.find((e) => e.uid === uid)?.name
    setEnvironments((prev) => prev.map((e) => (e.uid === uid ? { ...e, name } : e)))
    try {
      await renameEnvironment(uid, name)
    } catch (e) {
      setStatusMessage(`Error: ${(e as Error).message}`)
      if (previousName !== undefined) {
        setEnvironments((prev) => prev.map((e) => (e.uid === uid ? { ...e, name: previousName } : e)))
      }
    }
  }

  const handleSendChat = async (text: string) => {
    setChatMessages((prev) => [...prev, { role: 'user', text }])
    setChatSending(true)
    const workspaceName = workspaces.find((w) => w.id === selectedWorkspaceId)?.name
    try {
      const response = await sendChatMessage(text, chatSessionId, {
        workspaceName,
        workspaceId: selectedWorkspaceId ?? undefined,
        highlightedLabel: detailContentLabel(detailContent) ?? undefined,
      })
      setChatMessages((prev) => [
        ...prev,
        { role: 'assistant', text: response.reply, toolsUsed: response.toolsUsed, errored: response.errored },
      ])
      setChatSessionId(response.sessionId)
      if (!response.errored && response.toolsUsed.some(isWriteTool) && selectedWorkspaceId) {
        // The chat's MCP tools call Postman's API directly, bypassing our own
        // CachingPostmanApiService — so a plain refetch would just return our
        // stale cache. Invalidate it server-side first.
        setRefreshingWorkspace(true)
        try {
          await refreshWorkspace(selectedWorkspaceId)
          await loadWorkspaceData(selectedWorkspaceId)
        } finally {
          setRefreshingWorkspace(false)
        }
      }
    } catch (e) {
      setChatMessages((prev) => [
        ...prev,
        { role: 'assistant', text: `Error: ${(e as Error).message}`, toolsUsed: [], errored: true },
      ])
    } finally {
      setChatSending(false)
    }
  }

  const handleRefreshWorkspace = async () => {
    if (!selectedWorkspaceId) return
    setRefreshingWorkspace(true)
    try {
      await refreshWorkspace(selectedWorkspaceId)
      await loadWorkspaceData(selectedWorkspaceId)
    } catch (e) {
      setStatusMessage(`Error: ${(e as Error).message}`)
    } finally {
      setRefreshingWorkspace(false)
    }
  }

  const handleRefreshAllWorkspaces = async () => {
    setRefreshingAllWorkspaces(true)
    try {
      await refreshAllWorkspaces()
      const ws = await getWorkspaces()
      setWorkspaces(ws)
      if (selectedWorkspaceId) await loadWorkspaceData(selectedWorkspaceId)
    } catch (e) {
      setStatusMessage(`Error: ${(e as Error).message}`)
    } finally {
      setRefreshingAllWorkspaces(false)
    }
  }

  const handleLeftResize = (deltaX: number) => {
    setColumnWidths((prev) => {
      const maxLeftWidth = window.innerWidth - prev.rightWidth - MIN_COLUMN_WIDTH
      const leftWidth = Math.min(maxLeftWidth, Math.max(MIN_COLUMN_WIDTH, prev.leftWidth + deltaX))
      return { ...prev, leftWidth }
    })
  }

  const handleRightResize = (deltaX: number) => {
    setColumnWidths((prev) => {
      const maxRightWidth = window.innerWidth - prev.leftWidth - MIN_COLUMN_WIDTH
      const rightWidth = Math.min(maxRightWidth, Math.max(MIN_COLUMN_WIDTH, prev.rightWidth - deltaX))
      return { ...prev, rightWidth }
    })
  }

  const handleResizeEnd = () => {
    setColumnWidths((current) => {
      localStorage.setItem(COLUMN_WIDTHS_KEY, JSON.stringify(current))
      return current
    })
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100vh' }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, p: 1 }}>
        <WorkspaceSelector workspaces={workspaces} selectedId={selectedWorkspaceId} onSelect={setSelectedWorkspaceId} />
        <TabBar active={activeTab} onChange={setActiveTab} />
        <Button
          variant="outlined"
          size="small"
          disabled={!selectedWorkspaceId || refreshingWorkspace}
          onClick={handleRefreshWorkspace}
        >
          Refresh
        </Button>
        <Button variant="outlined" size="small" disabled={refreshingAllWorkspaces} onClick={handleRefreshAllWorkspaces}>
          Refresh All
        </Button>
      </Box>
      {statusMessage && <Typography color="error">{statusMessage}</Typography>}
      {refreshingWorkspace && <Typography color="text.secondary">Refreshing workspace…</Typography>}
      {refreshingAllWorkspaces && <Typography color="text.secondary">Refreshing all workspaces…</Typography>}
      <Box sx={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
        <Box sx={{ width: `${leftWidth}px`, flexShrink: 0, overflow: 'auto', display: 'flex', flexDirection: 'column' }}>
          <Box
            component="fieldset"
            sx={{ borderColor: 'divider', borderRadius: 1, m: 1, flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden', position: 'relative' }}
          >
            <Box component="legend" sx={{ px: 1 }}>
              {activeTab === 'collections' ? 'Collections' : 'Environments'}
            </Box>
            {loadingWorkspaceData && (
              <Box
                sx={{
                  position: 'absolute',
                  inset: 0,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  bgcolor: 'background.paper',
                  opacity: 0.8,
                }}
              >
                <CircularProgress />
              </Box>
            )}
            <Box sx={{ overflow: 'auto', flex: 1 }}>
              {activeTab === 'collections' && (
                <>
                  <Button variant="outlined" size="small" onClick={() => setCreateCollectionDialogOpen(true)}>
                    New Collection
                  </Button>
                  {collections.map((c) => {
                    const detail = collectionDetails.get(c.uid)
                    return detail ? (
                      <CollectionTree
                        key={c.uid}
                        detail={detail}
                        defaultExpanded={c.uid === newlyCreatedCollectionUid}
                        onSelectVariables={(variables) => setDetailContent({ kind: 'collectionVariables', variables })}
                        onSelectRequest={(item) => setDetailContent({ kind: 'request', item })}
                        onDeleteCollection={(uid, name) => setCollectionPendingDelete({ uid, name })}
                        onRenameCollection={handleRenameCollection}
                        selectedRequestItem={detailContent.kind === 'request' ? detailContent.item : null}
                        selectedVariables={detailContent.kind === 'collectionVariables' ? detailContent.variables : null}
                      />
                    ) : null
                  })}
                </>
              )}
              {activeTab === 'environments' && (
                <>
                  <Button variant="outlined" size="small" onClick={() => setCreateDialogOpen(true)}>
                    New Environment
                  </Button>
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
                    onDeleteEnvironment={(uid, name) => setEnvironmentPendingDelete({ uid, name })}
                    onRenameEnvironment={handleRenameEnvironment}
                  />
                </>
              )}
            </Box>
          </Box>
        </Box>
        <ResizableDivider onResize={handleLeftResize} onResizeEnd={handleResizeEnd} />
        <Box sx={{ flex: 1, minWidth: `${MIN_COLUMN_WIDTH}px`, overflow: 'auto', display: 'flex', flexDirection: 'column' }}>
          <Box
            component="fieldset"
            sx={{ borderColor: 'divider', borderRadius: 1, m: 1, flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}
          >
            <Box component="legend" sx={{ px: 1 }}>
              {detailContentLabel(detailContent) ?? 'Details'}
            </Box>
            <Box sx={{ overflow: 'auto', flex: 1 }}>
              <DetailPanel
                content={detailContent}
                onValueChange={handleValueChange}
                onEnabledToggle={handleEnabledToggle}
                onAddKey={handleAddKey}
                onDeleteKey={handleDeleteKey}
              />
            </Box>
          </Box>
        </Box>
        <ResizableDivider onResize={handleRightResize} onResizeEnd={handleResizeEnd} />
        <Box sx={{ width: `${rightWidth}px`, flexShrink: 0, overflow: 'auto', display: 'flex', flexDirection: 'column' }}>
          <Box
            component="fieldset"
            sx={{ borderColor: 'divider', borderRadius: 1, m: 1, flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}
          >
            <Box component="legend" sx={{ px: 1 }}>
              Chat
            </Box>
            <ChatPanel messages={chatMessages} sending={chatSending} onSend={handleSendChat} />
          </Box>
        </Box>
      </Box>
      <CreateEnvironmentDialog
        open={createDialogOpen}
        onCreate={handleCreateEnvironment}
        onClose={() => setCreateDialogOpen(false)}
      />
      <CreateCollectionDialog
        open={createCollectionDialogOpen}
        onCreate={handleCreateCollection}
        onClose={() => setCreateCollectionDialogOpen(false)}
      />
      <ConfirmDialog
        open={collectionPendingDelete !== null}
        title="Delete collection"
        message={`Delete collection "${collectionPendingDelete?.name}"? This cannot be undone.`}
        onConfirm={handleDeleteCollection}
        onCancel={() => setCollectionPendingDelete(null)}
      />
      <ConfirmDialog
        open={environmentPendingDelete !== null}
        title="Delete environment"
        message={`Delete environment "${environmentPendingDelete?.name}"? This cannot be undone.`}
        onConfirm={handleDeleteEnvironment}
        onCancel={() => setEnvironmentPendingDelete(null)}
      />
    </Box>
  )
}
