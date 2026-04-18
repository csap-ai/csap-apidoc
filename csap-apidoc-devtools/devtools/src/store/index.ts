import { create } from 'zustand'
import { getToken, setToken, removeToken } from '@/utils/auth'

interface UserInfo {
  name: string
  avatar: string
  roles: string[]
}

interface LoginParams {
  username: string
  password: string
}

interface UserState extends UserInfo {
  token: string
  setUserInfo: (userInfo: Partial<UserInfo>) => void
  login: (userInfo: LoginParams) => Promise<string>
  getInfo: () => Promise<UserInfo>
  logout: () => Promise<void>
  resetToken: () => void
}

interface SidebarState {
  opened: boolean
  withoutAnimation: boolean
}

interface AppState {
  sidebar: SidebarState
  device: 'desktop' | 'mobile'
  toggleSidebar: () => void
  closeSidebar: (withoutAnimation: boolean) => void
  toggleDevice: (device: 'desktop' | 'mobile') => void
  openSidebar: () => void
}

interface SettingsState {
  title: string
  fixedHeader: boolean
  sidebarLogo: boolean
  changeSetting: (key: keyof SettingsState, value: any) => void
}

// 用户状态管理
export const useUserStore = create<UserState>((set) => ({
  token: getToken() || '',
  name: '',
  avatar: '',
  roles: [],

  setUserInfo: (userInfo: Partial<UserInfo>) => set((state) => ({
    ...state,
    ...userInfo
  })),

  login: async (_userInfo: LoginParams) => {
    // 这里调用登录API
    // const response = await loginAPI({ username: _userInfo.username.trim(), password: _userInfo.password })
    // 模拟登录
    const token = 'admin-token'
    setToken(token)
    set({ token })
    return token
  },

  getInfo: async () => {
    // 这里调用获取用户信息API
    // const response = await getInfoAPI()
    // 模拟用户信息
    const userInfo: UserInfo = {
      name: 'Admin',
      avatar: 'https://wpimg.wallstcn.com/f778738c-e4f8-4870-b634-56703b4acafe.gif',
      roles: ['admin']
    }
    set(userInfo)
    return userInfo
  },

  logout: async () => {
    removeToken()
    set({
      token: '',
      name: '',
      avatar: '',
      roles: []
    })
  },

  resetToken: () => {
    removeToken()
    set({ token: '' })
  }
}))

// 应用设置状态管理
export const useAppStore = create<AppState>((set) => ({
  sidebar: {
    opened: localStorage.getItem('sidebarStatus') === '1', // 默认收起，只有明确设置为'1'才展开
    withoutAnimation: false
  },
  device: 'desktop',

  toggleSidebar: () => set((state) => {
    const opened = !state.sidebar.opened
    localStorage.setItem('sidebarStatus', opened ? '1' : '0')
    return {
      sidebar: {
        ...state.sidebar,
        opened,
        withoutAnimation: false
      }
    }
  }),

  closeSidebar: (withoutAnimation: boolean) => set((state) => ({
    sidebar: {
      ...state.sidebar,
      opened: false,
      withoutAnimation
    }
  })),

  toggleDevice: (device: 'desktop' | 'mobile') => set({ device }),

  openSidebar: () => set((state) => {
    localStorage.setItem('sidebarStatus', '1')
    return {
      sidebar: {
        ...state.sidebar,
        opened: true,
        withoutAnimation: false
      }
    }
  })
}))

// 设置状态管理
export const useSettingsStore = create<SettingsState>((set) => ({
  title: 'CSAP API Devtools',
  fixedHeader: false,
  sidebarLogo: true,

  changeSetting: (key: keyof SettingsState, value: any) => set((state) => ({
    ...state,
    [key]: value
  }))
}))

