import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ChatPanel } from './ChatPanel'
import type { ChatMessage, McpTool } from '../api'

describe('ChatPanel', () => {
  it('renders user and assistant messages', () => {
    const messages: ChatMessage[] = [
      { role: 'user', text: 'hello' },
      { role: 'assistant', text: 'hi there', toolsUsed: [], errored: false },
    ]
    render(<ChatPanel messages={messages} sending={false} onSend={vi.fn()} tools={[]} />)
    expect(screen.getByText('hello')).toBeInTheDocument()
    expect(screen.getByText('hi there')).toBeInTheDocument()
  })

  it('marks errored assistant messages distinctly', () => {
    const messages: ChatMessage[] = [
      { role: 'assistant', text: 'Error: boom', toolsUsed: [], errored: true },
    ]
    render(<ChatPanel messages={messages} sending={false} onSend={vi.fn()} tools={[]} />)
    expect(screen.getByText('error')).toBeInTheDocument()
  })

  it('calls onSend with the typed text and clears the input', () => {
    const onSend = vi.fn()
    render(<ChatPanel messages={[]} sending={false} onSend={onSend} tools={[]} />)

    const input = screen.getByRole('textbox')
    fireEvent.change(input, { target: { value: 'what is in staging' } })
    fireEvent.click(screen.getByText('Send'))

    expect(onSend).toHaveBeenCalledWith('what is in staging')
    expect(input).toHaveValue('')
  })

  it('does not call onSend while a turn is already sending', () => {
    const onSend = vi.fn()
    render(<ChatPanel messages={[]} sending={true} onSend={onSend} tools={[]} />)

    fireEvent.click(screen.getByText('Send'))

    expect(onSend).not.toHaveBeenCalled()
  })

  it('does not render a tools toggle when there are no tools', () => {
    render(<ChatPanel messages={[]} sending={false} onSend={vi.fn()} tools={[]} />)
    expect(screen.queryByText(/available tools/)).not.toBeInTheDocument()
  })

  it('shows the tool count and reveals tool names on click', () => {
    const tools: McpTool[] = [
      { name: 'getCollections', description: 'List collections' },
      { name: 'createEnvironment', description: 'Create an environment' },
    ]
    render(<ChatPanel messages={[]} sending={false} onSend={vi.fn()} tools={tools} />)

    expect(screen.getByText('Show available tools (2)')).toBeInTheDocument()
    expect(screen.queryByText('getCollections')).not.toBeInTheDocument()

    fireEvent.click(screen.getByText('Show available tools (2)'))

    expect(screen.getByText('getCollections')).toBeInTheDocument()
    expect(screen.getByText('createEnvironment')).toBeInTheDocument()
  })
})
