import { Button, Dialog, DialogActions, DialogContent, DialogTitle, TextField } from '@mui/material'
import { useState } from 'react'

export function CreateEnvironmentDialog(props: {
  open: boolean
  onCreate: (name: string) => void
  onClose: () => void
}) {
  const { open, onCreate, onClose } = props
  const [name, setName] = useState('')

  return (
    <Dialog open={open} onClose={onClose}>
      <DialogTitle>New Environment</DialogTitle>
      <DialogContent>
        <TextField
          label="Environment name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          autoFocus
          fullWidth
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          onClick={() => {
            if (!name.trim()) return
            onCreate(name)
            setName('')
          }}
        >
          Create
        </Button>
      </DialogActions>
    </Dialog>
  )
}
