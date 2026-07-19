import { useState } from 'react'
import { List, ListItemButton, ListItemText } from '@mui/material'
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
    return <ListItemText inset sx={{ pl: depth * 2 }}>{node.name}</ListItemText>
  }

  const isCollapsed = collapsedIds.has(id)
  return (
    <>
      <ListItemButton onClick={() => onToggle(id)} sx={{ pl: depth * 2 }}>
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
    () => new Set(collectNodeIds(detail.uid, detail.items))
  )

  const onToggle = (id: string) => {
    setCollapsedIds((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  return (
    <List onClick={() => onSelectVariables(detail.variables)}>
      {detail.items.map((node, i) => (
        <TreeNode
          key={`${detail.uid}/${i}`}
          node={node}
          id={`${detail.uid}/${i}`}
          depth={0}
          collapsedIds={collapsedIds}
          onToggle={onToggle}
        />
      ))}
    </List>
  )
}
