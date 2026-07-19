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
      />
    )
    fireEvent.click(screen.getAllByRole('checkbox')[0])
    expect(onToggleMark).toHaveBeenCalledWith('env-1')
    expect(onHighlight).not.toHaveBeenCalled()
  })
})
