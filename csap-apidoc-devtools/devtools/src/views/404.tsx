import { useNavigate } from 'react-router-dom'
import { Button } from 'antd'
import cloud from '@/assets/404_images/404_cloud.png'
import notFound from '@/assets/404_images/404.png'
import './404.scss'

const NotFound: React.FC = () => {
  const navigate = useNavigate()

  const handleBackHome = () => {
    navigate('/')
  }

  return (
    <div className="wscn-http404-container">
      <div className="wscn-http404">
        <div className="pic-404">
          <img className="pic-404__parent" src={notFound} alt="404" />
          <img className="pic-404__child left" src={cloud} alt="cloud" />
          <img className="pic-404__child mid" src={cloud} alt="cloud" />
          <img className="pic-404__child right" src={cloud} alt="cloud" />
        </div>
        <div className="bullshit">
          <div className="bullshit__oops">OOPS!</div>
          <div className="bullshit__info">
            版权所有
            <a
              style={{ color: '#20a0ff' }}
              href="https://wallstreetcn.com"
              target="_blank"
              rel="noopener noreferrer"
            >
              华尔街见闻
            </a>
          </div>
          <div className="bullshit__headline">对不起，您正在寻找的页面不存在，请尝试返回首页</div>
          <div className="bullshit__info">
            请检查您输入的网址是否正确，点击以下按钮返回主页
          </div>
          <Button 
            type="primary" 
            size="large" 
            onClick={handleBackHome}
            className="bullshit__return-home"
          >
            返回首页
          </Button>
        </div>
      </div>
    </div>
  )
}

export default NotFound

