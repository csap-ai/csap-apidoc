import { useEffect, useCallback } from 'react'
import { useAppStore } from '@/store'

const WIDTH = 992 // refer to Bootstrap's responsive design

export const useResizeHandler = (): void => {
  const { toggleDevice, closeSidebar } = useAppStore()

  const isMobile = (): boolean => {
    const rect = document.body.getBoundingClientRect()
    return rect.width - 1 < WIDTH
  }

  const resizeHandler = useCallback(() => {
    if (!document.hidden) {
      const mobile = isMobile()
      toggleDevice(mobile ? 'mobile' : 'desktop')

      if (mobile) {
        closeSidebar(true)
      }
    }
  }, [toggleDevice, closeSidebar])

  useEffect(() => {
    const mobile = isMobile()
    if (mobile) {
      toggleDevice('mobile')
      closeSidebar(true)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    window.addEventListener('resize', resizeHandler)
    return () => {
      window.removeEventListener('resize', resizeHandler)
    }
  }, [resizeHandler])
}

