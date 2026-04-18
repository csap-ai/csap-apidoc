import axios, { AxiosInstance, AxiosResponse } from 'axios'
import { message } from 'antd'
import { getToken } from '@/utils/auth'
import { useUserStore } from '@/store'

// 创建axios实例
// 通过环境变量配置 baseURL
// 开发环境(.env.development): /api - 使用 Vite 代理
// 生产环境(.env.production): '' - 直接请求后端接口
const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: Number(import.meta.env.VITE_API_TIMEOUT) || 5000
})

// 请求拦截器
service.interceptors.request.use(
  (config) => {
    // 在发送请求之前做些什么
    const token = getToken()
    if (token && config.headers) {
      // 让每个请求携带token
      config.headers['X-Token'] = token
    }
    return config
  },
  (error) => {
    // 处理请求错误
    console.log(error) // for debug
    return Promise.reject(error)
  }
)

// 响应拦截器
service.interceptors.response.use(
  (response: AxiosResponse<any>) => {
    const res = response.data

    // 后端返回格式: { code: "0", data: {...}, message: "成功", success: true }
    // code为"0"或success为true表示成功
    const isSuccess = res.code === '0' || res.code === 0 || res.success === true

    if (!isSuccess) {
      message.error(res.message || 'Error')

      // 50008: 非法token; 50012: 其他客户端登录; 50014: Token过期;
      if (res.code === 50008 || res.code === 50012 || res.code === 50014) {
        // 重新登录
        message.warning('您已登出，您可以取消以停留在此页面，或重新登录')
        useUserStore.getState().resetToken()
        // 跳转到登录页
        window.location.href = '/login'
      }
      return Promise.reject(new Error(res.message || 'Error'))
    } else {
      return response
    }
  },
  (error) => {
    console.log('err' + error) // for debug
    message.error(error.message)
    return Promise.reject(error)
  }
)

export default service

