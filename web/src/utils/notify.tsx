import { App, message as staticMessage, Modal as StaticModal } from 'antd';
import type { MessageInstance } from 'antd/es/message/interface';
import type { HookAPI } from 'antd/es/modal/useModal';

// 让 message / modal 能吃到主题(暗色)上下文:由 <NotifyHolder/> 在 antd <App> 内注入实例。
export const message: MessageInstance = new Proxy({} as MessageInstance, {
  get: (_t, k) => (holder.message as any)[k],
});
export const modal: Pick<HookAPI, 'warning' | 'error' | 'info' | 'success' | 'confirm'> = new Proxy({} as any, {
  get: (_t, k) => (holder.modal as any)[k],
});
const holder: { message: MessageInstance; modal: HookAPI | typeof StaticModal } = { message: staticMessage, modal: StaticModal };

export function NotifyHolder() {
  const api = App.useApp();
  holder.message = api.message;
  holder.modal = api.modal;
  return null;
}
