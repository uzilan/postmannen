import { Checkbox, List, ListItemButton, ListItemText } from '@mui/material'
import type { Environment } from '../api'

export function EnvironmentList(props: {
  environments: Environment[]
  highlightedId: string | null
  markedIds: Set<string>
  onHighlight: (id: string) => void
  onToggleMark: (id: string) => void
}) {
  const { environments, highlightedId, markedIds, onHighlight, onToggleMark } = props

  return (
    <List>
      {environments.map((env) => (
        <ListItemButton
          key={env.id}
          selected={env.id === highlightedId}
          onClick={() => onHighlight(env.id)}
        >
          <Checkbox
            checked={markedIds.has(env.id)}
            onClick={(e) => {
              e.stopPropagation()
              onToggleMark(env.id)
            }}
          />
          <ListItemText>{env.name}</ListItemText>
        </ListItemButton>
      ))}
    </List>
  )
}
