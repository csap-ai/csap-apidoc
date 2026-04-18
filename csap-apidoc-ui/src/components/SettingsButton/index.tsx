/**
 * SettingsButton — gear icon in the top bar that opens <SettingsDrawer>.
 *
 * Sits AFTER <AuthButton /> in `layouts/Header/index.tsx`. Behaves the
 * same as AuthButton/HeadersButton (Tooltip + Button) for visual
 * consistency. The drawer itself owns all the M6 settings UI.
 */

import React, { useState } from 'react';
import { Badge, Button, Tooltip } from 'antd';
import { SettingOutlined, LockOutlined } from '@ant-design/icons';
import { useVault } from '@/contexts/VaultContext';
import SettingsDrawer from '@/components/SettingsDrawer';
import './index.less';

const SettingsButton: React.FC = () => {
  const { state } = useVault();
  const [open, setOpen] = useState(false);

  const tooltip =
    state === 'encrypted-locked'
      ? '设置（保险库已锁定）'
      : state === 'encrypted-unlocked'
      ? '设置（加密已启用）'
      : '设置';

  const showLockBadge = state === 'encrypted-locked';

  return (
    <>
      <Tooltip title={tooltip}>
        <Badge
          dot={showLockBadge}
          color="#faad14"
          offset={[-4, 4]}
          className="settings-btn__badge"
        >
          <Button
            icon={showLockBadge ? <LockOutlined /> : <SettingOutlined />}
            onClick={() => setOpen(true)}
            className="settings-btn"
          >
            设置
          </Button>
        </Badge>
      </Tooltip>
      <SettingsDrawer open={open} onClose={() => setOpen(false)} />
    </>
  );
};

export default SettingsButton;
