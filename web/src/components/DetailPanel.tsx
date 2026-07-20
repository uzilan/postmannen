import { Box, Button, IconButton, Table, TableBody, TableCell, TableHead, TableRow, Checkbox, TextField, Typography } from '@mui/material'
import DeleteIcon from '@mui/icons-material/Delete'
import DragIndicatorIcon from '@mui/icons-material/DragIndicator'
import { Fragment, useEffect, useState } from 'react'
import type { CollectionNode, CollectionVariable, EnvironmentDetail } from '../api'
import { DndContext, PointerSensor, useSensor, useSensors, type DragEndEvent } from '@dnd-kit/core'
import { SortableContext, arrayMove, horizontalListSortingStrategy, useSortable } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'

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

function SortableColumnHeader({ uid, name }: { uid: string; name: string }) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id: uid })
  return (
    <TableCell
      ref={setNodeRef}
      style={{ transform: CSS.Transform.toString(transform), transition, opacity: isDragging ? 0.5 : 1 }}
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
  const sensors = useSensors(useSensor(PointerSensor))

  const detailUidsKey = content.kind === 'environments' ? content.details.map((d) => d.uid).join(',') : ''
  useEffect(() => {
    if (content.kind === 'environments') setColumnOrder(content.details.map((d) => d.uid))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [detailUidsKey])

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
        <Typography>
          <strong>{item.method}</strong> {item.url}
        </Typography>
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
      <Table>
        <TableHead>
          <DndContext sensors={sensors} onDragEnd={handleDragEnd}>
            <TableRow>
              <TableCell>Key</TableCell>
              <SortableContext items={columnOrder} strategy={horizontalListSortingStrategy}>
                {orderedDetails.map((d) => (
                  <SortableColumnHeader key={d.uid} uid={d.uid} name={d.name} />
                ))}
              </SortableContext>
              <TableCell />
            </TableRow>
          </DndContext>
        </TableHead>
        <TableBody>
          {keys.map((key) => (
            <TableRow key={key}>
              <TableCell>{key}</TableCell>
              {orderedDetails.map((d) => {
                const cell = d.values.find((v) => v.key === key)
                return (
                  <TableCell key={d.uid}>
                    <Checkbox
                      checked={cell?.enabled ?? false}
                      onChange={() => onEnabledToggle(d.uid, key)}
                      size="small"
                    />
                    <TextField
                      variant="standard"
                      defaultValue={cell?.value ?? ''}
                      onBlur={(e) => onValueChange(d.uid, key, e.target.value)}
                    />
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
    </>
  )
}
