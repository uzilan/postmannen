import { describe, expect, it } from 'vitest'
import { isWriteTool } from './api'

describe('isWriteTool', () => {
  it('recognizes real Postman MCP tool names (mcp__postman__<camelCaseVerb><Noun>)', () => {
    expect(isWriteTool('mcp__postman__createEnvironment')).toBe(true)
    expect(isWriteTool('mcp__postman__updateEnvironment')).toBe(true)
    expect(isWriteTool('mcp__postman__deleteEnvironment')).toBe(true)
  })

  it('does not flag read-only MCP tool names as write tools', () => {
    expect(isWriteTool('mcp__postman__getEnvironment')).toBe(false)
    expect(isWriteTool('mcp__postman__listWorkspaces')).toBe(false)
    expect(isWriteTool('ToolSearch')).toBe(false)
  })
})
