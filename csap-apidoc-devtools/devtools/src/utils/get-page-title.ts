import { useSettingsStore } from '@/store'

export function getPageTitle(pageTitle?: string): string {
  const title = useSettingsStore.getState().title
  if (pageTitle) {
    return `${pageTitle} - ${title}`
  }
  return title
}

export default getPageTitle

