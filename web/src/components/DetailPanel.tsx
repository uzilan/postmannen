import { Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material'
import type { CollectionVariable, EnvironmentDetail } from '../api'

export type DetailContent =
  | { kind: 'none' }
  | { kind: 'loading' }
  | { kind: 'collectionVariables'; variables: CollectionVariable[] }
  | { kind: 'environments'; details: EnvironmentDetail[] }

export function DetailPanel(props: { content: DetailContent }) {
  const { content } = props

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

  const keys = Array.from(new Set(content.details.flatMap((d) => d.values.map((v) => v.key))))
  return (
    <Table>
      <TableHead>
        <TableRow>
          <TableCell>Key</TableCell>
          {content.details.map((d) => (
            <TableCell key={d.uid}>{d.name}</TableCell>
          ))}
        </TableRow>
      </TableHead>
      <TableBody>
        {keys.map((key) => (
          <TableRow key={key}>
            <TableCell>{key}</TableCell>
            {content.details.map((d) => (
              <TableCell key={d.uid}>{d.values.find((v) => v.key === key)?.value ?? ''}</TableCell>
            ))}
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}
