import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { CollectionTree, collectNodeIds } from './CollectionTree'
import type { CollectionDetail } from '../api'

const loginItem = {
  type: 'item' as const,
  name: 'Login',
  method: 'POST',
  url: 'https://auth.example.com/login',
  headers: [{ key: 'Content-Type', value: 'application/json' }],
  body: '{"user":"x"}',
}

const signupItem = {
  type: 'item' as const,
  name: 'Signup',
  method: 'POST',
  url: 'https://auth.example.com/signup',
  headers: [],
  body: null,
}

const healthCheckItem = {
  type: 'item' as const,
  name: 'Health Check',
  method: 'GET',
  url: 'https://auth.example.com/health',
  headers: [],
  body: null,
}

const detail: CollectionDetail = {
  uid: 'col-1-uid',
  name: 'Auth API',
  items: [
    { type: 'folder', name: 'Users', children: [loginItem, signupItem] },
    healthCheckItem,
  ],
  variables: [{ key: 'base_url', value: 'https://x', enabled: true }],
}

describe('collectNodeIds', () => {
  it('returns position-based ids for folders only', () => {
    expect(collectNodeIds('col-1-uid', detail.items)).toEqual(['col-1-uid/0'])
  })
})

describe('CollectionTree', () => {
  it('starts fully collapsed, showing only the collection name', () => {
    render(<CollectionTree detail={detail} onSelectVariables={vi.fn()} onSelectRequest={vi.fn()} onDeleteCollection={vi.fn()} onRenameCollection={vi.fn()} selectedRequestItem={null} />)
    expect(screen.getByText('Auth API')).toBeInTheDocument()
    expect(screen.queryByText('Users')).not.toBeInTheDocument()
  })

  it('expands the collection on click, revealing its top-level folders/items still collapsed', () => {
    render(<CollectionTree detail={detail} onSelectVariables={vi.fn()} onSelectRequest={vi.fn()} onDeleteCollection={vi.fn()} onRenameCollection={vi.fn()} selectedRequestItem={null} />)
    fireEvent.click(screen.getByText('Auth API'))
    expect(screen.getByText('Users')).toBeInTheDocument()
    expect(screen.queryByText('Login')).not.toBeInTheDocument()
  })

  it('expands a nested folder on click, revealing its children', () => {
    render(<CollectionTree detail={detail} onSelectVariables={vi.fn()} onSelectRequest={vi.fn()} onDeleteCollection={vi.fn()} onRenameCollection={vi.fn()} selectedRequestItem={null} />)
    fireEvent.click(screen.getByText('Auth API'))
    fireEvent.click(screen.getByText('Users'))
    expect(screen.getByText('Login')).toBeInTheDocument()
    expect(screen.getByText('Signup')).toBeInTheDocument()
  })

  it('calls onSelectVariables with the collection variables when the collection row is clicked', () => {
    const onSelectVariables = vi.fn()
    render(<CollectionTree detail={detail} onSelectVariables={onSelectVariables} onSelectRequest={vi.fn()} onDeleteCollection={vi.fn()} onRenameCollection={vi.fn()} selectedRequestItem={null} />)
    fireEvent.click(screen.getByText('Auth API'))
    expect(onSelectVariables).toHaveBeenCalledWith(detail.variables)
  })

  it('calls onSelectRequest with the item node when a request row is clicked', () => {
    const onSelectRequest = vi.fn()
    render(<CollectionTree detail={detail} onSelectVariables={vi.fn()} onSelectRequest={onSelectRequest} onDeleteCollection={vi.fn()} onRenameCollection={vi.fn()} selectedRequestItem={null} />)
    fireEvent.click(screen.getByText('Auth API'))
    fireEvent.click(screen.getByText('Health Check'))
    expect(onSelectRequest).toHaveBeenCalledWith(healthCheckItem)
  })

  it('renders expanded by default when defaultExpanded is true', () => {
    render(
      <CollectionTree detail={detail} defaultExpanded onSelectVariables={vi.fn()} onSelectRequest={vi.fn()} onDeleteCollection={vi.fn()} onRenameCollection={vi.fn()} selectedRequestItem={null} />
    )
    expect(screen.getByText('Users')).toBeInTheDocument()
  })

  it('calls onDeleteCollection with the collection uid and name when the delete icon is clicked', () => {
    const onDeleteCollection = vi.fn()
    render(
      <CollectionTree
        detail={detail}
        onSelectVariables={vi.fn()}
        onSelectRequest={vi.fn()}
        onDeleteCollection={onDeleteCollection}
        onRenameCollection={vi.fn()}
        selectedRequestItem={null}
      />
    )

    fireEvent.click(screen.getByLabelText('Delete collection'))

    expect(onDeleteCollection).toHaveBeenCalledWith('col-1-uid', 'Auth API')
  })

  it('does not toggle or select variables when the delete icon is clicked', () => {
    const onSelectVariables = vi.fn()
    render(
      <CollectionTree
        detail={detail}
        onSelectVariables={onSelectVariables}
        onSelectRequest={vi.fn()}
        onDeleteCollection={vi.fn()}
        onRenameCollection={vi.fn()}
        selectedRequestItem={null}
      />
    )

    fireEvent.click(screen.getByLabelText('Delete collection'))

    expect(onSelectVariables).not.toHaveBeenCalled()
    expect(screen.queryByText('Users')).not.toBeInTheDocument()
  })

  it('shows an inline text field pre-filled with the current name when the rename icon is clicked', () => {
    render(
      <CollectionTree
        detail={detail}
        onSelectVariables={vi.fn()}
        onSelectRequest={vi.fn()}
        onDeleteCollection={vi.fn()}
        onRenameCollection={vi.fn()}
        selectedRequestItem={null}
      />
    )

    fireEvent.click(screen.getByLabelText('Rename collection'))

    expect(screen.getByDisplayValue('Auth API')).toBeInTheDocument()
  })

  it('calls onRenameCollection with the uid and new name on Enter, and exits edit mode', () => {
    const onRenameCollection = vi.fn()
    render(
      <CollectionTree
        detail={detail}
        onSelectVariables={vi.fn()}
        onSelectRequest={vi.fn()}
        onDeleteCollection={vi.fn()}
        onRenameCollection={onRenameCollection}
        selectedRequestItem={null}
      />
    )

    fireEvent.click(screen.getByLabelText('Rename collection'))
    const input = screen.getByDisplayValue('Auth API')
    fireEvent.change(input, { target: { value: 'Renamed API' } })
    fireEvent.keyDown(input, { key: 'Enter' })

    expect(onRenameCollection).toHaveBeenCalledWith('col-1-uid', 'Renamed API')
    expect(screen.queryByDisplayValue('Renamed API')).not.toBeInTheDocument()
  })

  it('discards the edit and calls nothing on Escape', () => {
    const onRenameCollection = vi.fn()
    render(
      <CollectionTree
        detail={detail}
        onSelectVariables={vi.fn()}
        onSelectRequest={vi.fn()}
        onDeleteCollection={vi.fn()}
        onRenameCollection={onRenameCollection}
        selectedRequestItem={null}
      />
    )

    fireEvent.click(screen.getByLabelText('Rename collection'))
    const input = screen.getByDisplayValue('Auth API')
    fireEvent.change(input, { target: { value: 'Discarded' } })
    fireEvent.keyDown(input, { key: 'Escape' })

    expect(onRenameCollection).not.toHaveBeenCalled()
    expect(screen.getByText('Auth API')).toBeInTheDocument()
  })

  it('does not toggle or select variables when the rename icon is clicked', () => {
    const onSelectVariables = vi.fn()
    render(
      <CollectionTree
        detail={detail}
        onSelectVariables={onSelectVariables}
        onSelectRequest={vi.fn()}
        onDeleteCollection={vi.fn()}
        onRenameCollection={vi.fn()}
        selectedRequestItem={null}
      />
    )

    fireEvent.click(screen.getByLabelText('Rename collection'))

    expect(onSelectVariables).not.toHaveBeenCalled()
    expect(screen.queryByText('Users')).not.toBeInTheDocument()
  })

  it('highlights the selected request item and no other row', () => {
    render(
      <CollectionTree
        detail={detail}
        defaultExpanded
        onSelectVariables={vi.fn()}
        onSelectRequest={vi.fn()}
        onDeleteCollection={vi.fn()}
        onRenameCollection={vi.fn()}
        selectedRequestItem={healthCheckItem}
      />
    )

    expect(screen.getByText('Health Check').closest('.MuiListItemButton-root')).toHaveClass('Mui-selected')
    expect(screen.getByText('Login').closest('.MuiListItemButton-root')).not.toHaveClass('Mui-selected')
  })
})
