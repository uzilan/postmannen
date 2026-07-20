import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ConfirmDialog } from './ConfirmDialog'

describe('ConfirmDialog', () => {
  it('renders the title and message', () => {
    render(
      <ConfirmDialog
        open={true}
        title="Delete collection"
        message='Delete collection "Auth API"? This cannot be undone.'
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />
    )

    expect(screen.getByText('Delete collection')).toBeInTheDocument()
    expect(screen.getByText('Delete collection "Auth API"? This cannot be undone.')).toBeInTheDocument()
  })

  it('calls onConfirm when Confirm is clicked', () => {
    const onConfirm = vi.fn()
    render(
      <ConfirmDialog open={true} title="t" message="m" onConfirm={onConfirm} onCancel={vi.fn()} />
    )

    fireEvent.click(screen.getByText('Confirm'))

    expect(onConfirm).toHaveBeenCalled()
  })

  it('calls onCancel when Cancel is clicked', () => {
    const onCancel = vi.fn()
    render(
      <ConfirmDialog open={true} title="t" message="m" onConfirm={vi.fn()} onCancel={onCancel} />
    )

    fireEvent.click(screen.getByText('Cancel'))

    expect(onCancel).toHaveBeenCalled()
  })
})
