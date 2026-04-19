/**
 * SettingsDrawer — global preferences UI.
 *
 * Three sections:
 *   1. 「Vault 加密」  — toggle, change/lock password, idle timeout slider
 *   2. 「试运行」      — proxy URL + timeout (M5 reads these)
 *   3. 「数据」        — export / import / reset all
 *
 * Vault toggle UX:
 *   - 启用加密 → opens MasterPasswordModal in 'set' mode
 *   - 关闭加密 → Modal.confirm warning → MasterPasswordModal in 'change'?
 *     We re-use the unlock flow inside disableEncryption(pwd) instead, so
 *     the modal shows up as 'unlock' to confirm the user knows the
 *     password before we throw away the encryption.
 *   - 修改主密码 → MasterPasswordModal 'change'
 *   - 立即锁定 → just calls vault.lock()
 *
 * Reset wipes EVERY csap-apidoc:* localStorage key and reloads.
 */

import React, { useEffect, useRef, useState } from 'react';
import {
  Drawer,
  Form,
  Switch,
  Slider,
  Input,
  InputNumber,
  Button,
  Space,
  Modal,
  message,
  Tag,
  Typography,
} from 'antd';
import {
  LockOutlined,
  UnlockOutlined,
  KeyOutlined,
  DownloadOutlined,
  UploadOutlined,
  DeleteOutlined,
  ExclamationCircleOutlined,
} from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { useVault } from '@/contexts/VaultContext';
import { settingsStore } from '@/stores/settingsStore';
import MasterPasswordModal, {
  MasterPasswordModalMode,
} from '@/components/MasterPasswordModal';
import './index.less';

const { Text } = Typography;

interface Props {
  open: boolean;
  onClose: () => void;
}

