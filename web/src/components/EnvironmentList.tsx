import { useState } from 'react'
import { Checkbox, IconButton, List, ListItemButton, ListItemText, TextField } from '@mui/material'
import DeleteIcon from '@mui/icons-material/Delete'
import EditIcon from '@mui/icons-material/Edit'
import type { Environment } from '../api'

export function EnvironmentList(props: {
  environments: Environment[]
  highlightedId: string | null
  markedIds: Set<string>
  onHighlight: (id: string) => void
  onToggleMark: (id: string) => void
  onDeleteEnvironment: (uid: string, name: string) => void
  onRenameEnvironment: (uid: string, name: string) => void
}) {
  const { environments, highlightedId, markedIds, onHighlight, onToggleMark, onDeleteEnvironment, onRenameEnvironment } = props
  const [editingUid, setEditingUid] = useState<string | null>(null)
  const [editValue, setEditValue] = useState('')

  const startEditing = (env: Environment) => {
    setEditValue(env.name)
    setEditingUid(env.uid)
  }

  const commitEdit = (env: Environment) => {
    const trimmed = editValue.trim()
    setEditingUid(null)
    if (trimmed && trimmed !== env.name) onRenameEnvironment(env.uid, trimmed)
  }

  const cancelEdit = () => {
    setEditingUid(null)
  }

  return (
    <List dense>
      {environments.map((env) => (
        <ListItemButton
          key={env.id}
          selected={env.id === highlightedId}
          onClick={() => {
            if (editingUid === env.uid) return
            onHighlight(env.id)
          }}
          sx={{ '&:hover .delete-environment-button, &:hover .rename-environment-button': { opacity: 1 } }}
        >
          <Checkbox
            checked={markedIds.has(env.id)}
            onClick={(e) => {
              e.stopPropagation()
              onToggleMark(env.id)
            }}
          />
          {editingUid === env.uid ? (
            <TextField
              size="small"
              autoFocus
              value={editValue}
              onClick={(e) => e.stopPropagation()}
              onChange={(e) => setEditValue(e.target.value)}
              onBlur={() => commitEdit(env)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') commitEdit(env)
                else if (e.key === 'Escape') cancelEdit()
              }}
            />
          ) : (
            <ListItemText>{env.name}</ListItemText>
          )}
          <IconButton
            className="rename-environment-button"
            aria-label="Rename environment"
            size="small"
            sx={{ opacity: 0 }}
            onClick={(e) => {
              e.stopPropagation()
              startEditing(env)
            }}
          >
            <EditIcon fontSize="small" />
          </IconButton>
          <IconButton
            className="delete-environment-button"
            aria-label="Delete environment"
            size="small"
            sx={{ opacity: 0 }}
            onClick={(e) => {
              e.stopPropagation()
              onDeleteEnvironment(env.uid, env.name)
            }}
          >
            <DeleteIcon fontSize="small" />
          </IconButton>
        </ListItemButton>
      ))}
    </List>
  )
}
