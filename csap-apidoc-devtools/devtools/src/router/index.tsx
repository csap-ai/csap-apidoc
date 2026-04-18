import { Navigate, RouteObject } from 'react-router-dom'
import { lazy } from 'react'
import Layout from '@/layout'

// 懒加载组件
const Login = lazy(() => import('@/views/login'))
const NotFound = lazy(() => import('@/views/404'))
const ApiManagement = lazy(() => import('@/views/api'))

export interface RouteMeta {
  title?: string
  icon?: string
  hidden?: boolean
  alwaysShow?: boolean
  breadcrumb?: boolean
  roles?: string[]
}

export interface CustomRouteObject {
  path?: string
  index?: boolean
  element?: React.ReactNode
  meta?: RouteMeta
  children?: CustomRouteObject[]
}

/**
 * Note: sub-menu only appear when route children.length >= 1
 *
 * hidden: true                   if set true, item will not show in the sidebar(default is false)
 * alwaysShow: true               if set true, will always show the root menu
 * meta : {
 *   roles: ['admin','editor']    control the page roles (you can set multiple roles)
 *   title: 'title'               the name show in sidebar and breadcrumb (recommend set)
 *   icon: 'svg-name'             the icon show in the sidebar
 *   breadcrumb: false            if set false, the item will hidden in breadcrumb(default is true)
 * }
 */

const routes: RouteObject[] = [
  {
    path: '/login',
    element: <Login />
  },
  {
    path: '/404',
    element: <NotFound />
  },
  {
    path: '/',
    element: <Layout />,
    children: [
      {
        index: true,
        element: <Navigate to="/api" replace />
      },
      {
        path: 'api',
        element: <ApiManagement />
      }
    ]
  },
  {
    path: '*',
    element: <Navigate to="/404" replace />
  }
]

// 路由元信息映射 (用于侧边栏和面包屑)
export const routeMetaMap: Record<string, RouteMeta> = {
  '/login': { hidden: true },
  '/404': { hidden: true },
  '/api': { title: '接口管理', icon: 'api2' },
  '*': { hidden: true }
}

export default routes

