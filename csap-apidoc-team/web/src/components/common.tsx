import { Tag, Button, Tooltip, Typography, Alert, type ButtonProps } from 'antd';
import type { ReactNode } from 'react';
import type { ChangeStatus, ServiceStatus } from '@/api/types';
import { t, tDyn } from '@/i18n';

const changeColor: Record<ChangeStatus, string> = {
  DRAFT: 'default',
  PENDING: 'processing',
  PUBLISHED: 'success',
  REJECTED: 'error',
  WITHDRAWN: 'default',
  CONFLICT: 'warning',
};
export const ChangeStatusTag = ({ status }: { status: ChangeStatus }) => (
  <Tag color={changeColor[status]} data-testid="change-status">{tDyn('changes.status', status)}</Tag>
);

const svcColor: Record<ServiceStatus, string> = { ONLINE: 'success', OFFLINE: 'default', STALE: 'warning' };
export const ServiceStatusTag = ({ status }: { status: ServiceStatus }) => (
  <Tag color={svcColor[status]}>{tDyn('docs.status', status)}</Tag>
);

/** 无权限时禁用并给出提示 */
export function GuardedButton({ allowed, reason, children, ...rest }: ButtonProps & { allowed: boolean; reason?: string; children: ReactNode }) {
  if (allowed) return <Button {...rest}>{children}</Button>;
  return (
    <Tooltip title={reason || t('common.noPermission')}>
      <span>
        <Button {...rest} disabled>{children}</Button>
      </span>
    </Tooltip>
  );
}

export const PageTitle = ({ title, extra }: { title: ReactNode; extra?: ReactNode }) => (
  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16, gap: 12, flexWrap: 'wrap' }}>
    <Typography.Title level={4} style={{ margin: 0 }}>{title}</Typography.Title>
    <div>{extra}</div>
  </div>
);

export const Forbidden = () => (
  <Alert type="warning" showIcon message={t('forbidden.title')} description={t('forbidden.desc')} />
);
