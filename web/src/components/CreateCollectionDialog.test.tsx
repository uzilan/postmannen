import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { CreateCollectionDialog } from './CreateCollectionDialog'

describe('CreateCollectionDialog', () => {
  it('calls onCreate with the entered name', () => {
    const onCreate = vi.fn()
    render(<CreateCollectionDialog open={true} onCreate={onCreate} onClose={vi.fn()} />)

    fireEvent.change(screen.getByLabelText('Collection name'), { target: { value: 'QA API' } })
    fireEvent.click(screen.getByText('Create'))

    expect(onCreate).toHaveBeenCalledWith('QA API')
  })

  it('does not call onCreate when the name is blank', () => {
    const onCreate = vi.fn()
    render(<CreateCollectionDialog open={true} onCreate={onCreate} onClose={vi.fn()} />)

    fireEvent.click(screen.getByText('Create'))

    expect(onCreate).not.toHaveBeenCalled()
  })
})
