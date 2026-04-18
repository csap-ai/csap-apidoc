/**
 * AuthButton — top-bar entry to the auth-schemes drawer.
 *
 * Badge counts how many schemes are currently bound to a service. Once M5
 * threads the active service into the layout, the badge will show whether
 * the *current* service has an active binding (a single dot indicator).
 * For now we surface the total binding count + total scheme count so the
 * user can see at a glance whether anything is configured.
 */

import React, { useMemo, useState } from 'react';
import { Badge, Button, Tooltip } from 'antd';
import { SafetyCertificateOutlined } from '@ant-design/icons';
import { useAuth } from '@/contexts/AuthContext';
import AuthSchemesDrawer from '@/components/AuthSchemesDrawer';
import './index.less';

interface Props {
  knownServices?: Array<{ url: string; name: string }>;
}

const AuthButton: React.FC<Props> = ({ knownServices }) => {
  const { state } = useAuth();
  const [open, setOpen] = useState(false);

  const boundCount = useMemo(
    () => Object.keys(state.activeBindings).length,
    [state.activeBindings],
  );

  const tooltip = useMemo(() => {
    if (state.items.length === 0) return '认证方案：未配置';
    if (boundCount === 0)
      return `认证方案：已配置 ${state.items.length} 个，尚未绑定到任何服务`;
    return `认证方案：${state.items.length} 个方案，${boundCount} 个服务已绑定`;
  }, [state.items.length, boundCount]);

  return (
    <>
      <Tooltip title={tooltip}>
        <Badge
          count={boundCount}
          size="small"
          offset={[-4, 4]}
          className="auth-btn__badge"
        >
          <Button
            icon={<SafetyCertificateOutlined />}
            onClick={() => setOpen(true)}
            className="auth-btn"
          >
            认证
          </Button>
        </Badge>
      </Tooltip>
      <AuthSchemesDrawer
        open={open}
        onClose={() => setOpen(false)}
        knownServices={knownServices}
      />
    </>
  );
};

export default AuthButton;
