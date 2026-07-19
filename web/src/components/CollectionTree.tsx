import { useState } from 'react'
import { List, ListItemButton, ListItemIcon, ListItemText } from '@mui/material'
import ChevronRightIcon from '@mui/icons-material/ChevronRight'
import DescriptionOutlinedIcon from '@mui/icons-material/DescriptionOutlined'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import type { CollectionDetail, CollectionNode, CollectionVariable } from '../api'

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
}) {
  const { node, id, depth, collapsedIds, onToggle } = props

  if (node.type === 'item') {
    return (
      <ListItemButton sx={{ pl: (depth + 1) * 2 + 4 }} disableRipple>
        <ListItemIcon sx={{ minWidth: 32 }}>
          <DescriptionOutlinedIcon fontSize="small" />
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
          />
        ))}
    </>
  )
}

export function CollectionTree(props: {
  detail: CollectionDetail
  onSelectVariables: (variables: CollectionVariable[]) => void
}) {
  const { detail, onSelectVariables } = props
  const [collapsedIds, setCollapsedIds] = useState<Set<string>>(
    () => new Set([detail.uid, ...collectNodeIds(detail.uid, detail.items)])
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
      >
        <ListItemIcon sx={{ minWidth: 32 }}>
          {isCollapsed ? <ChevronRightIcon fontSize="small" /> : <ExpandMoreIcon fontSize="small" />}
        </ListItemIcon>
        <ListItemText>{detail.name}</ListItemText>
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
          />
        ))}
    </List>
  )
}
