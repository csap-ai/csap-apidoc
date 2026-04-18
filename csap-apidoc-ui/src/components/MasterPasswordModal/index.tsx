/**
 * MasterPasswordModal — single component, three modes:
 *   - 'set'    → first-time setup. Two fields: new password + confirm.
 *   - 'change' → rotate password. Three fields: old + new + confirm.
 *   - 'unlock' → enter the existing password. One field.
 *
 * The modal is purely presentational: it collects the password(s) and
 * delegates to a handler returned by VaultContext. We never store the
 * password in component state beyond the modal lifetime.
 */

import React, { useEffect, useState } from 'react';
import { Form, Input, Modal, Alert, Typography } from 'antd';
import { useVault } from '@/contexts/VaultContext';
import './index.less';

const { Text } = Typography;

export type MasterPasswordModalMode = 'set' | 'change' | 'unlock';

interface Props {
  open: boolean;
  mode: MasterPasswordModalMode;
  onClose: () => void;
  /** Called after a successful operation. */
  onSuccess?: () => void;
}

interface FormValues {
  oldPassword?: string;
  newPassword?: string;
  confirmPassword?: string;
  unlockPassword?: string;
}

const TITLES: Record<MasterPasswordModalMode, string> = {
  set: '设置主密码（启用加密）',
  change: '修改主密码',
  unlock: '解锁保险库',
};

const OK_TEXTS: Record<MasterPasswordModalMode, string> = {
  set: '启用加密',
  change: '修改',
  unlock: '解锁',
};

const MIN_PASSWORD_LENGTH = 4;

const MasterPasswordModal: React.FC<Props> = ({
  open,
  mode,
  onClose,
  onSuccess,
}) => {
  const { enableEncryption, changePassword, unlock } = useVault();
  const [form] = Form.useForm<FormValues>();
  const [submitting, setSubmitting] = useState(false);
  const [errorText, setErrorText] = useState<string | null>(null);

  useEffect(() => {
    if (!open) {
      form.resetFields();
      setErrorText(null);
      setSubmitting(false);
    }
  }, [open, form]);

  const handleOk = async (): Promise<void> => {
    setErrorText(null);
    let values: FormValues;
    try {
      values = await form.validateFields();
    } catch {
      return; // antd shows field-level errors
    }
    setSubmitting(true);
    try {
      if (mode === 'set') {
        if (!values.newPassword || values.newPassword !== values.confirmPassword) {
          setErrorText('两次输入的密码不一致');
          return;
        }
        await enableEncryption(values.newPassword);
      } else if (mode === 'change') {
        if (!values.newPassword || values.newPassword !== values.confirmPassword) {
          setErrorText('两次输入的新密码不一致');
          return;
        }
        const ok = await changePassword(
          values.oldPassword ?? '',
          values.newPassword,
        );
        if (!ok) {
          setErrorText('原密码不正确，或新密码不符合要求');
          return;
        }
      } else {
        const ok = await unlock(values.unlockPassword ?? '');
        if (!ok) {
          setErrorText('密码错误，请重试');
          return;
        }
      }
      onSuccess?.();
      onClose();
    } catch (err) {
      const msg = err instanceof Error ? err.message : '操作失败，请重试';
      setErrorText(msg);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      open={open}
      title={TITLES[mode]}
      onCancel={onClose}
      onOk={handleOk}
      okText={OK_TEXTS[mode]}
      cancelText="取消"
      confirmLoading={submitting}
      destroyOnClose
      maskClosable={false}
      className="master-password-modal"
    >
      {mode === 'set' && (
        <Alert
          type="info"
          showIcon
          className="master-password-modal__notice"
          message="请记住这个主密码"
          description="主密码仅保存在你的浏览器内存中，CSAP 团队无法找回。忘记后只能重置全部数据。"
        />
      )}
      {mode === 'change' && (
        <Alert
          type="warning"
          showIcon
          className="master-password-modal__notice"
          message="修改后所有现有凭证将以新密码重新加密"
        />
      )}
      <Form layout="vertical" form={form} requiredMark={false}>
        {mode === 'change' && (
          <Form.Item
            label="原主密码"
            name="oldPassword"
            rules={[{ required: true, message: '请输入当前主密码' }]}
          >
            <Input.Password autoFocus placeholder="当前主密码" />
          </Form.Item>
        )}
        {(mode === 'set' || mode === 'change') && (
          <>
            <Form.Item
              label={mode === 'change' ? '新主密码' : '主密码'}
              name="newPassword"
              rules={[
                { required: true, message: '请输入主密码' },
                {
                  min: MIN_PASSWORD_LENGTH,
                  message: `至少 ${MIN_PASSWORD_LENGTH} 个字符`,
                },
              ]}
            >
              <Input.Password
                autoFocus={mode === 'set'}
                placeholder={`至少 ${MIN_PASSWORD_LENGTH} 个字符`}
              />
            </Form.Item>
            <Form.Item
              label="确认密码"
              name="confirmPassword"
              dependencies={['newPassword']}
              rules={[
                { required: true, message: '请再次输入密码' },
                ({ getFieldValue }) => ({
                  validator(_, value) {
                    if (!value || getFieldValue('newPassword') === value) {
                      return Promise.resolve();
                    }
                    return Promise.reject(new Error('两次输入的密码不一致'));
                  },
                }),
              ]}
            >
              <Input.Password placeholder="再输入一次" />
            </Form.Item>
          </>
        )}
        {mode === 'unlock' && (
          <Form.Item
            label="主密码"
            name="unlockPassword"
            rules={[{ required: true, message: '请输入主密码' }]}
          >
            <Input.Password autoFocus placeholder="输入主密码以解锁" />
          </Form.Item>
        )}
      </Form>
      {errorText && (
        <Text type="danger" className="master-password-modal__err">
          {errorText}
        </Text>
      )}
    </Modal>
  );
};

export default MasterPasswordModal;
