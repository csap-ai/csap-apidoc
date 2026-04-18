import { useState } from 'react'
import { Form, Input, Button, message } from 'antd'
import {
  UserOutlined,
  LockOutlined,
  ThunderboltOutlined,
  SafetyOutlined,
  RocketOutlined,
  CloudServerOutlined
} from '@ant-design/icons'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useUserStore } from '@/store'
import './index.scss'

interface LoginFormValues {
  username: string
  password: string
}

const Login: React.FC = () => {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [loading, setLoading] = useState(false)
  const { login } = useUserStore()

  const onFinish = async (values: LoginFormValues) => {
    setLoading(true)
    try {
      await login(values)
      const redirect = searchParams.get('redirect') || '/'
      navigate(redirect, { replace: true })
      message.success('登录成功')
    } catch (error: any) {
      message.error('登录失败：' + error.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="login-container">
      {/* 左侧产品介绍区域 */}
      <div className="login-left">
        <div className="left-content">
          <div className="brand-section">
            <div className="brand-logo">
              <img src="/favicon.ico" alt="CSAP Logo" className="logo-image" />
            </div>
            <h1 className="brand-title">CSAP API Devtools</h1>
            <p className="brand-slogan">专业的API管理与开发工具平台</p>
          </div>

          <div className="features-section">
            <div className="feature-item">
              <div className="feature-icon">
                <ThunderboltOutlined />
              </div>
              <div className="feature-content">
                <h3>高效开发</h3>
                <p>快速生成API文档，提升开发效率</p>
              </div>
            </div>

            <div className="feature-item">
              <div className="feature-icon">
                <CloudServerOutlined />
              </div>
              <div className="feature-content">
                <h3>在线调试</h3>
                <p>实时API测试，支持多种请求方式</p>
              </div>
            </div>

            <div className="feature-item">
              <div className="feature-icon">
                <SafetyOutlined />
              </div>
              <div className="feature-content">
                <h3>安全可靠</h3>
                <p>企业级安全保障，数据加密传输</p>
              </div>
            </div>

            <div className="feature-item">
              <div className="feature-icon">
                <RocketOutlined />
              </div>
              <div className="feature-content">
                <h3>持续集成</h3>
                <p>无缝对接CI/CD，自动化部署</p>
              </div>
            </div>
          </div>

          <div className="decoration-shapes">
            <div className="deco-shape deco-1"></div>
            <div className="deco-shape deco-2"></div>
            <div className="deco-shape deco-3"></div>
          </div>
        </div>
      </div>

      {/* 右侧登录表单区域 */}
      <div className="login-right">
        <div className="login-card">
          <div className="card-header">
            <h2 className="login-title">欢迎回来</h2>
            <p className="login-subtitle">登录您的账户以继续</p>
          </div>

          <Form<LoginFormValues>
            className="login-form"
            onFinish={onFinish}
            initialValues={{
              username: 'admin',
              password: '111111'
            }}
          >
            <Form.Item
              name="username"
              rules={[{ required: true, message: '请输入用户名' }]}
            >
              <Input
                prefix={<UserOutlined className="input-icon" />}
                placeholder="请输入用户名"
                size="large"
                className="login-input"
              />
            </Form.Item>

            <Form.Item
              name="password"
              rules={[{ required: true, message: '请输入密码' }]}
            >
              <Input.Password
                prefix={<LockOutlined className="input-icon" />}
                placeholder="请输入密码"
                size="large"
                className="login-input"
              />
            </Form.Item>

            <Form.Item>
              <Button
                type="primary"
                htmlType="submit"
                className="login-button"
                loading={loading}
                size="large"
                block
              >
                {loading ? '登录中...' : '立即登录'}
              </Button>
            </Form.Item>
          </Form>

          <div className="login-footer">
            <p className="tips">默认账号: admin / 111111</p>
          </div>
        </div>
      </div>
    </div>
  )
}

export default Login

