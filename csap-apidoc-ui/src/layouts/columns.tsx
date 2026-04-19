import {Tooltip, Tag, Collapse, Badge} from 'antd';
import React from 'react';
import type {ColumnsType} from 'antd/es/table';
import {InfoCircleOutlined, CheckCircleOutlined, WarningOutlined, ExclamationCircleFilled} from '@ant-design/icons';
import {useTranslation} from 'react-i18next';
import type {TFunction} from 'i18next';

const {Panel} = Collapse;

interface HeaderRecord {
    key: string;
    value: string;
    required: boolean;
    example: string;
    description: string;
}

interface EnumItem {
    name: string;
    code?: string;
    description?: string;

    [key: string]: string | undefined;
}

interface ValidateItem {
    code?: string;
    type?: string;
    descr?: string;
    pattern?: string;
    message?: string;
    level?: number;
}

interface BodyRecord {
    name: string;
    dataType: string;
    required: boolean;
    defaultValue: string;
    example: string;
    value: string;
    description: string;
    paramType?: string;
    extendDescr?: EnumItem[];
    validate?: ValidateItem[];
    children?: BodyRecord[];
}

// M8.1: column titles are computed from the active locale via factory
// helpers below. The original `columns / columnsBody / columnsBodyResponse`
// constants are kept as zh-CN snapshots so any caller that still imports
// the static export keeps working without behavioral change.

const getEnumLabel = (item: EnumItem): string => {
    if (!item) return '';

    const label = item.message || item.description;

    if (item.code && label) {
        return `${item.code}: ${label}`;
    } else if (label) {
        return label;
    } else if (item.code) {
        return String(item.code);
    } else if (item.name) {
        return item.name;
    }

    const firstValue = Object.values(item).find(v => v !== undefined && v !== null);
    return firstValue ? String(firstValue) : 'N/A';
};

const EnumRenderer: React.FC<{ enumData: EnumItem[] }> = ({enumData}) => {
    const {t} = useTranslation();
    if (!enumData || enumData.length === 0) return null;

    if (enumData.length <= 3) {
        return (
            <div className="enum-inline-tags">
                {enumData.map((item, index) => {
                    const label = getEnumLabel(item);
                    return (
                        <Tag key={index}>{label}</Tag>
                    );
                })}
            </div>
        );
    }

    if (enumData.length <= 8) {
        const tooltipContent = (
            <div style={{maxWidth: '400px'}}>
                <div style={{fontWeight: 600, marginBottom: '8px', fontSize: '13px'}}>{t('cols.enum.title')}</div>
                <div style={{display: 'flex', flexWrap: 'wrap', gap: '6px'}}>
                    {enumData.map((item, index) => {
                        const label = getEnumLabel(item);
                        return (
                            <div
                                key={index}
                                style={{
                                    padding: '4px 8px',
                                    background: 'rgba(255,255,255,0.15)',
                                    borderRadius: '4px',
                                    fontSize: '12px'
                                }}
                            >
                                {label}
                            </div>
                        );
                    })}
                </div>
            </div>
        );

        return (
            <Tooltip title={tooltipContent} overlayStyle={{maxWidth: '500px'}}>
                <Tag
                    icon={<InfoCircleOutlined/>}
                    className="enum-tooltip-tag"
                >
                    {t('cols.enum.count', {count: enumData.length})}
                </Tag>
            </Tooltip>
        );
    }

    return (
        <Collapse
            ghost
            className="enum-collapse"
        >
            <Panel
                header={
                    <span style={{fontSize: '12px', color: '#6366f1', fontWeight: 600}}>
            <InfoCircleOutlined style={{marginRight: '4px'}}/>
            {t('cols.enum.viewMore', {count: enumData.length})}
          </span>
                }
                key="1"
            >
                <div className="enum-grid">
                    {enumData.map((item, index) => {
                        const fields = Object.keys(item).filter(k => item[k] !== undefined);
                        const commonFields = ['name', 'code', 'value', 'message', 'desc', 'label', 'id', 'key'];

                        if (fields.length <= 3 && fields.every(f => commonFields.includes(f))) {
                            const label = getEnumLabel(item);
                            return (
                                <div key={index} className="enum-item">
                                    {label}
                                </div>
                            );
                        }

                        return (
                            <div key={index} className="enum-item">
                                {fields.map(field => (
                                    <div key={field} className="enum-field">
                                        <span className="enum-field-label">{field}:</span>
                                        <span className="enum-field-value">{item[field]}</span>
                                    </div>
                                ))}
                            </div>
                        );
                    })}
                </div>
            </Panel>
        </Collapse>
    );
};

