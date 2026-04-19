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
import { useTranslation } from 'react-i18next';
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

const TITLE_KEYS: Record<MasterPasswordModalMode, string> = {
  set: 'mpModal.title.set',
  change: 'mpModal.title.change',
  unlock: 'mpModal.title.unlock',
};

const OK_KEYS: Record<MasterPasswordModalMode, string> = {
  set: 'mpModal.ok.set',
  change: 'mpModal.ok.change',
  unlock: 'mpModal.ok.unlock',
};

const MIN_PASSWORD_LENGTH = 4;

const MasterPasswordModal: React.FC<Props> = ({
  open,
  mode,
  onClose,
  onSuccess,
}) => {
  const { t } = useTranslation();
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
          setErrorText(t('mpModal.error.mismatch'));
          return;
        }
        await enableEncryption(values.newPassword);
      } else if (mode === 'change') {
        if (!values.newPassword || values.newPassword !== values.confirmPassword) {
          setErrorText(t('mpModal.error.mismatchNew'));
          return;
        }
        const ok = await changePassword(
          values.oldPassword ?? '',
          values.newPassword,
        );
        if (!ok) {
          setErrorText(t('mpModal.error.changeFailed'));
          return;
        }
      } else {
        const ok = await unlock(values.unlockPassword ?? '');
        if (!ok) {
          setErrorText(t('mpModal.error.wrongPwd'));
          return;
        }
      }
      onSuccess?.();
      onClose();
    } catch (err) {
      const msg = err instanceof Error ? err.message : t('mpModal.error.generic');
      setErrorText(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const minLenMsg = t('mpModal.field.minLen', { min: MIN_PASSWORD_LENGTH });

  return (
    <Modal
      open={open}
      title={t(TITLE_KEYS[mode])}
      onCancel={onClose}
      onOk={handleOk}
      okText={t(OK_KEYS[mode])}
      cancelText={t('common.cancel')}
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
          message={t('mpModal.notice.set.title')}
          description={t('mpModal.notice.set.desc')}
        />
      )}
      {mode === 'change' && (
        <Alert
          type="warning"
          showIcon
          className="master-password-modal__notice"
          message={t('mpModal.notice.change')}
        />
      )}
      <Form layout="vertical" form={form} requiredMark={false}>
        {mode === 'change' && (
          <Form.Item
            label={t('mpModal.field.oldPwd')}
            name="oldPassword"
            rules={[{ required: true, message: t('mpModal.field.oldPwd.required') }]}
          >
            <Input.Password autoFocus placeholder={t('mpModal.field.oldPwd.placeholder')} />
          </Form.Item>
        )}
        {(mode === 'set' || mode === 'change') && (
          <>
            <Form.Item
              label={mode === 'change' ? t('mpModal.field.newPwd') : t('mpModal.field.pwd')}
              name="newPassword"
              rules={[
                { required: true, message: t('mpModal.field.newPwd.required') },
                { min: MIN_PASSWORD_LENGTH, message: minLenMsg },
              ]}
            >
              <Input.Password autoFocus={mode === 'set'} placeholder={minLenMsg} />
            </Form.Item>
            <Form.Item
              label={t('mpModal.field.confirm')}
              name="confirmPassword"
              dependencies={['newPassword']}
              rules={[
                { required: true, message: t('mpModal.field.confirm.required') },
                ({ getFieldValue }) => ({
                  validator(_, value) {
                    if (!value || getFieldValue('newPassword') === value) {
                      return Promise.resolve();
                    }
                    return Promise.reject(new Error(t('mpModal.error.mismatch')));
                  },
                }),
              ]}
            >
              <Input.Password placeholder={t('mpModal.field.confirm.placeholder')} />
            </Form.Item>
          </>
        )}
        {mode === 'unlock' && (
          <Form.Item
            label={t('mpModal.field.pwd')}
            name="unlockPassword"
            rules={[{ required: true, message: t('mpModal.field.newPwd.required') }]}
          >
            <Input.Password autoFocus placeholder={t('mpModal.field.unlockPwd.placeholder')} />
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
