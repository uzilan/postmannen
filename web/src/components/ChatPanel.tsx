import { useState } from 'react'
import { Box, Button, TextField, Typography } from '@mui/material'
import type { ChatMessage } from '../api'

export function ChatPanel(props: {
  messages: ChatMessage[]
  sending: boolean
  onSend: (text: string) => void
}) {
  const { messages, sending, onSend } = props
  const [input, setInput] = useState('')

  const handleSend = () => {
    if (!input.trim() || sending) return
    onSend(input)
    setInput('')
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', flex: 1, overflow: 'hidden', p: 1 }}>
      <Box sx={{ flex: 1, overflow: 'auto', display: 'flex', flexDirection: 'column', gap: 1 }}>
        {messages.map((m, i) => (
          <Box key={i} sx={{ alignSelf: m.role === 'user' ? 'flex-end' : 'flex-start', maxWidth: '70%' }}>
            <Typography
              sx={{
                bgcolor: m.role === 'user' ? 'primary.main' : 'action.hover',
                color: m.role === 'user' ? 'primary.contrastText' : 'text.primary',
                borderRadius: 1,
                p: 1,
              }}
            >
              {m.text}
            </Typography>
            {m.role === 'assistant' && m.toolsUsed.length > 0 && (
              <Typography variant="caption" color="text.secondary">
                used: {m.toolsUsed.join(', ')}
              </Typography>
            )}
            {m.role === 'assistant' && m.errored && (
              <Typography variant="caption" color="error">
                error
              </Typography>
            )}
          </Box>
        ))}
      </Box>
      <Box sx={{ display: 'flex', gap: 1, pt: 1 }}>
        <TextField
          fullWidth
          size="small"
          value={input}
          disabled={sending}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') handleSend()
          }}
        />
        <Button onClick={handleSend} disabled={sending}>
          Send
        </Button>
      </Box>
    </Box>
  )
}
