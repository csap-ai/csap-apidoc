import { Space, Tag, Tooltip } from 'antd';
import type { ServiceInfo, ServiceStatus } from '@/api/types';
import { useEnv } from '@/env/EnvContext';
import { t, tDyn } from '@/i18n';

const color: Record<ServiceStatus, string> = { ONLINE: 'success', OFFLINE: 'default', STALE: 'warning' };

/** 多环境时,在一行里列出服务在各环境的状态(没出现过的环境不列) */
export function ServiceEnvTags({ service }: { service: ServiceInfo }) {
  const { nameOf } = useEnv();
  const envs = service.envs ?? [];
  if (!envs.length) return <span style={{ opacity: 0.5 }}>-</span>;
  return (
    <Space size={[4, 4]} wrap data-testid={`svc-envs-${service.service}`}>
      {envs.map((e) => (
        <Tooltip key={e.env} title={`${e.baseUrl ?? t('env.noRecord')} · ${t('docs.instances')} ${e.instances}`}>
          <Tag color={color[e.status]} style={{ marginInlineEnd: 0 }}>{nameOf(e.env)} · {tDyn('docs.status', e.status)}</Tag>
        </Tooltip>
      ))}
    </Space>
  );
}
