import { Dropdown, MenuProps } from 'antd'
import { DownOutlined, HomeOutlined, LogoutOutlined, GithubOutlined } from '@ant-design/icons'
import { useNavigate, useLocation } from 'react-router-dom'
import { useAppStore, useUserStore, useSettingsStore } from '@/store'
import Breadcrumb from '@/components/Breadcrumb'
import Hamburger from '@/components/Hamburger'
import './Navbar.scss'

const Navbar: React.FC = () => {
  const navigate = useNavigate()
  const location = useLocation()
  const { sidebar, toggleSidebar } = useAppStore()
  const { avatar, logout } = useUserStore()
  const { title } = useSettingsStore()

  const handleLogout = async () => {
    await logout()
    navigate(`/login?redirect=${location.pathname}`)
  }

  const menuItems: MenuProps['items'] = [
    {
      key: 'home',
      label: 'Home',
      icon: <HomeOutlined />,
      onClick: () => navigate('/')
    },
    {
      key: 'github',
      label: (
        <a href="https://github.com/PanJiaChen/vue-admin-template/" target="_blank" rel="noopener noreferrer">
          Github
        </a>
      ),
      icon: <GithubOutlined />
    },
    {
      type: 'divider'
    },
    {
      key: 'logout',
      label: '退出登录',
      icon: <LogoutOutlined />,
      onClick: handleLogout
    }
  ]

  return (
    <div className="navbar">
      <div className="navbar-left">
        <Hamburger
          isActive={sidebar.opened}
          className="hamburger-container"
          onToggleClick={toggleSidebar}
        />

        <div className="logo-container" onClick={() => navigate('/')}>
          <img src="/favicon.ico" alt="CSAP Logo" className="navbar-logo" />
          <span className="navbar-title">{title}</span>
        </div>
      </div>

      <Breadcrumb className="breadcrumb-container" />

      <div className="right-menu">
        <Dropdown menu={{ items: menuItems }} trigger={['click']}>
          <div className="avatar-container">
            <div className="avatar-wrapper">
              <img
                src={avatar ? `${avatar}?imageView2/1/w/80/h/80` : 'https://wpimg.wallstcn.com/f778738c-e4f8-4870-b634-56703b4acafe.gif'}
                className="user-avatar"
                alt="avatar"
                style={{ width: 40, height: 40, borderRadius: 10 }}
              />
              <DownOutlined className="el-icon-caret-bottom" />
            </div>
          </div>
        </Dropdown>
      </div>
    </div>
  )
}

export default Navbar

