import { useState } from 'react'
import { Box, IconButton, List, ListItemButton, ListItemIcon, ListItemText } from '@mui/material'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import DeleteIcon from '@mui/icons-material/Delete'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import type { CollectionDetail, CollectionNode, CollectionVariable } from '../api'

type RequestItemNode = Extract<CollectionNode, { type: 'item' }>

const METHOD_COLORS: Record<string, string> = {
  GET: '#4caf50',
  POST: '#ff9800',
  PUT: '#2196f3',
  PATCH: '#9c27b0',
  DELETE: '#f44336',
  HEAD: '#795548',
  OPTIONS: '#607d8b',
}

function MethodLabel({ method }: { method: string }) {
  const color = METHOD_COLORS[method.toUpperCase()] ?? '#9e9e9e'
  return (
    <Box
      component="span"
      sx={{ color, fontWeight: 'bold', fontSize: '0.75rem', minWidth: 48, display: 'inline-block', mr: 1 }}
    >
      {method.toUpperCase()}
    </Box>
  )
}

export function collectNodeIds(parentId: string, nodes: CollectionNode[]): string[] {
  return nodes.flatMap((node, i) => {
    if (node.type !== 'folder') return []
    const id = `${parentId}/${i}`
    return [id, ...collectNodeIds(id, node.children)]
  })
}

function TreeNode(props: {
  node: CollectionNode
  id: string
  depth: number
  collapsedIds: Set<string>
  onToggle: (id: string) => void
  onSelectRequest: (item: RequestItemNode) => void
}) {
  const { node, id, depth, collapsedIds, onToggle, onSelectRequest } = props

  if (node.type === 'item') {
    return (
      <ListItemButton sx={{ pl: (depth + 1) * 2 + 4 }} disableRipple onClick={() => onSelectRequest(node)}>
        <ListItemIcon sx={{ minWidth: 32 }}>
          <MethodLabel method={node.method} />
        </ListItemIcon>
        <ListItemText>{node.name}</ListItemText>
      </ListItemButton>
    )
  }

  const isCollapsed = collapsedIds.has(id)
  return (
    <>
      <ListItemButton onClick={() => onToggle(id)} sx={{ pl: (depth + 1) * 2 }}>
        <ListItemIcon sx={{ minWidth: 32 }}>
          {isCollapsed ? <ChevronRightIcon fontSize="small" /> : <ExpandMoreIcon fontSize="small" />}
        </ListItemIcon>
        <ListItemText>{node.name}</ListItemText>
      </ListItemButton>
      {!isCollapsed &&
        node.children.map((child, i) => (
          <TreeNode
            key={`${id}/${i}`}
            node={child}
            id={`${id}/${i}`}
            depth={depth + 1}
            collapsedIds={collapsedIds}
            onToggle={onToggle}
            onSelectRequest={onSelectRequest}
          />
        ))}
    </>
  )
}

export function CollectionTree(props: {
  detail: CollectionDetail
  defaultExpanded?: boolean
  onSelectVariables: (variables: CollectionVariable[]) => void
  onSelectRequest: (item: RequestItemNode) => void
  onDeleteCollection: (uid: string, name: string) => void
}) {
  const { detail, defaultExpanded, onSelectVariables, onSelectRequest, onDeleteCollection } = props
  const [collapsedIds, setCollapsedIds] = useState<Set<string>>(
    () => (defaultExpanded ? new Set<string>() : new Set([detail.uid, ...collectNodeIds(detail.uid, detail.items)]))
  )

  const onToggle = (id: string) => {
    setCollapsedIds((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const isCollapsed = collapsedIds.has(detail.uid)

  return (
    <List>
      <ListItemButton
        onClick={() => {
          onToggle(detail.uid)
          onSelectVariables(detail.variables)
        }}
        sx={{ '&:hover .delete-collection-button': { opacity: 1 } }}
      >
        <ListItemIcon sx={{ minWidth: 32 }}>
          {isCollapsed ? <ChevronRightIcon fontSize="small" /> : <ExpandMoreIcon fontSize="small" />}
        </ListItemIcon>
        <ListItemText>{detail.name}</ListItemText>
        <IconButton
          className="delete-collection-button"
          aria-label="Delete collection"
          size="small"
          sx={{ opacity: 0 }}
          onClick={(e) => {
            e.stopPropagation()
            onDeleteCollection(detail.uid, detail.name)
          }}
        >
          <DeleteIcon fontSize="small" />
        </IconButton>
      </ListItemButton>
      {!isCollapsed &&
        detail.items.map((node, i) => (
          <TreeNode
            key={`${detail.uid}/${i}`}
            node={node}
            id={`${detail.uid}/${i}`}
            depth={1}
            collapsedIds={collapsedIds}
            onToggle={onToggle}
            onSelectRequest={onSelectRequest}
          />
        ))}
    </List>
  )
}
