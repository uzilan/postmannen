import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { WorkspaceSelector } from './WorkspaceSelector'
import type { Workspace } from '../api'

const workspaces: Workspace[] = [
  { id: 'ws-1', name: 'Engineering', type: 'team' },
  { id: 'ws-2', name: 'Personal', type: 'personal' },
]

describe('WorkspaceSelector', () => {
  it('renders the selected workspace name', () => {
    render(<WorkspaceSelector workspaces={workspaces} selectedId="ws-1" onSelect={vi.fn()} />)
    expect(screen.getByText('Engineering')).toBeInTheDocument()
  })

  it('calls onSelect with the chosen workspace id', () => {
    const onSelect = vi.fn()
    render(<WorkspaceSelector workspaces={workspaces} selectedId="ws-1" onSelect={onSelect} />)

    fireEvent.mouseDown(screen.getByRole('combobox'))
    fireEvent.click(screen.getByText('Personal'))

    expect(onSelect).toHaveBeenCalledWith('ws-2')
  })
})
