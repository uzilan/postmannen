import { Checkbox, IconButton, List, ListItemButton, ListItemText } from '@mui/material'
import DeleteIcon from '@mui/icons-material/Delete'
import type { Environment } from '../api'

export function EnvironmentList(props: {
  environments: Environment[]
  highlightedId: string | null
  markedIds: Set<string>
  onHighlight: (id: string) => void
  onToggleMark: (id: string) => void
  onDeleteEnvironment: (uid: string, name: string) => void
}) {
  const { environments, highlightedId, markedIds, onHighlight, onToggleMark, onDeleteEnvironment } = props

  return (
    <List>
      {environments.map((env) => (
        <ListItemButton
          key={env.id}
          selected={env.id === highlightedId}
          onClick={() => onHighlight(env.id)}
          sx={{ '&:hover .delete-environment-button': { opacity: 1 } }}
        >
          <Checkbox
            checked={markedIds.has(env.id)}
            onClick={(e) => {
              e.stopPropagation()
              onToggleMark(env.id)
            }}
          />
          <ListItemText>{env.name}</ListItemText>
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
