import { render, screen, fireEvent } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { CollectionTree, collectNodeIds } from './CollectionTree'
import type { CollectionDetail } from '../api'

const detail: CollectionDetail = {
  uid: 'col-1-uid',
  name: 'Auth API',
  items: [
    { type: 'folder', name: 'Users', children: [{ type: 'item', name: 'Login' }, { type: 'item', name: 'Signup' }] },
    { type: 'item', name: 'Health Check' },
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
    render(<CollectionTree detail={detail} onSelectVariables={vi.fn()} />)
    expect(screen.getByText('Auth API')).toBeInTheDocument()
    expect(screen.queryByText('Users')).not.toBeInTheDocument()
  })

  it('expands the collection on click, revealing its top-level folders/items still collapsed', () => {
    render(<CollectionTree detail={detail} onSelectVariables={vi.fn()} />)
    fireEvent.click(screen.getByText('Auth API'))
    expect(screen.getByText('Users')).toBeInTheDocument()
    expect(screen.queryByText('Login')).not.toBeInTheDocument()
  })

  it('expands a nested folder on click, revealing its children', () => {
    render(<CollectionTree detail={detail} onSelectVariables={vi.fn()} />)
    fireEvent.click(screen.getByText('Auth API'))
    fireEvent.click(screen.getByText('Users'))
    expect(screen.getByText('Login')).toBeInTheDocument()
    expect(screen.getByText('Signup')).toBeInTheDocument()
  })

  it('calls onSelectVariables with the collection variables when the collection row is clicked', () => {
    const onSelectVariables = vi.fn()
    render(<CollectionTree detail={detail} onSelectVariables={onSelectVariables} />)
    fireEvent.click(screen.getByText('Auth API'))
    expect(onSelectVariables).toHaveBeenCalledWith(detail.variables)
  })
})
