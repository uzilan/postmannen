import { Button, Dialog, DialogActions, DialogContent, DialogTitle, TextField } from '@mui/material'
import { useState } from 'react'

export function AddKeyDialog(props: {
  open: boolean
  onCreate: (key: string) => void
  onClose: () => void
}) {
  const { open, onCreate, onClose } = props
  const [key, setKey] = useState('')

  return (
    <Dialog open={open} onClose={onClose}>
      <DialogTitle>Add Key</DialogTitle>
      <DialogContent>
        <TextField
          label="Key name"
          value={key}
          onChange={(e) => setKey(e.target.value)}
          autoFocus
          fullWidth
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          onClick={() => {
            if (!key.trim()) return
            onCreate(key)
            setKey('')
          }}
        >
          Add
        </Button>
      </DialogActions>
    </Dialog>
  )
}