const SettingsDrawer: React.FC<Props> = ({ open, onClose }) => {
  const { t } = useTranslation();
  const {
    state,
    hasEncryptedData,
    lock,
    disableEncryption,
    exportConfig,
    importConfig,
    resetAll,
  } = useVault();

  const [settings, setSettings] = useState(() => settingsStore.getState());
  const [pwdModalMode, setPwdModalMode] =
    useState<MasterPasswordModalMode | null>(null);
  const [disableConfirmOpen, setDisableConfirmOpen] = useState(false);
  const [disablePwd, setDisablePwd] = useState('');
  const [disableSubmitting, setDisableSubmitting] = useState(false);
  const [disableErr, setDisableErr] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => settingsStore.subscribe(setSettings), []);

  const isEncrypted = state !== 'plaintext';
  const isUnlocked = state === 'encrypted-unlocked';

  /* -------- vault: enable / disable / change pwd / lock -------- */

  const onToggleEncryption = (next: boolean): void => {
    if (next) {
      // plaintext → encrypted
      setPwdModalMode('set');
    } else {
      // encrypted → plaintext (needs password to confirm)
      setDisableConfirmOpen(true);
    }
  };

  const handleDisableConfirm = async (): Promise<void> => {
    setDisableErr(null);
    setDisableSubmitting(true);
    try {
      await disableEncryption(disablePwd);
      message.success(t('settings.vault.disabledToast'));
      setDisableConfirmOpen(false);
      setDisablePwd('');
    } catch (err) {
      const msg = err instanceof Error ? err.message : t('settings.vault.disable.failed');
      setDisableErr(msg.includes('wrong') ? t('settings.vault.disable.wrongPwd') : msg);
    } finally {
      setDisableSubmitting(false);
    }
  };

  const handleLockNow = (): void => {
    lock();
    message.success(t('settings.vault.lockedToast'));
  };

  /* -------- settings field updates -------- */

  const setLockTimeout = (mins: number): void => {
    settingsStore.update({ vaultLockTimeoutMin: mins });
  };

  const setProxyUrl = (url: string): void => {
    settingsStore.update({ tryItOutProxyUrl: url.trim() ? url.trim() : null });
  };

  const setTimeoutMs = (ms: number | null): void => {
    if (typeof ms !== 'number' || ms <= 0) return;
    settingsStore.update({ tryItOutTimeoutMs: ms });
  };

  const setWithCredentials = (v: boolean): void => {
    settingsStore.update({ tryItOutWithCredentials: v });
  };

  /* -------- data: export / import / reset -------- */

  const handleExport = (): void => {
    try {
      const json = exportConfig();
      const blob = new Blob([json], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      const stamp = new Date().toISOString().replace(/[:.]/g, '-');
      a.href = url;
      a.download = `csap-apidoc-config-${stamp}.json`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
      message.success(t('settings.data.exportSuccess'));
    } catch (err) {
      console.error(err);
      message.error(t('settings.data.exportFailed'));
    }
  };

  const handleImportClick = (): void => {
    fileInputRef.current?.click();
  };

  const handleImportFile = (e: React.ChangeEvent<HTMLInputElement>): void => {
    const file = e.target.files?.[0];
    e.target.value = ''; // allow re-selecting the same file
    if (!file) return;
    Modal.confirm({
      title: t('settings.data.import.confirmTitle'),
      icon: <ExclamationCircleOutlined />,
      content: t('settings.data.import.confirmContent'),
      okText: t('settings.data.import.ok'),
      cancelText: t('common.cancel'),
      onOk: async () => {
        try {
          const text = await file.text();
          importConfig(text);
        } catch (err) {
          const msg = err instanceof Error ? err.message : t('settings.data.import.failed');
          message.error(msg);
        }
      },
    });
  };

  const handleResetAll = (): void => {
    Modal.confirm({
      title: t('settings.data.reset.confirmTitle'),
      icon: <ExclamationCircleOutlined />,
      content: t('settings.data.reset.confirmContent'),
      okText: t('settings.data.reset.ok'),
      okType: 'danger',
      cancelText: t('common.cancel'),
      onOk: () => {
        try {
          resetAll();
        } catch (err) {
          console.error(err);
          message.error(t('settings.data.reset.failed'));
        }
      },
    });
  };

  /* -------- render -------- */

  return (
    <Drawer
      title={t('settings.drawer.title')}
      width={520}
      open={open}
      onClose={onClose}
      destroyOnClose
      className="settings-drawer"
    >
      {/* ---------------- Vault ---------------- */}
      <section className="settings-drawer__section">
        <header className="settings-drawer__section-header">
          <span className="settings-drawer__section-title">{t('settings.section.vault')}</span>
          {isEncrypted ? (
            isUnlocked ? (
              <Tag color="green" icon={<UnlockOutlined />}>
                {t('settings.vault.tag.unlocked')}
              </Tag>
            ) : (
              <Tag color="gold" icon={<LockOutlined />}>
                {t('settings.vault.tag.locked')}
              </Tag>
            )
          ) : (
            <Tag>{t('settings.vault.tag.plaintext')}</Tag>
          )}
        </header>
        <Form layout="vertical" requiredMark={false}>
          <Form.Item label={t('settings.vault.enable.label')}>
            <Switch
              checked={isEncrypted}
              onChange={onToggleEncryption}
              checkedChildren={t('common.switch.on')}
              unCheckedChildren={t('common.switch.off')}
            />
            <Text type="secondary" className="settings-drawer__hint">
              {isEncrypted
                ? t('settings.vault.enable.help.on')
                : t('settings.vault.enable.help.off')}
            </Text>
          </Form.Item>

          {isEncrypted && (
            <Form.Item label={t('settings.vault.masterPwd')}>
              <Space wrap>
                <Button
                  icon={<KeyOutlined />}
                  onClick={() => setPwdModalMode('change')}
                  disabled={!isUnlocked}
                >
                  {t('settings.vault.changePwd')}
                </Button>
                <Button
                  icon={<LockOutlined />}
                  onClick={handleLockNow}
                  disabled={!isUnlocked}
                >
                  {t('settings.vault.lockNow')}
                </Button>
              </Space>
              {!isUnlocked && (
                <Text type="secondary" className="settings-drawer__hint">
                  {t('settings.vault.lockedHint')}
                </Text>
              )}
            </Form.Item>
          )}

          <Form.Item
            label={
              <span>
                {t('settings.vault.idleLock.label')}
                <strong>
                  {settings.vaultLockTimeoutMin === 0
                    ? t('settings.vault.idleLock.off')
                    : t('settings.vault.idleLock.minutes', { count: settings.vaultLockTimeoutMin })}
                </strong>
              </span>
            }
          >
            <Slider
              min={0}
              max={120}
              step={5}
              value={settings.vaultLockTimeoutMin}
              onChange={setLockTimeout}
              marks={{ 0: t('settings.vault.idleLock.mark.off'), 30: '30', 60: '60', 120: '120' }}
              disabled={!isEncrypted}
            />
          </Form.Item>
        </Form>
      </section>

      {/* ---------------- Try it out ---------------- */}
      <section className="settings-drawer__section">
        <header className="settings-drawer__section-header">
          <span className="settings-drawer__section-title">{t('settings.section.tryout')}</span>
        </header>
        <Form layout="vertical" requiredMark={false}>
          <Form.Item
            label={t('settings.tryout.proxy.label')}
            help={t('settings.tryout.proxy.help')}
          >
            <Input
              value={settings.tryItOutProxyUrl ?? ''}
              onChange={(e) => setProxyUrl(e.target.value)}
              placeholder={t('settings.tryout.proxy.placeholder')}
              allowClear
            />
          </Form.Item>
          <Form.Item label={t('settings.tryout.timeout.label')}>
            <InputNumber
              min={1000}
              max={300000}
              step={1000}
              value={settings.tryItOutTimeoutMs}
              onChange={setTimeoutMs}
              style={{ width: 200 }}
            />
          </Form.Item>
          <Form.Item label={t('settings.tryout.withCredentials.label')}>
            <Switch
              checked={settings.tryItOutWithCredentials}
              onChange={setWithCredentials}
              checkedChildren={t('common.switch.on')}
              unCheckedChildren={t('common.switch.off')}
            />
            <Text type="secondary" className="settings-drawer__hint">
              {t('settings.tryout.withCredentials.help')}
            </Text>
          </Form.Item>
        </Form>
      </section>

      {/* ---------------- Data ---------------- */}
      <section className="settings-drawer__section">
        <header className="settings-drawer__section-header">
          <span className="settings-drawer__section-title">{t('settings.section.data')}</span>
          {hasEncryptedData && state === 'encrypted-locked' && (
            <Tag color="gold">{t('settings.data.lockedExportNote')}</Tag>
          )}
        </header>
        <Space direction="vertical" style={{ width: '100%' }}>
          <Button
            block
            icon={<DownloadOutlined />}
            onClick={handleExport}
          >
            {t('settings.data.export')}
          </Button>
          <Button
            block
            icon={<UploadOutlined />}
            onClick={handleImportClick}
          >
            {t('settings.data.import')}
          </Button>
          <input
            ref={fileInputRef}
            type="file"
            accept="application/json,.json"
            onChange={handleImportFile}
            style={{ display: 'none' }}
          />
          <Button
            block
            danger
            icon={<DeleteOutlined />}
            onClick={handleResetAll}
          >
            {t('settings.data.reset')}
          </Button>
        </Space>
      </section>

      {/* ---------------- Modals ---------------- */}
      <MasterPasswordModal
        open={pwdModalMode !== null}
        mode={pwdModalMode ?? 'set'}
        onClose={() => setPwdModalMode(null)}
        onSuccess={() => {
          if (pwdModalMode === 'set') message.success(t('settings.vault.enabledToast'));
          if (pwdModalMode === 'change') message.success(t('settings.vault.pwdChangedToast'));
        }}
      />

      <Modal
        open={disableConfirmOpen}
        title={t('settings.vault.disable.confirmTitle')}
        okText={t('settings.vault.disable.confirmOk')}
        cancelText={t('common.cancel')}
        okButtonProps={{ danger: true, loading: disableSubmitting }}
        onCancel={() => {
          setDisableConfirmOpen(false);
          setDisablePwd('');
          setDisableErr(null);
        }}
        onOk={handleDisableConfirm}
        destroyOnClose
        maskClosable={false}
      >
        <p>
          {t('settings.vault.disable.confirmContent.before')}
          <strong>{t('settings.vault.disable.confirmContent.strong')}</strong>
          {t('settings.vault.disable.confirmContent.after')}
        </p>
        <Input.Password
          value={disablePwd}
          onChange={(e) => setDisablePwd(e.target.value)}
          placeholder={t('settings.vault.disable.pwdPlaceholder')}
          autoFocus
          onPressEnter={handleDisableConfirm}
        />
        {disableErr && (
          <Text type="danger" className="settings-drawer__hint">
            {disableErr}
          </Text>
        )}
      </Modal>
    </Drawer>
  );
};

export default SettingsDrawer;
