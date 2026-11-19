import { setupWorker } from 'msw/browser';
import { handlers } from './handlers';
import { db, editOutside } from './db';

export async function startMock() {
  // 刷新页面时恢复"已登录"会话(仅 mock)
  try {
    const uid = sessionStorage.getItem('mock-uid');
    if (uid) db.sessionUserId = Number(uid);
  } catch { /* ignore */ }
  const worker = setupWorker(...handlers);
  await worker.start({ onUnhandledRequest: 'bypass', quiet: true });
  // 控制台演示辅助:模拟"服务侧 DevTools 直接改规则",用来制造冲突
  (window as any).__mock = { db, editOutside };
}
