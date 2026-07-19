import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ChatPanel } from './ChatPanel'
import type { ChatMessage } from '../api'

describe('ChatPanel', () => {
  it('renders user and assistant messages', () => {
    const messages: ChatMessage[] = [
      { role: 'user', text: 'hello' },
      { role: 'assistant', text: 'hi there', toolsUsed: [], errored: false },
    ]
    render(<ChatPanel messages={messages} sending={false} onSend={vi.fn()} />)
    expect(screen.getByText('hello')).toBeInTheDocument()
    expect(screen.getByText('hi there')).toBeInTheDocument()
  })

  it('shows a tools-used line for assistant messages that used tools', () => {
    const messages: ChatMessage[] = [
      { role: 'assistant', text: 'done', toolsUsed: ['update_environment', 'get_environment'], errored: false },
    ]
    render(<ChatPanel messages={messages} sending={false} onSend={vi.fn()} />)
    expect(screen.getByText(/update_environment, get_environment/)).toBeInTheDocument()
  })

  it('marks errored assistant messages distinctly', () => {
    const messages: ChatMessage[] = [
      { role: 'assistant', text: 'Error: boom', toolsUsed: [], errored: true },
    ]
    render(<ChatPanel messages={messages} sending={false} onSend={vi.fn()} />)
    expect(screen.getByText('error')).toBeInTheDocument()
  })

  it('calls onSend with the typed text and clears the input', () => {
    const onSend = vi.fn()
    render(<ChatPanel messages={[]} sending={false} onSend={onSend} />)

    const input = screen.getByRole('textbox')
    fireEvent.change(input, { target: { value: 'what is in staging' } })
    fireEvent.click(screen.getByText('Send'))

    expect(onSend).toHaveBeenCalledWith('what is in staging')
    expect(input).toHaveValue('')
  })

  it('does not call onSend while a turn is already sending', () => {
    const onSend = vi.fn()
    render(<ChatPanel messages={[]} sending={true} onSend={onSend} />)

    fireEvent.click(screen.getByText('Send'))

    expect(onSend).not.toHaveBeenCalled()
  })
})
