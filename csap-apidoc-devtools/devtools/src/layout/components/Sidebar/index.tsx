import { Menu, MenuProps } from 'antd'
import { ApiOutlined } from '@ant-design/icons'
import { useLocation, useNavigate } from 'react-router-dom'
import { RouteObject } from 'react-router-dom'
import { useAppStore, useSettingsStore } from '@/store'
import Logo from './Logo'
import routes, { routeMetaMap } from '@/router'
import './index.scss'

interface SidebarProps {
  className?: string
}

const Sidebar: React.FC<SidebarProps> = ({ className }) => {
  const location = useLocation()
  const navigate = useNavigate()
  const { sidebar } = useAppStore()
  const { sidebarLogo } = useSettingsStore()

  const isCollapse = !sidebar.opened

  // 获取图标组件
  const getIconComponent = (iconName?: string) => {
    if (!iconName) return null

    // 可以根据图标名称返回不同的图标
    const iconMap: Record<string, React.ReactNode> = {
      'api': <ApiOutlined style={{ fontSize: '16px' }} />,
      'api2': <ApiOutlined style={{ fontSize: '16px' }} />
    }

    return iconMap[iconName] || <ApiOutlined style={{ fontSize: '16px' }} />
  }

  // 从路由配置生成菜单项
  const generateMenuItems = (routes: RouteObject[]): MenuProps['items'] => {
    return routes
      .filter(route => {
        const path = route.path || ''
        const meta = routeMetaMap[path]
        return !meta?.hidden
      })
      .map(route => {
        const path = route.path || ''
        const meta = routeMetaMap[path]

        if (route.children && route.children.length > 0) {
          // 有子菜单
          const children = route.children
            .filter(child => {
              const childPath = child.path
              if (!childPath) return false
              const fullPath = path === '/' ? `/${childPath}` : `${path}/${childPath}`
              const childMeta = routeMetaMap[fullPath]
              return !childMeta?.hidden
            })
            .map(child => {
              const childPath = child.path || ''
              const fullPath = path === '/' ? `/${childPath}` : `${path}/${childPath}`
              const childMeta = routeMetaMap[fullPath]
              return {
                key: fullPath,
                label: childMeta?.title || childPath,
                icon: getIconComponent(childMeta?.icon)
              }
            })

          if (children.length === 1 && !meta?.alwaysShow) {
            // 只有一个子菜单，不显示父级
            return children[0]
          }

          return {
            key: path,
            label: meta?.title || path,
            icon: getIconComponent(meta?.icon),
            children
          }
        }

        return {
          key: path,
          label: meta?.title || path,
          icon: getIconComponent(meta?.icon)
        }
      })
  }

  const menuItems = generateMenuItems(routes)

  const handleMenuClick: MenuProps['onClick'] = ({ key }) => {
    navigate(key)
  }

  return (
    <div className={`${sidebarLogo ? 'has-logo' : ''} ${className || ''}`}>
      {sidebarLogo && <Logo collapse={isCollapse} />}
      <div className="sidebar-menu-wrapper">
        <Menu
          mode="inline"
          selectedKeys={[location.pathname]}
          onClick={handleMenuClick}
          inlineCollapsed={isCollapse}
          items={menuItems}
          className="sidebar-menu"
          theme="dark"
        />
      </div>
    </div>
  )
}

export default Sidebar

