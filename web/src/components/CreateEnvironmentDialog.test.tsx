import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { CreateEnvironmentDialog } from './CreateEnvironmentDialog'

describe('CreateEnvironmentDialog', () => {
  it('calls onCreate with the entered name', () => {
    const onCreate = vi.fn()
    render(<CreateEnvironmentDialog open={true} onCreate={onCreate} onClose={vi.fn()} />)

    fireEvent.change(screen.getByLabelText('Environment name'), { target: { value: 'QA' } })
    fireEvent.click(screen.getByText('Create'))

    expect(onCreate).toHaveBeenCalledWith('QA')
  })
})