const ValidationRenderer: React.FC<{ validations: ValidateItem[] }> = ({ validations }) => {
  const {t} = useTranslation();
  if (!validations || validations.length === 0) return null;

  const priorityLabel = (level: number): string => {
    if (level === 1) return t('cols.validation.priority.high');
    if (level === 2) return t('cols.validation.priority.mid');
    return t('cols.validation.priority.normal', {level});
  };

  if (validations.length <= 2) {
    return (
      <div style={{ marginTop: '8px' }}>
        {validations.map((validation, index) => {
          const isRegex = validation.pattern && (validation.pattern.startsWith('^') || validation.pattern.includes('\\d'));

          return (
            <div
              key={index}
              style={{
                padding: '6px 10px',
                marginBottom: index < validations.length - 1 ? '4px' : 0,
                background: 'linear-gradient(135deg, rgba(250, 173, 20, 0.08), rgba(250, 140, 22, 0.05))',
                border: '1px solid rgba(250, 173, 20, 0.3)',
                borderRadius: '6px',
                fontSize: '12px',
                display: 'flex',
                alignItems: 'flex-start',
                gap: '6px'
              }}
            >
              <CheckCircleOutlined style={{ color: '#fa8c16', marginTop: '2px', flexShrink: 0 }} />
              <div style={{ flex: 1 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '2px', flexWrap: 'wrap' }}>
                  <span style={{ fontWeight: 600, color: '#d46b08' }}>
                    {validation.type || t('cols.validation.label')}
                  </span>
                  {validation.code && (
                    <Tooltip title={t('cols.validation.codeTooltip')}>
                      <Tag
                        color="orange"
                        style={{
                          margin: 0,
                          fontSize: '10px',
                          padding: '0 6px',
                          lineHeight: '18px',
                          height: '18px',
                          cursor: 'help'
                        }}
                      >
                        {t('cols.validation.errorCode', {code: validation.code})}
                      </Tag>
                    </Tooltip>
                  )}
                  {validation.level !== undefined && (
                    <Tooltip title={t('cols.validation.priorityTooltip', {level: validation.level})}>
                      <Tag
                        color={validation.level === 1 ? 'red' : validation.level === 2 ? 'orange' : 'gold'}
                        style={{
                          margin: 0,
                          fontSize: '10px',
                          padding: '0 6px',
                          lineHeight: '18px',
                          height: '18px',
                          cursor: 'help'
                        }}
                      >
                        {priorityLabel(validation.level)}
                      </Tag>
                    </Tooltip>
                  )}
                </div>
                {validation.descr && validation.descr !== validation.type && (
                  <div style={{ color: '#8c8c8c', marginBottom: '2px' }}>
                    {validation.descr}
                  </div>
                )}
                {validation.pattern && (
                  <div style={{
                    color: '#595959',
                    fontFamily: "'SF Mono', 'Monaco', monospace",
                    fontSize: '11px',
                    background: 'rgba(0,0,0,0.04)',
                    padding: '2px 6px',
                    borderRadius: '3px',
                    marginBottom: '2px'
                  }}>
                    {isRegex ? t('cols.validation.regex', {pattern: validation.pattern}) : validation.pattern}
                  </div>
                )}
                {validation.message && (
                  <div style={{ color: '#8c8c8c', fontSize: '11px' }}>
                    💬 {validation.message}
                  </div>
                )}
              </div>
            </div>
          );
        })}
      </div>
    );
  }

  return (
    <Collapse
      ghost
      style={{
        marginTop: '8px',
        background: 'rgba(250, 173, 20, 0.05)',
        borderRadius: '6px',
        border: '1px solid rgba(250, 173, 20, 0.2)'
      }}
    >
      <Panel
        header={
          <span style={{ fontSize: '12px', color: '#fa8c16', fontWeight: 600 }}>
            <WarningOutlined style={{ marginRight: '4px' }} />
            {t('cols.validation.count', {count: validations.length})}
          </span>
        }
        key="1"
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          {validations.map((validation, index) => {
            const isRegex = validation.pattern && (validation.pattern.startsWith('^') || validation.pattern.includes('\\d'));

            return (
              <div
                key={index}
                style={{
                  padding: '8px 10px',
                  background: 'white',
                  border: '1px solid #f0f0f0',
                  borderRadius: '4px',
                  fontSize: '12px'
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '4px', flexWrap: 'wrap' }}>
                  <Badge
                    count={index + 1}
                    style={{
                      backgroundColor: '#fa8c16',
                      fontSize: '10px',
                      height: '18px',
                      lineHeight: '18px',
                      minWidth: '18px'
                    }}
                  />
                  <span style={{ fontWeight: 600, color: '#d46b08' }}>
                    {validation.type || t('cols.validation.label')}
                  </span>
                  {validation.code && (
                    <Tooltip title={t('cols.validation.codeTooltip')}>
                      <Tag
                        color="orange"
                        style={{
                          margin: 0,
                          fontSize: '10px',
                          padding: '0 6px',
                          lineHeight: '18px',
                          height: '18px',
                          cursor: 'help'
                        }}
                      >
                        {t('cols.validation.errorCode', {code: validation.code})}
                      </Tag>
                    </Tooltip>
                  )}
                  {validation.level !== undefined && (
                    <Tooltip title={t('cols.validation.priorityTooltip', {level: validation.level})}>
                      <Tag
                        color={validation.level === 1 ? 'red' : validation.level === 2 ? 'orange' : 'gold'}
                        style={{
                          margin: 0,
                          fontSize: '10px',
                          padding: '0 6px',
                          lineHeight: '18px',
                          height: '18px',
                          cursor: 'help'
                        }}
                      >
                        {priorityLabel(validation.level)}
                      </Tag>
                    </Tooltip>
                  )}
                </div>

                {validation.descr && validation.descr !== validation.type && (
                  <div style={{ color: '#595959', marginLeft: '24px', marginBottom: '4px' }}>
                    📝 {validation.descr}
                  </div>
                )}

                {validation.pattern && (
                  <div style={{
                    color: '#262626',
                    fontFamily: "'SF Mono', 'Monaco', monospace",
                    fontSize: '11px',
                    background: '#f5f5f5',
                    padding: '4px 8px',
                    borderRadius: '3px',
                    marginLeft: '24px',
                    marginBottom: '4px',
                    wordBreak: 'break-all'
                  }}>
                    {isRegex
                      ? t('cols.validation.regexLong', {pattern: validation.pattern})
                      : t('cols.validation.rule', {pattern: validation.pattern})}
                  </div>
                )}

                {validation.message && (
                  <div style={{
                    color: '#8c8c8c',
                    fontSize: '11px',
                    marginLeft: '24px',
                    padding: '3px 0'
                  }}>
                    💬 {t('cols.validation.errorMessage', {message: validation.message})}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </Panel>
    </Collapse>
  );
};

/** Factory: header table columns. Pass a `t` function so titles refresh on locale switch. */
export const buildColumns = (t: TFunction): ColumnsType<HeaderRecord> => [
    {
        title: t('cols.paramName'),
        dataIndex: 'key',
        key: 'key',
        width: 150,
    },
    {
        title: t('cols.paramValue'),
        dataIndex: 'value',
        key: 'value',
        width: 180,
        ellipsis: {showTitle: false},
        render: (text: string) => (
            <Tooltip placement="topLeft" title={text || ''}>{text || ''}</Tooltip>
        ),
    },
    {
        title: t('cols.required'),
        dataIndex: 'required',
        key: 'required',
        width: 100,
        render: (_text: any, record: HeaderRecord) => record.required === true ? t('common.yes') : t('common.no'),
    },
    {
        title: t('cols.example'),
        dataIndex: 'example',
        key: 'example',
        width: 200,
        ellipsis: {showTitle: false},
        render: (text: string) => (
            <Tooltip placement="topLeft" title={text || ''}>{text || ''}</Tooltip>
        ),
    },
    {
        title: t('cols.remark'),
        dataIndex: 'description',
        key: 'description',
        ellipsis: {showTitle: false},
        render: (text: string) => (
            <Tooltip placement="topLeft" title={text || ''}>{text || ''}</Tooltip>
        ),
    },
];

/** Factory: request body columns (with required/optional indicator). */
export const buildColumnsBody = (t: TFunction): ColumnsType<BodyRecord> => [
    {
        title: t('cols.paramName'),
        dataIndex: 'name',
        key: 'name',
        width: 200,
        ellipsis: {showTitle: false},
        render: (text: string, record: BodyRecord) => {
            const tooltipTitle = record.required
                ? t('cols.tooltip.required', {name: text})
                : t('cols.tooltip.optional', {name: text});

            return (
                <Tooltip placement="topLeft" title={tooltipTitle}>
                    <span style={{display: 'flex', alignItems: 'center', gap: '4px'}}>
                        {record.required ? (
                            <ExclamationCircleFilled style={{color: '#ff4d4f', fontSize: '12px'}}/>
                        ) : (
                            <CheckCircleOutlined style={{color: '#d9d9d9', fontSize: '12px'}}/>
                        )}
                        <span style={{overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap'}}>
                            {text || ''}
                        </span>
                    </span>
                </Tooltip>
            );
        },
    },
    {
        title: t('cols.dataType'),
        dataIndex: 'dataType',
        key: 'dataType',
        width: 180,
        ellipsis: {showTitle: false},
        render: (text: string) => (
            <Tooltip placement="topLeft" title={text || ''}>{text || ''}</Tooltip>
        ),
    },
    {
        title: t('cols.defaultValue'),
        dataIndex: 'defaultValue',
        key: 'defaultValue',
        width: 120,
        ellipsis: {showTitle: false},
        render: (text: string) => (
            <Tooltip placement="topLeft" title={text || ''}>{text || ''}</Tooltip>
        ),
    },
    {
        title: t('cols.example'),
        dataIndex: 'example',
        key: 'example',
        width: 150,
        ellipsis: {showTitle: false},
        render: (text: string) => (
            <Tooltip placement="topLeft" title={text || ''}>{text || ''}</Tooltip>
        ),
    },
    {
        title: t('cols.remark'),
        dataIndex: 'value',
        key: 'value',
        width: 200,
        ellipsis: {showTitle: false},
        render: (text: string) => (
            <Tooltip placement="topLeft" title={text || ''}>{text || ''}</Tooltip>
        ),
    },
    {
        title: t('cols.description'),
        dataIndex: 'description',
        key: 'description',
        ellipsis: {showTitle: false},
        render: (text: string, record: BodyRecord) => (
            <div>
                <Tooltip placement="topLeft" title={text || ''}>
                    <div style={{overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap'}}>
                        {text || ''}
                    </div>
                </Tooltip>
                {record.extendDescr && record.extendDescr.length > 0 && (
                    <EnumRenderer enumData={record.extendDescr}/>
                )}
                {record.validate && record.validate.length > 0 && (
                    <ValidationRenderer validations={record.validate}/>
                )}
            </div>
        ),
    },
];

/** Factory: response body columns (no required/optional indicator on the name cell). */
export const buildColumnsBodyResponse = (t: TFunction): ColumnsType<BodyRecord> => [
    {
        title: t('cols.paramName'),
        dataIndex: 'name',
        key: 'name',
        width: 200,
        ellipsis: {showTitle: false},
        render: (text: string) => (
            <Tooltip placement="topLeft" title={text || ''}>
                <span style={{overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap'}}>
                    {text || ''}
                </span>
            </Tooltip>
        ),
    },
    {
        title: t('cols.dataType'),
        dataIndex: 'dataType',
        key: 'dataType',
        width: 180,
        ellipsis: {showTitle: false},
        render: (text: string) => (
            <Tooltip placement="topLeft" title={text || ''}>{text || ''}</Tooltip>
        ),
    },
    {
        title: t('cols.defaultValue'),
        dataIndex: 'defaultValue',
        key: 'defaultValue',
        width: 100,
        ellipsis: {showTitle: false},
        render: (text: string) => (
            <Tooltip placement="topLeft" title={text || ''}>{text || ''}</Tooltip>
        ),
    },
    {
        title: t('cols.example'),
        dataIndex: 'example',
        key: 'example',
        width: 150,
        ellipsis: {showTitle: false},
        render: (text: string) => (
            <Tooltip placement="topLeft" title={text || ''}>{text || ''}</Tooltip>
        ),
    },
    {
        title: t('cols.remark'),
        dataIndex: 'value',
        key: 'value',
        width: 150,
        ellipsis: {showTitle: false},
        render: (text: string) => (
            <Tooltip placement="topLeft" title={text || ''}>{text || ''}</Tooltip>
        ),
    },
    {
        title: t('cols.description'),
        dataIndex: 'description',
        key: 'description',
        ellipsis: {showTitle: false},
        render: (text: string, record: BodyRecord) => (
            <div>
                <Tooltip placement="topLeft" title={text || ''}>
                    <div style={{overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap'}}>
                        {text || ''}
                    </div>
                </Tooltip>
                {record.extendDescr && record.extendDescr.length > 0 && (
                    <EnumRenderer enumData={record.extendDescr}/>
                )}
                {record.validate && record.validate.length > 0 && (
                    <ValidationRenderer validations={record.validate}/>
                )}
            </div>
        ),
    },
];

// Backwards-compat zh-CN snapshots for any external import path that still
// pulls the static constants. Internally `layouts/index.tsx` switched to the
// factory helpers so it picks up locale changes immediately.
import i18n from '@/i18n';

const fallbackT: TFunction = ((key: string, opts?: Record<string, unknown>) =>
    i18n.t(key, opts as never)) as unknown as TFunction;

export const columns: ColumnsType<HeaderRecord> = buildColumns(fallbackT);
export const columnsBody: ColumnsType<BodyRecord> = buildColumnsBody(fallbackT);
export const columnsBodyResponse: ColumnsType<BodyRecord> = buildColumnsBodyResponse(fallbackT);

export const apiOptions = [];
