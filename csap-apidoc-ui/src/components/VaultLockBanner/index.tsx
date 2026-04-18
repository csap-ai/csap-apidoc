/**
 * VaultLockBanner — slim banner that appears at the top of the layout
 * whenever the vault is in 'encrypted-locked' state.
 *
 * Renders nothing in plaintext or encrypted-unlocked states. Listens to
 * `csap-apidoc:vault-locked-access` events so it can briefly highlight
 * itself when something tried to read a secret while locked (a hint for
 * the user that the page is currently degraded).
 *
 * Mounted by `layouts/index.tsx` immediately under the top bar so the
 * banner sits in the natural visual flow rather than overlaying the page
 * header. The actual unlock UX lives in <MasterPasswordModal
 * mode="unlock">, opened by the "解锁" button here.
 */

import React, { useEffect, useState } from 'react';
import { Button } from 'antd';
import { LockOutlined } from '@ant-design/icons';
import { useVault } from '@/contexts/VaultContext';
import { VAULT_LOCKED_ACCESS_EVENT } from '@/stores/vault';
import MasterPasswordModal from '@/components/MasterPasswordModal';
import './index.less';

const FLASH_MS = 1500;

const VaultLockBanner: React.FC = () => {
  const { state } = useVault();
  const [open, setOpen] = useState(false);
  const [flash, setFlash] = useState(false);

  useEffect(() => {
    if (state !== 'encrypted-locked') return undefined;
    const onLockedAccess = (): void => {
      setFlash(true);
      window.setTimeout(() => setFlash(false), FLASH_MS);
    };
    window.addEventListener(VAULT_LOCKED_ACCESS_EVENT, onLockedAccess);
    return () => {
      window.removeEventListener(VAULT_LOCKED_ACCESS_EVENT, onLockedAccess);
    };
  }, [state]);

  if (state !== 'encrypted-locked') return null;

  return (
    <>
      <div
        className={`vault-lock-banner${flash ? ' vault-lock-banner--flash' : ''}`}
        role="status"
      >
        <span className="vault-lock-banner__icon">
          <LockOutlined />
        </span>
        <span className="vault-lock-banner__text">
          保险库已锁定 — 已保存的凭证当前不可读取，请输入主密码以恢复 Try-it-out 能力。
        </span>
        <Button
          type="primary"
          size="small"
          onClick={() => setOpen(true)}
          className="vault-lock-banner__btn"
        >
          解锁
        </Button>
      </div>
      <MasterPasswordModal
        open={open}
        mode="unlock"
        onClose={() => setOpen(false)}
      />
    </>
  );
};

export default VaultLockBanner;
