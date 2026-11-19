import { setupWorker } from 'msw/browser';
import { handlers } from './handlers';
import { db, editOutside } from './db';

export async function startMock() {
  // 刷新页面时恢复"已登录"会话(仅 mock)
  try {
    const uid = sessionStorage.getItem('mock-uid');
    if (uid) db.sessionUserId = Number(uid);
  } catch { /* ignore */ }
  // 刷新页面不丢数据:mock 库存在 sessionStorage 里(关标签页即重置,可重新走初始化向导)
  try {
    const saved = sessionStorage.getItem('mock-db');
    if (saved) Object.assign(db, JSON.parse(saved));
  } catch { /* ignore */ }
  const worker = setupWorker(...handlers);
  worker.events.on('response:mocked', () => {
    try { sessionStorage.setItem('mock-db', JSON.stringify(db)); } catch { /* ignore */ }
  });
  await worker.start({ onUnhandledRequest: 'bypass', quiet: true });
  // 控制台演示辅助:模拟"服务侧 DevTools 直接改规则",用来制造冲突
  (window as any).__mock = { db, editOutside };
}
