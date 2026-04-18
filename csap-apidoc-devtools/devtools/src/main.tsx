import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import 'normalize.css/normalize.css'
import './styles/index.scss'
import App from './App'

// 数组删除扩展方法（兼容原Vue代码）
declare global {
  interface Array<T> {
    del(n: number): Array<T>
  }
}

Array.prototype.del = function<T>(this: T[], n: number): T[] {
  if (n < 0) {
    return this
  } else {
    return this.slice(0, n).concat(this.slice(n + 1, this.length))
  }
}

// 添加调试信息
console.log('%c🚀 React App Starting...', 'color: green; font-size: 20px; font-weight: bold;')
console.log('Location:', window.location.href)
console.log('User Agent:', navigator.userAgent)

const root = document.getElementById('root')
console.log('Root element:', root)

if (!root) {
  document.body.innerHTML = `
    <div style="padding: 50px; background: #ffebee; color: #c62828; font-family: Arial; border: 3px solid #c62828;">
      <h1>❌ 错误：找不到root元素</h1>
      <p>请联系开发人员</p>
    </div>
  `
  throw new Error('Root element not found')
}

try {
  ReactDOM.createRoot(root).render(
    <React.StrictMode>
      <ConfigProvider locale={zhCN}>
        <BrowserRouter basename="/devtools-ui">
          <App />
        </BrowserRouter>
      </ConfigProvider>
    </React.StrictMode>,
  )
  console.log('%c✅ React rendered successfully', 'color: green; font-size: 16px;')
} catch (error) {
  console.error('%c❌ React render error:', 'color: red; font-size: 16px;', error)
  root.innerHTML = `
    <div style="padding: 50px; background: #ffebee; color: #c62828; font-family: Arial;">
      <h1>❌ React渲染错误</h1>
      <pre>${error}</pre>
      <p>请打开浏览器控制台查看详细错误信息（F12）</p>
    </div>
  `
}

