import { Tab, Tabs } from '@mui/material'

export type AppTab = 'collections' | 'environments'

export function TabBar(props: { active: AppTab; onChange: (tab: AppTab) => void }) {
  const { active, onChange } = props
  return (
    <Tabs value={active} onChange={(_, value) => onChange(value)}>
      <Tab label="Collections" value="collections" />
      <Tab label="Environments" value="environments" />
    </Tabs>
  )
}
