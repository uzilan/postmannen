import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { EnvironmentList } from './EnvironmentList'
import type { Environment } from '../api'

const environments: Environment[] = [
  { id: 'env-1', name: 'Staging', uid: 'env-1-uid' },
  { id: 'env-2', name: 'Production', uid: 'env-2-uid' },
]

describe('EnvironmentList', () => {
  it('calls onHighlight when a row is clicked', () => {
    const onHighlight = vi.fn()
    render(
      <EnvironmentList
        environments={environments}
        highlightedId={null}
        markedIds={new Set()}
        onHighlight={onHighlight}
        onToggleMark={vi.fn()}
        onDeleteEnvironment={vi.fn()}
        onRenameEnvironment={vi.fn()}
      />
    )
    fireEvent.click(screen.getByText('Staging'))
    expect(onHighlight).toHaveBeenCalledWith('env-1')
  })

  it('calls onToggleMark when a row checkbox is toggled, not onHighlight', () => {
    const onHighlight = vi.fn()
    const onToggleMark = vi.fn()
    render(
      <EnvironmentList
        environments={environments}
        highlightedId={null}
        markedIds={new Set()}
        onHighlight={onHighlight}
        onToggleMark={onToggleMark}
        onDeleteEnvironment={vi.fn()}
        onRenameEnvironment={vi.fn()}
      />
    )
    fireEvent.click(screen.getAllByRole('checkbox')[0])
    expect(onToggleMark).toHaveBeenCalledWith('env-1')
    expect(onHighlight).not.toHaveBeenCalled()
  })

  it('calls onDeleteEnvironment with the environment uid and name when the delete icon is clicked, not onHighlight', () => {
    const onHighlight = vi.fn()
    const onDeleteEnvironment = vi.fn()
    render(
      <EnvironmentList
        environments={environments}
        highlightedId={null}
        markedIds={new Set()}
        onHighlight={onHighlight}
        onToggleMark={vi.fn()}
        onDeleteEnvironment={onDeleteEnvironment}
        onRenameEnvironment={vi.fn()}
      />
    )
    fireEvent.click(screen.getAllByLabelText('Delete environment')[0])
    expect(onDeleteEnvironment).toHaveBeenCalledWith('env-1-uid', 'Staging')
    expect(onHighlight).not.toHaveBeenCalled()
  })

  it('shows an inline text field pre-filled with the row name when its rename icon is clicked', () => {
    render(
      <EnvironmentList
        environments={environments}
        highlightedId={null}
        markedIds={new Set()}
        onHighlight={vi.fn()}
        onToggleMark={vi.fn()}
        onDeleteEnvironment={vi.fn()}
        onRenameEnvironment={vi.fn()}
      />
    )

    fireEvent.click(screen.getAllByLabelText('Rename environment')[0])

    expect(screen.getByDisplayValue('Staging')).toBeInTheDocument()
    expect(screen.getByText('Production')).toBeInTheDocument()
  })

  it('calls onRenameEnvironment with the uid and new name on Enter, not onHighlight', () => {
    const onRenameEnvironment = vi.fn()
    const onHighlight = vi.fn()
    render(
      <EnvironmentList
        environments={environments}
        highlightedId={null}
        markedIds={new Set()}
        onHighlight={onHighlight}
        onToggleMark={vi.fn()}
        onDeleteEnvironment={vi.fn()}
        onRenameEnvironment={onRenameEnvironment}
      />
    )

    fireEvent.click(screen.getAllByLabelText('Rename environment')[0])
    const input = screen.getByDisplayValue('Staging')
    fireEvent.change(input, { target: { value: 'Staging Renamed' } })
    fireEvent.keyDown(input, { key: 'Enter' })

    expect(onRenameEnvironment).toHaveBeenCalledWith('env-1-uid', 'Staging Renamed')
    expect(onHighlight).not.toHaveBeenCalled()
  })

  it('discards the edit and calls nothing on Escape', () => {
    const onRenameEnvironment = vi.fn()
    render(
      <EnvironmentList
        environments={environments}
        highlightedId={null}
        markedIds={new Set()}
        onHighlight={vi.fn()}
        onToggleMark={vi.fn()}
        onDeleteEnvironment={vi.fn()}
        onRenameEnvironment={onRenameEnvironment}
      />
    )

    fireEvent.click(screen.getAllByLabelText('Rename environment')[0])
    const input = screen.getByDisplayValue('Staging')
    fireEvent.change(input, { target: { value: 'Discarded' } })
    fireEvent.keyDown(input, { key: 'Escape' })

    expect(onRenameEnvironment).not.toHaveBeenCalled()
    expect(screen.getByText('Staging')).toBeInTheDocument()
  })
})
