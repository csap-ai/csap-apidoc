import { Link } from 'react-router-dom'
import { useSettingsStore } from '@/store'
import './Logo.scss'

interface LogoProps {
  collapse: boolean
}

const Logo: React.FC<LogoProps> = ({ collapse }) => {
  const { title } = useSettingsStore()

  return (
    <div className={`sidebar-logo-container ${collapse ? 'collapse' : ''}`}>
      <Link to="/" className="sidebar-logo-link">
        <img src="/favicon.ico" className="sidebar-logo" alt="CSAP logo" />
        {!collapse && <h1 className="sidebar-title">{title}</h1>}
      </Link>
    </div>
  )
}

export default Logo

