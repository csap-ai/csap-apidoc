import { setupWorker } from 'msw/browser';
import { handlers } from './handlers';
import { db, editOutside, setEnvStatus, setScenario, tamperAudit, type Scenario } from './db';

/** 演示场景:默认 multi(开发 / 测试 / 生产);URL 带 ?scenario=single 切到"只有一个环境",之后记在 sessionStorage */
function pickScenario(): Scenario {
  try {
    const q = new URLSearchParams(window.location.search).get('scenario');
    if (q === 'single' || q === 'multi') sessionStorage.setItem('mock-scenario', q);
    const s = sessionStorage.getItem('mock-scenario');
    if (s === 'single' || s === 'multi') return s;
  } catch { /* ignore */ }
  return 'multi';
}

export async function startMock() {
  setScenario(pickScenario());
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
  // 控制台演示辅助:
  //   __mock.editOutside(service, key, rulesJson)  模拟"服务侧 DevTools 直接改规则",用来制造冲突
  //   __mock.setEnvStatus('test', 'DOWN')          让某个环境不可用(再 'UP' 恢复)
  //   __mock.tamperAudit(id)                        改一条审计记录的内容,之后「校验哈希链」会报异常
  (window as any).__mock = { db, editOutside, setEnvStatus, tamperAudit };
}
