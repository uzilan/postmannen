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
  it('starts with folders collapsed, hiding their children', () => {
    render(<CollectionTree detail={detail} onSelectVariables={vi.fn()} />)
    expect(screen.getByText('Users')).toBeInTheDocument()
    expect(screen.queryByText('Login')).not.toBeInTheDocument()
  })

  it('expands a folder on click, revealing its children', () => {
    render(<CollectionTree detail={detail} onSelectVariables={vi.fn()} />)
    fireEvent.click(screen.getByText('Users'))
    expect(screen.getByText('Login')).toBeInTheDocument()
    expect(screen.getByText('Signup')).toBeInTheDocument()
  })
})
