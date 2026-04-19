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
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/contexts/AuthContext';
import AuthSchemesDrawer from '@/components/AuthSchemesDrawer';
import './index.less';

interface Props {
  knownServices?: Array<{ url: string; name: string }>;
}

const AuthButton: React.FC<Props> = ({ knownServices }) => {
  const { t } = useTranslation();
  const { state } = useAuth();
  const [open, setOpen] = useState(false);

  const boundCount = useMemo(
    () => Object.keys(state.activeBindings).length,
    [state.activeBindings],
  );

  const tooltip = useMemo(() => {
    if (state.items.length === 0) return t('auth.tooltip.empty');
    if (boundCount === 0)
      return t('auth.tooltip.unbound', { count: state.items.length });
    return t('auth.tooltip.bound', {
      schemes: state.items.length,
      bound: boundCount,
    });
  }, [state.items.length, boundCount, t]);

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
            {t('auth.button.label')}
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
