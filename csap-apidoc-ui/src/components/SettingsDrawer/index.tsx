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
      message.success('已关闭加密，凭证已转回明文存储');
      setDisableConfirmOpen(false);
      setDisablePwd('');
    } catch (err) {
      const msg = err instanceof Error ? err.message : '关闭加密失败';
      setDisableErr(msg.includes('wrong') ? '密码错误，请重试' : msg);
    } finally {
      setDisableSubmitting(false);
    }
  };

  const handleLockNow = (): void => {
    lock();
    message.success('保险库已锁定');
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
      message.success('配置已导出');
    } catch (err) {
      console.error(err);
      message.error('导出失败');
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
      title: '导入配置会覆盖当前所有设置',
      icon: <ExclamationCircleOutlined />,
      content:
        '现有的环境、请求头、认证方案、保险库与设置都会被替换；加密会被重置为明文模式。建议先导出当前配置作为备份。',
      okText: '继续导入',
      cancelText: '取消',
      onOk: async () => {
        try {
          const text = await file.text();
          importConfig(text);
        } catch (err) {
          const msg = err instanceof Error ? err.message : '导入失败';
          message.error(msg);
        }
      },
    });
  };

  const handleResetAll = (): void => {
    Modal.confirm({
      title: '重置全部',
      icon: <ExclamationCircleOutlined />,
      content:
        '会清空浏览器中所有 CSAP Apidoc 数据：环境、请求头、认证方案、保险库与设置。此操作不可撤销。',
      okText: '重置',
      okType: 'danger',
      cancelText: '取消',
      onOk: () => {
        try {
          resetAll();
        } catch (err) {
          console.error(err);
          message.error('重置失败');
        }
      },
    });
  };

  /* -------- render -------- */

  return (
    <Drawer
      title="设置"
      width={520}
      open={open}
      onClose={onClose}
      destroyOnClose
      className="settings-drawer"
    >
      {/* ---------------- Vault ---------------- */}
      <section className="settings-drawer__section">
        <header className="settings-drawer__section-header">
          <span className="settings-drawer__section-title">Vault 加密</span>
          {isEncrypted ? (
            isUnlocked ? (
              <Tag color="green" icon={<UnlockOutlined />}>
                已解锁
              </Tag>
            ) : (
              <Tag color="gold" icon={<LockOutlined />}>
                已锁定
              </Tag>
            )
          ) : (
            <Tag>明文</Tag>
          )}
        </header>
        <Form layout="vertical" requiredMark={false}>
          <Form.Item label="启用加密（AES-GCM + PBKDF2）">
            <Switch
              checked={isEncrypted}
              onChange={onToggleEncryption}
              checkedChildren="开"
              unCheckedChildren="关"
            />
            <Text type="secondary" className="settings-drawer__hint">
              {isEncrypted
                ? '所有凭证均以你的主密码派生密钥加密，不在浏览器外传输。'
                : '凭证以明文形式保存在 localStorage（默认）。建议在共享设备上启用加密。'}
            </Text>
          </Form.Item>

          {isEncrypted && (
            <Form.Item label="主密码">
              <Space wrap>
                <Button
                  icon={<KeyOutlined />}
                  onClick={() => setPwdModalMode('change')}
                  disabled={!isUnlocked}
                >
                  修改主密码
                </Button>
                <Button
                  icon={<LockOutlined />}
                  onClick={handleLockNow}
                  disabled={!isUnlocked}
                >
                  立即锁定
                </Button>
              </Space>
              {!isUnlocked && (
                <Text type="secondary" className="settings-drawer__hint">
                  当前已锁定，请先在顶部解锁后再修改主密码。
                </Text>
              )}
            </Form.Item>
          )}

          <Form.Item
            label={
              <span>
                空闲自动锁定：
                <strong>
                  {settings.vaultLockTimeoutMin === 0
                    ? '不自动锁定'
                    : `${settings.vaultLockTimeoutMin} 分钟`}
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
              marks={{ 0: '关闭', 30: '30', 60: '60', 120: '120' }}
              disabled={!isEncrypted}
            />
          </Form.Item>
        </Form>
      </section>

      {/* ---------------- Try it out ---------------- */}
      <section className="settings-drawer__section">
        <header className="settings-drawer__section-header">
          <span className="settings-drawer__section-title">试运行</span>
        </header>
        <Form layout="vertical" requiredMark={false}>
          <Form.Item
            label="CORS 代理 URL（可选）"
            help="会被前置到 Try-it-out 的目标地址，例如 https://proxy.example.com/?url="
          >
            <Input
              value={settings.tryItOutProxyUrl ?? ''}
              onChange={(e) => setProxyUrl(e.target.value)}
              placeholder="留空表示不使用代理"
              allowClear
            />
          </Form.Item>
          <Form.Item label="请求超时（毫秒）">
            <InputNumber
              min={1000}
              max={300000}
              step={1000}
              value={settings.tryItOutTimeoutMs}
              onChange={setTimeoutMs}
              style={{ width: 200 }}
            />
          </Form.Item>
        </Form>
      </section>

      {/* ---------------- Data ---------------- */}
      <section className="settings-drawer__section">
        <header className="settings-drawer__section-header">
          <span className="settings-drawer__section-title">数据</span>
          {hasEncryptedData && state === 'encrypted-locked' && (
            <Tag color="gold">导出将不包含已锁定的凭证</Tag>
          )}
        </header>
        <Space direction="vertical" style={{ width: '100%' }}>
          <Button
            block
            icon={<DownloadOutlined />}
            onClick={handleExport}
          >
            导出 JSON
          </Button>
          <Button
            block
            icon={<UploadOutlined />}
            onClick={handleImportClick}
          >
            导入 JSON
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
            重置全部
          </Button>
        </Space>
      </section>

      {/* ---------------- Modals ---------------- */}
      <MasterPasswordModal
        open={pwdModalMode !== null}
        mode={pwdModalMode ?? 'set'}
        onClose={() => setPwdModalMode(null)}
        onSuccess={() => {
          if (pwdModalMode === 'set') message.success('已启用加密');
          if (pwdModalMode === 'change') message.success('主密码已修改');
        }}
      />

      <Modal
        open={disableConfirmOpen}
        title="确认关闭加密"
        okText="确认关闭"
        cancelText="取消"
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
          关闭加密会把所有凭证以<strong>明文</strong>形式写回 localStorage。
          请输入当前主密码以确认。
        </p>
        <Input.Password
          value={disablePwd}
          onChange={(e) => setDisablePwd(e.target.value)}
          placeholder="当前主密码"
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
