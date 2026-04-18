import { Suspense } from 'react'
import { useRoutes } from 'react-router-dom'
import { Spin } from 'antd'
import routes from './router'
import { usePermission } from './permission'

function App() {
  const element = useRoutes(routes)
  usePermission()
  
  return (
    <div id="app">
      <Suspense fallback={
        <div style={{ 
          display: 'flex', 
          justifyContent: 'center', 
          alignItems: 'center', 
          height: '100vh',
          flexDirection: 'column',
          gap: '20px'
        }}>
          <Spin size="large" />
          <div>加载中...</div>
        </div>
      }>
        {element}
      </Suspense>
    </div>
  )
}

export default App

