import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import App from '@/App';
import { ThemeProvider } from '@/theme';
import { db, resetDb } from '@/mock/db';

export function loginAs(username: string | null) {
  resetDb(true);
  db.sessionUserId = username ? db.users.find((u) => u.username === username)!.id : null;
}

export function renderApp(route: string) {
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={[route]}>
        <App />
      </MemoryRouter>
    </ThemeProvider>,
  );
}

export const userId = (username: string) => db.users.find((u) => u.username === username)!.id;
export const projectId = (name: string) => db.projects.find((p) => p.name === name)!.id;

/** 往 mock 库里放一张修改单 */
export function addChange(opts: { author: string; status: 'DRAFT' | 'PENDING'; baseVersion?: number; project?: string }) {
  const author = db.users.find((u) => u.username === opts.author)!;
  const pid = projectId(opts.project ?? '订单');
  const key = 'com.demo.order.OrderController.create';
  const before = db.rules['order-service'][key].rules;
  const c = {
    id: ++db.seq, projectId: pid, projectName: opts.project ?? '订单', title: '调整订单金额校验', reason: '测试', kind: 'EDIT' as const, status: opts.status,
    author: { id: author.id, displayName: author.displayName }, createdAt: Date.now(), submittedAt: opts.status === 'PENDING' ? Date.now() : null, decidedBy: null, decidedAt: null,
    decisionNote: null, publishedAt: null,
    items: [{ id: ++db.seq, service: 'order-service', ruleKey: key, baseVersion: opts.baseVersion ?? 1, beforeRules: before, afterRules: JSON.stringify({ amount: { required: true, min: 1 } }) }],
  };
  db.changes.unshift(c);
  return c;
}
