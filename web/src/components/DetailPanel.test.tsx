import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { DetailPanel } from './DetailPanel'
import type { DetailContent } from './DetailPanel'

const noopProps = {
  onValueChange: vi.fn(),
  onEnabledToggle: vi.fn(),
  onAddKey: vi.fn(),
  onDeleteKey: vi.fn(),
}

describe('DetailPanel', () => {
  it('renders nothing distinctive for none', () => {
    const content: DetailContent = { kind: 'none' }
    render(<DetailPanel content={content} {...noopProps} />)
    expect(screen.queryByText(/error/i)).not.toBeInTheDocument()
  })

  it('renders a loading indicator', () => {
    const content: DetailContent = { kind: 'loading' }
    render(<DetailPanel content={content} {...noopProps} />)
    expect(screen.getByText('Loading...')).toBeInTheDocument()
  })

  it('renders collection variables as key/value rows', () => {
    const content: DetailContent = {
      kind: 'collectionVariables',
      variables: [{ key: 'base_url', value: 'https://x', enabled: true }],
    }
    render(<DetailPanel content={content} {...noopProps} />)
    expect(screen.getByText('base_url')).toBeInTheDocument()
    expect(screen.getByText('https://x')).toBeInTheDocument()
  })

  it('renders one column per environment when comparing', () => {
    const content: DetailContent = {
      kind: 'environments',
      details: [
        { id: 'env-1', uid: 'env-1-uid', name: 'Staging', values: [{ key: 'BASE_URL', value: 'https://staging', enabled: true, type: 'default' }] },
        { id: 'env-2', uid: 'env-2-uid', name: 'Production', values: [{ key: 'BASE_URL', value: 'https://prod', enabled: true, type: 'default' }] },
      ],
    }
    render(<DetailPanel content={content} {...noopProps} />)
    expect(screen.getByText('Staging')).toBeInTheDocument()
    expect(screen.getByText('Production')).toBeInTheDocument()
  })

  it('calls onValueChange when an editable cell is changed', () => {
    const onValueChange = vi.fn()
    const content: DetailContent = {
      kind: 'environments',
      details: [{ id: 'env-1', uid: 'env-1-uid', name: 'Staging', values: [{ key: 'BASE_URL', value: 'https://staging', enabled: true, type: 'default' }] }],
    }
    render(<DetailPanel content={content} {...noopProps} onValueChange={onValueChange} />)

    const input = screen.getByDisplayValue('https://staging')
    fireEvent.change(input, { target: { value: 'https://staging2' } })
    fireEvent.blur(input)

    expect(onValueChange).toHaveBeenCalledWith('env-1-uid', 'BASE_URL', 'https://staging2')
  })

  it('calls onDeleteKey with the row key when its delete button is clicked', () => {
    const onDeleteKey = vi.fn()
    const content: DetailContent = {
      kind: 'environments',
      details: [{ id: 'env-1', uid: 'env-1-uid', name: 'Staging', values: [{ key: 'BASE_URL', value: 'https://staging', enabled: true, type: 'default' }] }],
    }
    render(<DetailPanel content={content} {...noopProps} onDeleteKey={onDeleteKey} />)

    fireEvent.click(screen.getByLabelText('delete row BASE_URL'))

    expect(onDeleteKey).toHaveBeenCalledWith('BASE_URL')
  })
})
