// 权限控制
import { useEffect } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { useUserStore } from '@/store'
import { getPageTitle } from '@/utils/get-page-title'

NProgress.configure({ showSpinner: false })

const whiteList = ['/login'] // 白名单，不需要登录的页面

export const usePermission = (): void => {
  const location = useLocation()
  const navigate = useNavigate()
  const { token, getInfo } = useUserStore()

  useEffect(() => {
    NProgress.start()

    if (token) {
      if (location.pathname === '/login') {
        // 如果已登录，跳转到首页
        navigate('/', { replace: true })
        NProgress.done()
      } else {
        // 获取用户信息
        const hasRoles = useUserStore.getState().roles && useUserStore.getState().roles.length > 0
        if (hasRoles) {
          NProgress.done()
        } else {
          getInfo()
            .then(() => {
              NProgress.done()
            })
            .catch(() => {
              // 移除token并跳转到登录页
              useUserStore.getState().resetToken()
              navigate('/login', { replace: true })
              NProgress.done()
            })
        }
      }
    } else {
      // 没有token
      if (whiteList.indexOf(location.pathname) !== -1) {
        // 在白名单中，直接进入
        NProgress.done()
      } else {
        // 没有访问权限，跳转到登录页
        navigate(`/login?redirect=${location.pathname}`, { replace: true })
        NProgress.done()
      }
    }

    // 设置页面标题
    document.title = getPageTitle(location.pathname)
  }, [location.pathname, token, navigate, getInfo])
}

export default {}

