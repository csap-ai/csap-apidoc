import { Alert, Select, Tag } from 'antd';
import type { Env } from '@/api/types';
import { useEnv } from '@/env/EnvContext';
import { t } from '@/i18n';

/** 环境标签:受保护环境(例如生产)用醒目的红色,DOWN 的显示"不可用" */
export function EnvTag({ env, showState = true }: { env: Pick<Env, 'name' | 'protected' | 'readOnly' | 'status'> | undefined; showState?: boolean }) {
  if (!env) return null;
  return (
    <span data-testid="env-tag" style={{ whiteSpace: 'nowrap' }}>
      <Tag color={env.protected ? 'red' : 'blue'} style={{ marginInlineEnd: 4 }}>{env.name}</Tag>
      {showState && env.protected && <Tag color="red" bordered={false}>{t('env.protected')}</Tag>}
      {showState && env.readOnly && <Tag bordered={false}>{t('env.readOnly')}</Tag>}
      {showState && env.status === 'DOWN' && <Tag color="error">{t('env.down')}</Tag>}
    </span>
  );
}

/** 按 key 显示环境标签;找不到环境时退回纯文本 */
export function EnvTagByKey({ envKey, name, showState = false }: { envKey: string | null | undefined; name?: string; showState?: boolean }) {
  const { find } = useEnv();
  if (!envKey) return <>{t('audit.envNone')}</>;
  const e = find(envKey);
  return e ? <EnvTag env={e} showState={showState} /> : <Tag>{name ?? envKey}</Tag>;
}

/** 顶栏环境切换器:只在配置了 2 个以上环境时显示 */
export default function EnvSwitcher() {
  const { envs, env, setEnv, multi } = useEnv();
  if (!multi) return null;
  return (
    <Select
      data-testid="env-switcher"
      aria-label={t('env.switch')}
      style={{ minWidth: 190 }}
      value={env}
      onChange={setEnv}
      optionLabelProp="label"
      options={envs.map((e) => ({
        value: e.key,
        label: <EnvTag env={e} />,
        title: e.name,
      }))}
      optionRender={(o) => <EnvTag env={envs.find((e) => e.key === o.value)} />}
    />
  );
}

/** 当前环境不可用 / 只读时在页面顶部给出说明 */
export function EnvBanner() {
  const { current, multi } = useEnv();
  if (!multi || !current) return null;
  return (
    <>
      {current.status === 'DOWN' && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
          data-testid="env-down-banner"
          message={t('env.downBanner', { name: current.name, error: current.error || t('env.downBannerNoReason') })}
        />
      )}
      {current.readOnly && (
        <Alert type="info" showIcon style={{ marginBottom: 16 }} data-testid="env-readonly-banner" message={t('env.readOnlyBanner', { name: current.name })} />
      )}
    </>
  );
}
