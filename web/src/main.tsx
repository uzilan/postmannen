import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { CssBaseline, ThemeProvider, createTheme, useMediaQuery } from '@mui/material'
import './index.css'
import App from './App.tsx'

function Root() {
  const prefersDark = useMediaQuery('(prefers-color-scheme: dark)')
  const theme = createTheme({ palette: { mode: prefersDark ? 'dark' : 'light' } })

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <App />
    </ThemeProvider>
  )
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Root />
  </StrictMode>,
)
