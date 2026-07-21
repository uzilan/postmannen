import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { DetailPanel, detailContentLabel } from './DetailPanel'
import type { DetailContent } from './DetailPanel'

const noopProps = {
  onValueChange: vi.fn(),
  onEnabledToggle: vi.fn(),
  onAddKey: vi.fn(),
  onDeleteKey: vi.fn(),
  onRenameKey: vi.fn(),
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

  it('calls onRenameKey with old and new key when the key cell is edited', () => {
    const onRenameKey = vi.fn()
    const content: DetailContent = {
      kind: 'environments',
      details: [{ id: 'env-1', uid: 'env-1-uid', name: 'Staging', values: [{ key: 'BASE_URL', value: 'https://staging', enabled: true, type: 'default' }] }],
    }
    render(<DetailPanel content={content} {...noopProps} onRenameKey={onRenameKey} />)

    const input = screen.getByDisplayValue('BASE_URL')
    fireEvent.change(input, { target: { value: 'API_BASE_URL' } })
    fireEvent.blur(input)

    expect(onRenameKey).toHaveBeenCalledWith('BASE_URL', 'API_BASE_URL')
  })

  it('renders method, url, headers and body for a request', () => {
    const content: DetailContent = {
      kind: 'request',
      item: {
        type: 'item',
        name: 'Login',
        method: 'POST',
        url: 'https://auth.example.com/login',
        headers: [{ key: 'Content-Type', value: 'application/json' }],
        body: '{"user":"x"}',
      },
    }
    render(<DetailPanel content={content} {...noopProps} />)
    expect(screen.getByText('POST')).toBeInTheDocument()
    expect(screen.getByText('https://auth.example.com/login')).toBeInTheDocument()
    expect(screen.getByText('Content-Type')).toBeInTheDocument()
    expect(screen.getByText('application/json')).toBeInTheDocument()
    expect(screen.getByText('{"user":"x"}')).toBeInTheDocument()
  })

  it('renders a placeholder when a request has no raw body', () => {
    const content: DetailContent = {
      kind: 'request',
      item: {
        type: 'item',
        name: 'Health Check',
        method: 'GET',
        url: 'https://auth.example.com/health',
        headers: [],
        body: null,
      },
    }
    render(<DetailPanel content={content} {...noopProps} />)
    expect(screen.getByText('(no body)')).toBeInTheDocument()
  })
})

describe('detailContentLabel', () => {
  it('returns null for none and loading', () => {
    expect(detailContentLabel({ kind: 'none' })).toBeNull()
    expect(detailContentLabel({ kind: 'loading' })).toBeNull()
  })

  it('returns "Variables" for collection variables', () => {
    expect(detailContentLabel({ kind: 'collectionVariables', variables: [] })).toBe('Variables')
  })

  it('returns comma-joined environment names for environments', () => {
    const content = {
      kind: 'environments' as const,
      details: [
        { id: 'env-1', uid: 'env-1-uid', name: 'Staging', values: [] },
        { id: 'env-2', uid: 'env-2-uid', name: 'Production', values: [] },
      ],
    }
    expect(detailContentLabel(content)).toBe('Staging, Production')
  })

  it('returns the request name for a request', () => {
    const content = {
      kind: 'request' as const,
      item: { type: 'item' as const, name: 'Login', method: 'POST', url: 'x', headers: [], body: null },
    }
    expect(detailContentLabel(content)).toBe('Login')
  })
})
