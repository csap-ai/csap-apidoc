import { Outlet } from 'react-router-dom'
import { useAppStore, useSettingsStore } from '@/store'
import Navbar from './components/Navbar'
import Sidebar from './components/Sidebar'
import { useResizeHandler } from './hooks/useResizeHandler'
import './index.scss'

const Layout: React.FC = () => {
  const { sidebar, device, closeSidebar } = useAppStore()
  const { fixedHeader } = useSettingsStore()
  
  // 使用resize handler hook
  useResizeHandler()

  const classObj = {
    hideSidebar: !sidebar.opened,
    openSidebar: sidebar.opened,
    withoutAnimation: sidebar.withoutAnimation,
    mobile: device === 'mobile'
  }

  const handleClickOutside = () => {
    closeSidebar(false)
  }

  return (
    <div className={`app-wrapper ${Object.keys(classObj).filter(key => classObj[key as keyof typeof classObj]).join(' ')}`}>
      {device === 'mobile' && sidebar.opened && (
        <div className="drawer-bg" onClick={handleClickOutside} />
      )}
      <Sidebar className="sidebar-container" />
      <div className="main-container">
        <div className={fixedHeader ? 'fixed-header' : ''}>
          <Navbar />
        </div>
        <section className="app-main">
          <div className="app-main-content">
            <Outlet />
          </div>
        </section>
      </div>
    </div>
  )
}

export default Layout

