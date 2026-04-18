import { Breadcrumb as AntBreadcrumb } from 'antd'
import { Link, useLocation } from 'react-router-dom'
import { useEffect, useState, useCallback } from 'react'
import { RouteObject } from 'react-router-dom'
import routes, { routeMetaMap } from '@/router'
import './index.scss'

interface BreadcrumbProps {
  className?: string
}

interface BreadcrumbItem {
  path: string
  title: string
  breadcrumb: boolean
}

const Breadcrumb: React.FC<BreadcrumbProps> = ({ className }) => {
  const location = useLocation()
  const [breadcrumbList, setBreadcrumbList] = useState<BreadcrumbItem[]>([])

  const findRouteByPath = (routes: RouteObject[], path: string) => {
    for (const route of routes) {
      if (route.path === path) {
        return routeMetaMap[path]
      }
      if (route.children) {
        for (const child of route.children) {
          const childPath = child.path || ''
          const fullPath = route.path === '/' ? `/${childPath}` : `${route.path}/${childPath}`
          if (fullPath === path) {
            return routeMetaMap[fullPath]
          }
        }
      }
    }
    return null
  }

  const getBreadcrumb = useCallback(() => {
    // 根据当前路径生成面包屑
    const pathSnippets = location.pathname.split('/').filter(i => i)
    
    const breadcrumbItems: BreadcrumbItem[] = pathSnippets.map((_, index) => {
      const url = `/${pathSnippets.slice(0, index + 1).join('/')}`
      
      // 从路由配置中查找匹配的路由
      const route = findRouteByPath(routes, url)
      
      return {
        path: url,
        title: route?.title || url,
        breadcrumb: route?.breadcrumb !== false
      }
    }).filter(item => item.breadcrumb)

    setBreadcrumbList(breadcrumbItems)
  }, [location.pathname])

  useEffect(() => {
    getBreadcrumb()
  }, [getBreadcrumb])

  const items = breadcrumbList.map((item, index) => {
    const isLast = index === breadcrumbList.length - 1
    return {
      title: isLast ? (
        <span className="no-redirect">{item.title}</span>
      ) : (
        <Link to={item.path}>{item.title}</Link>
      )
    }
  })

  return (
    <AntBreadcrumb 
      className={`app-breadcrumb ${className || ''}`}
      separator="/"
      items={items}
    />
  )
}

export default Breadcrumb

