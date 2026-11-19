import { message } from '@/utils/notify';
import { ApiError } from '@/api/client';
import { t, type MsgKey } from '@/i18n';

export function errorText(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.body.message) return e.body.message;
    return t(`err.${e.code}` as MsgKey) || e.message;
  }
  if (e instanceof TypeError) return t('err.network');
  return e instanceof Error ? e.message : String(e);
}

export function showError(e: unknown) {
  if (e instanceof ApiError && (e.code === 'UNAUTHORIZED' || e.code === 'SETUP_REQUIRED')) return; // 已跳转
  message.error(errorText(e));
}
