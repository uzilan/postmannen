import { Box, Button, IconButton, Table, TableBody, TableCell, TableHead, TableRow, Checkbox, TextField, Typography } from '@mui/material'
import DeleteIcon from '@mui/icons-material/Delete'
import DragIndicatorIcon from '@mui/icons-material/DragIndicator'
import { Fragment, useEffect, useState } from 'react'
import type { CollectionNode, CollectionVariable, EnvironmentDetail } from '../api'
import { DndContext, PointerSensor, useSensor, useSensors, type DragEndEvent } from '@dnd-kit/core'
import { SortableContext, arrayMove, horizontalListSortingStrategy, useSortable } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { MethodLabel } from './CollectionTree'

type RequestItemNode = Extract<CollectionNode, { type: 'item' }>

const JSON_TOKEN_PATTERN =
  /(\/\/.*$)|("(?:\\.|[^"\\])*"(?=\s*:))|("(?:\\.|[^"\\])*")|(\b(?:true|false|null)\b)|(-?\b\d+(?:\.\d+)?(?:[eE][+-]?\d+)?\b)|([{}[\],:])/gm

const JSON_TOKEN_COLORS = {
  comment: '#6a9955',
  key: '#9cdcfe',
  string: '#ce9178',
  literal: '#569cd6',
  number: '#b5cea8',
  punctuation: '#d4d4d4',
} as const

function JsonBody({ text }: { text: string }) {
  const parts: React.ReactNode[] = []
  let lastIndex = 0
  let match: RegExpExecArray | null
  JSON_TOKEN_PATTERN.lastIndex = 0
  while ((match = JSON_TOKEN_PATTERN.exec(text))) {
    if (match.index > lastIndex) parts.push(text.slice(lastIndex, match.index))
    const [full, comment, key, string, literal, number] = match
    const color = comment
      ? JSON_TOKEN_COLORS.comment
      : key
        ? JSON_TOKEN_COLORS.key
        : string
          ? JSON_TOKEN_COLORS.string
          : literal
            ? JSON_TOKEN_COLORS.literal
            : number
              ? JSON_TOKEN_COLORS.number
              : JSON_TOKEN_COLORS.punctuation
    parts.push(
      <Box component="span" key={match.index} sx={{ color }}>
        {full}
      </Box>
    )
    lastIndex = match.index + full.length
  }
  if (lastIndex < text.length) parts.push(text.slice(lastIndex))
  return (
    <Typography component="pre">
      {parts.map((p, i) => (
        <Fragment key={i}>{p}</Fragment>
      ))}
    </Typography>
  )
}

const DEFAULT_COLUMN_WIDTH = 400
const DEFAULT_KEY_COLUMN_WIDTH = 250
const MIN_COLUMN_WIDTH = 120
const KEY_COLUMN_ID = '__key__'

function useColumnResize(width: number, onResize: (width: number) => void) {
  return function handleResizeStart(e: React.MouseEvent) {
    e.preventDefault()
    e.stopPropagation()
    const startX = e.clientX
    const startWidth = width
    function handleMouseMove(moveEvent: MouseEvent) {
      onResize(Math.max(MIN_COLUMN_WIDTH, startWidth + moveEvent.clientX - startX))
    }
    function handleMouseUp() {
      window.removeEventListener('mousemove', handleMouseMove)
      window.removeEventListener('mouseup', handleMouseUp)
    }
    window.addEventListener('mousemove', handleMouseMove)
    window.addEventListener('mouseup', handleMouseUp)
  }
}

function ColumnResizeHandle({ name, onMouseDown }: { name: string; onMouseDown: (e: React.MouseEvent) => void }) {
  return (
    <Box
      onMouseDown={onMouseDown}
      aria-label={`resize column ${name}`}
      sx={{
        position: 'absolute',
        top: 0,
        right: 0,
        height: '100%',
        width: '6px',
        cursor: 'col-resize',
        userSelect: 'none',
        '&::after': {
          content: '""',
          position: 'absolute',
          top: '15%',
          bottom: '15%',
          left: '2px',
          width: '2px',
          borderRadius: '1px',
          backgroundColor: 'divider',
        },
        '&:hover::after': {
          backgroundColor: 'primary.main',
        },
      }}
    />
  )
}

function ResizableColumnHeader({
  name,
  width,
  onResize,
}: {
  name: string
  width: number
  onResize: (width: number) => void
}) {
  const handleResizeStart = useColumnResize(width, onResize)
  return (
    <TableCell style={{ width, minWidth: width, maxWidth: width, position: 'relative' }}>
      {name}
      <ColumnResizeHandle name={name} onMouseDown={handleResizeStart} />
    </TableCell>
  )
}

function SortableColumnHeader({
  uid,
  name,
  width,
  onResize,
}: {
  uid: string
  name: string
  width: number
  onResize: (uid: string, width: number) => void
}) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id: uid })
  const handleResizeStart = useColumnResize(width, (w) => onResize(uid, w))

  return (
    <TableCell
      ref={setNodeRef}
      style={{
        transform: CSS.Transform.toString(transform),
        transition,
        opacity: isDragging ? 0.5 : 1,
        width,
        minWidth: width,
        maxWidth: width,
        position: 'relative',
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
        <Box
          {...attributes}
          {...listeners}
          sx={{ cursor: 'grab', display: 'flex', alignItems: 'center' }}
          aria-label={`drag column ${name}`}
        >
          <DragIndicatorIcon fontSize="small" />
        </Box>
        {name}
      </Box>
      <ColumnResizeHandle name={name} onMouseDown={handleResizeStart} />
    </TableCell>
  )
}

export type DetailContent =
  | { kind: 'none' }
  | { kind: 'loading' }
  | { kind: 'collectionVariables'; variables: CollectionVariable[] }
  | { kind: 'environments'; details: EnvironmentDetail[] }
  | { kind: 'request'; item: RequestItemNode }

export function detailContentLabel(content: DetailContent): string | null {
  if (content.kind === 'collectionVariables') return 'Variables'
  if (content.kind === 'environments') return content.details.map((d) => d.name).join(', ')
  if (content.kind === 'request') return content.item.name
  return null
}

export function DetailPanel(props: {
  content: DetailContent
  onValueChange: (environmentUid: string, key: string, newValue: string) => void
  onEnabledToggle: (environmentUid: string, key: string) => void
  onAddKey: (key: string) => void
  onDeleteKey: (key: string) => void
}) {
  const { content, onValueChange, onEnabledToggle, onAddKey, onDeleteKey } = props
  const [newKey, setNewKey] = useState('')
  const [columnOrder, setColumnOrder] = useState<string[]>([])
  const [columnWidths, setColumnWidths] = useState<Record<string, number>>({})
  const sensors = useSensors(useSensor(PointerSensor))

  const detailUidsKey = content.kind === 'environments' ? content.details.map((d) => d.uid).join(',') : ''
  useEffect(() => {
    if (content.kind === 'environments') {
      setColumnOrder(content.details.map((d) => d.uid))
      setColumnWidths((prev) => {
        const next = { ...prev }
        if (next[KEY_COLUMN_ID] == null) next[KEY_COLUMN_ID] = DEFAULT_KEY_COLUMN_WIDTH
        for (const d of content.details) if (next[d.uid] == null) next[d.uid] = DEFAULT_COLUMN_WIDTH
        return next
      })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [detailUidsKey])

  function handleColumnResize(uid: string, width: number) {
    setColumnWidths((prev) => ({ ...prev, [uid]: width }))
  }

  if (content.kind === 'none') return null
  if (content.kind === 'loading') return <Typography>Loading...</Typography>

  if (content.kind === 'collectionVariables') {
    return (
      <Table>
        <TableBody>
          {content.variables.map((v) => (
            <TableRow key={v.key}>
              <TableCell>{v.key}</TableCell>
              <TableCell>{v.value}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    )
  }

  if (content.kind === 'request') {
    const { item } = content
    return (
      <>
        <fieldset>
          <legend>Request</legend>
          <Typography>
            <MethodLabel method={item.method} /> {item.url}
          </Typography>
        </fieldset>
        {item.headers.length > 0 && (
          <fieldset>
            <legend>Headers</legend>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Header</TableCell>
                  <TableCell>Value</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {item.headers.map((h) => (
                  <TableRow key={h.key}>
                    <TableCell>{h.key}</TableCell>
                    <TableCell>{h.value}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </fieldset>
        )}
        {item.body != null && (
          <fieldset>
            <legend>Body</legend>
            <JsonBody text={item.body} />
          </fieldset>
        )}
      </>
    )
  }

  const keys = Array.from(new Set(content.details.flatMap((d) => d.values.map((v) => v.key))))
  const orderedDetails = columnOrder
    .map((uid) => content.details.find((d) => d.uid === uid))
    .filter((d): d is EnvironmentDetail => d != null)

  function handleDragEnd(event: DragEndEvent) {
    const { active, over } = event
    if (over == null || active.id === over.id) return
    setColumnOrder((prev) => {
      const oldIndex = prev.indexOf(String(active.id))
      const newIndex = prev.indexOf(String(over.id))
      return arrayMove(prev, oldIndex, newIndex)
    })
  }

  return (
    <>
      <TextField
        size="small"
        placeholder="new key"
        value={newKey}
        onChange={(e) => setNewKey(e.target.value)}
      />
      <Button
        onClick={() => {
          if (!newKey.trim()) return
          onAddKey(newKey)
          setNewKey('')
        }}
      >
        Add key
      </Button>
      <Table sx={{ tableLayout: 'fixed' }}>
        <TableHead>
          <DndContext sensors={sensors} onDragEnd={handleDragEnd}>
            <TableRow>
              <ResizableColumnHeader
                name="Key"
                width={columnWidths[KEY_COLUMN_ID] ?? DEFAULT_KEY_COLUMN_WIDTH}
                onResize={(w) => handleColumnResize(KEY_COLUMN_ID, w)}
              />
              <SortableContext items={columnOrder} strategy={horizontalListSortingStrategy}>
                {orderedDetails.map((d) => (
                  <SortableColumnHeader
                    key={d.uid}
                    uid={d.uid}
                    name={d.name}
                    width={columnWidths[d.uid] ?? DEFAULT_COLUMN_WIDTH}
                    onResize={handleColumnResize}
                  />
                ))}
              </SortableContext>
              <TableCell />
            </TableRow>
          </DndContext>
        </TableHead>
        <TableBody sx={{ '& .MuiTableCell-root': { borderBottom: 'none' } }}>
          {keys.map((key) => (
            <TableRow key={key}>
              <TableCell
                style={{
                  width: columnWidths[KEY_COLUMN_ID] ?? DEFAULT_KEY_COLUMN_WIDTH,
                  minWidth: columnWidths[KEY_COLUMN_ID] ?? DEFAULT_KEY_COLUMN_WIDTH,
                  maxWidth: columnWidths[KEY_COLUMN_ID] ?? DEFAULT_KEY_COLUMN_WIDTH,
                }}
              >
                {key}
              </TableCell>
              {orderedDetails.map((d) => {
                const cell = d.values.find((v) => v.key === key)
                const width = columnWidths[d.uid] ?? DEFAULT_COLUMN_WIDTH
                return (
                  <TableCell key={d.uid} style={{ width, minWidth: width, maxWidth: width }}>
                    <Box sx={{ display: 'flex', alignItems: 'center' }}>
                      <Checkbox
                        checked={cell?.enabled ?? false}
                        onChange={() => onEnabledToggle(d.uid, key)}
                        size="small"
                      />
                      <TextField
                        variant="standard"
                        fullWidth
                        defaultValue={cell?.value ?? ''}
                        onBlur={(e) => onValueChange(d.uid, key, e.target.value)}
                      />
                    </Box>
                  </TableCell>
                )
              })}
              <TableCell>
                <IconButton aria-label={`delete row ${key}`} onClick={() => onDeleteKey(key)} size="small">
                  <DeleteIcon fontSize="small" />
                </IconButton>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </>
  )
}
