import { Box } from '@mui/material'

interface ResizableDividerProps {
  onResize: (deltaX: number) => void
  onResizeEnd: () => void
}

export function ResizableDivider({ onResize, onResizeEnd }: ResizableDividerProps) {
  const handleMouseDown = (startEvent: React.MouseEvent) => {
    startEvent.preventDefault()
    let lastX = startEvent.clientX

    const handleMouseMove = (moveEvent: MouseEvent) => {
      onResize(moveEvent.clientX - lastX)
      lastX = moveEvent.clientX
    }

    const handleMouseUp = () => {
      document.removeEventListener('mousemove', handleMouseMove)
      document.removeEventListener('mouseup', handleMouseUp)
      onResizeEnd()
    }

    document.addEventListener('mousemove', handleMouseMove)
    document.addEventListener('mouseup', handleMouseUp)
  }

  return (
    <Box
      onMouseDown={handleMouseDown}
      sx={{
        width: '4px',
        flexShrink: 0,
        cursor: 'col-resize',
        '&:hover': { bgcolor: 'divider' },
      }}
    />
  )
}
