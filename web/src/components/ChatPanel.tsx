import { useState } from 'react'
import { Box, Button, Chip, CircularProgress, Collapse, TextField, Tooltip, Typography } from '@mui/material'
import type { ChatMessage, McpTool } from '../api'

export function ChatPanel(props: {
  messages: ChatMessage[]
  sending: boolean
  onSend: (text: string) => void
  tools: McpTool[]
}) {
  const { messages, sending, onSend, tools } = props
  const [input, setInput] = useState('')
  const [toolsExpanded, setToolsExpanded] = useState(false)

  const handleSend = () => {
    if (!input.trim() || sending) return
    onSend(input)
    setInput('')
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', flex: 1, overflow: 'hidden', p: 1 }}>
      {tools.length > 0 && (
        <Box sx={{ pb: 1 }}>
          <Button size="small" onClick={() => setToolsExpanded((prev) => !prev)}>
            {toolsExpanded ? 'Hide' : 'Show'} available tools ({tools.length})
          </Button>
          <Collapse in={toolsExpanded} unmountOnExit>
            <Box
              sx={{
                maxHeight: 200,
                overflow: 'auto',
                display: 'flex',
                flexWrap: 'wrap',
                gap: 0.5,
                p: 1,
                border: 1,
                borderColor: 'divider',
                borderRadius: 1,
              }}
            >
              {tools.map((tool) => (
                <Tooltip key={tool.name} title={tool.description}>
                  <Chip label={tool.name} size="small" />
                </Tooltip>
              ))}
            </Box>
          </Collapse>
        </Box>
      )}
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
            {m.role === 'assistant' && m.errored && (
              <Typography variant="caption" color="error">
                error
              </Typography>
            )}
          </Box>
        ))}
        {sending && (
          <Box sx={{ alignSelf: 'flex-start', p: 1 }}>
            <CircularProgress size={20} />
          </Box>
        )}
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
