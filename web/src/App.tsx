import { useEffect, useState } from 'react'
import { Box, Button, Typography } from '@mui/material'
import {
  createEnvironment,
  getCollectionDetail,
  getCollections,
  getEnvironmentDetail,
  getEnvironments,
  getWorkspaces,
  isWriteTool,
  refreshWorkspace,
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
import { ChatPanel } from './components/ChatPanel'

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

  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([])
  const [chatSessionId, setChatSessionId] = useState<string | null>(null)
  const [chatSending, setChatSending] = useState(false)
  const [refreshingWorkspace, setRefreshingWorkspace] = useState(false)

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
    try {
      const cols = await getCollections(workspaceId)
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
    } catch (e) {
      setStatusMessage(`Error: ${(e as Error).message}`)
    }

    try {
      setEnvironments(await getEnvironments(workspaceId))
    } catch (e) {
      setStatusMessage(`Error: ${(e as Error).message}`)
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
      setEnvironments((prev) => [...prev, env])
      setCreateDialogOpen(false)
    } catch (e) {
      setStatusMessage(`Error: ${(e as Error).message}`)
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

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100vh' }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, p: 1 }}>
        <WorkspaceSelector workspaces={workspaces} selectedId={selectedWorkspaceId} onSelect={setSelectedWorkspaceId} />
        <TabBar active={activeTab} onChange={setActiveTab} />
      </Box>
      {statusMessage && <Typography color="error">{statusMessage}</Typography>}
      {refreshingWorkspace && <Typography color="text.secondary">Refreshing workspace…</Typography>}
      <Box sx={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
        <Box sx={{ width: '30%', overflow: 'auto', display: 'flex', flexDirection: 'column' }}>
          <Box
            component="fieldset"
            sx={{ borderColor: 'divider', borderRadius: 1, m: 1, flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}
          >
            <Box component="legend" sx={{ px: 1 }}>
              {activeTab === 'collections' ? 'Collections' : 'Environments'}
            </Box>
            <Box sx={{ overflow: 'auto', flex: 1 }}>
              {activeTab === 'collections' &&
                collections.map((c) => {
                  const detail = collectionDetails.get(c.uid)
                  return detail ? (
                    <CollectionTree
                      key={c.uid}
                      detail={detail}
                      onSelectVariables={(variables) => setDetailContent({ kind: 'collectionVariables', variables })}
                      onSelectRequest={(item) => setDetailContent({ kind: 'request', item })}
                    />
                  ) : null
                })}
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
          </Box>
        </Box>
        <Box sx={{ flex: 1, overflow: 'auto', display: 'flex', flexDirection: 'column' }}>
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
        <Box sx={{ width: '30%', overflow: 'auto', display: 'flex', flexDirection: 'column' }}>
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
    </Box>
  )
}
