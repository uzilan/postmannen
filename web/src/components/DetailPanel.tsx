import { Box, Button, IconButton, Table, TableBody, TableCell, TableHead, TableRow, Checkbox, TextField, Typography } from '@mui/material'
import DeleteIcon from '@mui/icons-material/Delete'
import { useState } from 'react'
import type { CollectionVariable, EnvironmentDetail } from '../api'

export type DetailContent =
  | { kind: 'none' }
  | { kind: 'loading' }
  | { kind: 'collectionVariables'; variables: CollectionVariable[] }
  | { kind: 'environments'; details: EnvironmentDetail[] }

export function DetailPanel(props: {
  content: DetailContent
  onValueChange: (environmentUid: string, key: string, newValue: string) => void
  onEnabledToggle: (environmentUid: string, key: string) => void
  onAddKey: (key: string) => void
  onDeleteKey: (key: string) => void
}) {
  const { content, onValueChange, onEnabledToggle, onAddKey, onDeleteKey } = props
  const [newKey, setNewKey] = useState('')

  if (content.kind === 'none') return null
  if (content.kind === 'loading') return <Typography>Loading...</Typography>

  if (content.kind === 'collectionVariables') {
    return (
      <Box component="fieldset" sx={{ borderColor: 'divider', borderRadius: 1, m: 1 }}>
        <Box component="legend" sx={{ px: 1 }}>
          Variables
        </Box>
        <Table>
          <TableBody>
            {content.variables.map((v) => (
              <TableRow key={v.key}>
                <TableCell>{v.key}</TableCell>
                <TableCell>{v.value}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Box>
    )
  }

  const keys = Array.from(new Set(content.details.flatMap((d) => d.values.map((v) => v.key))))

  return (
    <>
      <Table>
        <TableHead>
          <TableRow>
            <TableCell>Key</TableCell>
            {content.details.map((d) => (
              <TableCell key={d.uid}>{d.name}</TableCell>
            ))}
            <TableCell />
          </TableRow>
        </TableHead>
        <TableBody>
          {keys.map((key) => (
            <TableRow key={key}>
              <TableCell>{key}</TableCell>
              {content.details.map((d) => {
                const cell = d.values.find((v) => v.key === key)
                return (
                  <TableCell key={d.uid}>
                    <Checkbox
                      checked={cell?.enabled ?? false}
                      onChange={() => onEnabledToggle(d.uid, key)}
                      size="small"
                    />
                    <TextField
                      variant="standard"
                      defaultValue={cell?.value ?? ''}
                      onBlur={(e) => onValueChange(d.uid, key, e.target.value)}
                    />
                  </TableCell>
                )
              })}
              <TableCell>
                <IconButton aria-label={`delete row ${key}`} onClick={() => onDeleteKey(key)} size="small">
                  <DeleteIcon fontSize="small" />
                </IconButton>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      <TextField
        size="small"
        placeholder="new key"
        value={newKey}
        onChange={(e) => setNewKey(e.target.value)}
      />
      <Button
        onClick={() => {
          if (!newKey.trim()) return
          onAddKey(newKey)
          setNewKey('')
        }}
      >
        Add key
      </Button>
    </>
  )
}
