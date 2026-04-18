/**
 * HeadersButton — top-bar button that opens the global-headers drawer.
 *
 * Badge shows the number of enabled rules that *would* apply in the
 * current context (global + current-env). Service-scope rules are
 * excluded from the badge count in M2 because the active service is not
 * yet threaded into the provider; M5 will refine this.
 */

import React, { useMemo, useState } from 'react';
import { Badge, Button, Tooltip } from 'antd';
import { ControlOutlined } from '@ant-design/icons';
import { useHeaders } from '@/contexts/HeadersContext';
import { useActiveEnvironment } from '@/contexts/EnvironmentContext';
import HeadersManagerDrawer from '@/components/HeadersManagerDrawer';
import './index.less';

interface Props {
  knownServices?: Array<{ url: string; name: string }>;
}

const HeadersButton: React.FC<Props> = ({ knownServices }) => {
  const { state } = useHeaders();
  const env = useActiveEnvironment();
  const [open, setOpen] = useState(false);

  const activeCount = useMemo(() => {
    return state.items.filter((r) => {
      if (!r.enabled) return false;
      if (r.scope === 'global') return true;
      if (r.scope === 'environment') return env?.id === r.scopeRefId;
      return false;
    }).length;
  }, [state.items, env?.id]);

  return (
    <>
      <Tooltip title="全局请求头">
        <Badge
          count={activeCount}
          size="small"
          offset={[-4, 4]}
          className="headers-btn__badge"
        >
          <Button
            icon={<ControlOutlined />}
            onClick={() => setOpen(true)}
            className="headers-btn"
          >
            请求头
          </Button>
        </Badge>
      </Tooltip>
      <HeadersManagerDrawer
        open={open}
        onClose={() => setOpen(false)}
        knownServices={knownServices}
      />
    </>
  );
};

export default HeadersButton;
