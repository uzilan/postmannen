import { MenuItem, Select } from '@mui/material'
import type { SelectChangeEvent } from '@mui/material'
import type { Workspace } from '../api'

export function WorkspaceSelector(props: {
  workspaces: Workspace[]
  selectedId: string | null
  onSelect: (id: string) => void
}) {
  const { workspaces, selectedId, onSelect } = props

  const handleChange = (event: SelectChangeEvent) => {
    onSelect(event.target.value)
  }

  return (
    <Select value={selectedId ?? ''} onChange={handleChange} displayEmpty size="small">
      {workspaces.map((workspace) => (
        <MenuItem key={workspace.id} value={workspace.id}>
          {workspace.name}
        </MenuItem>
      ))}
    </Select>
  )
}
