import { Button, Dialog, DialogActions, DialogContent, DialogTitle, TextField } from '@mui/material'
import { useState } from 'react'

export function CreateCollectionDialog(props: {
  open: boolean
  onCreate: (name: string) => void
  onClose: () => void
}) {
  const { open, onCreate, onClose } = props
  const [name, setName] = useState('')

  return (
    <Dialog open={open} onClose={onClose}>
      <DialogTitle>New Collection</DialogTitle>
      <DialogContent>
        <TextField
          label="Collection name"
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
