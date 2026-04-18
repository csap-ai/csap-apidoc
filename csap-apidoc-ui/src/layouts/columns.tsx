import {Tooltip, Tag, Collapse, Badge} from 'antd';
import React from 'react';
import type {ColumnsType} from 'antd/es/table';
import {InfoCircleOutlined, CheckCircleOutlined, WarningOutlined, ExclamationCircleFilled} from '@ant-design/icons';

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

export const columns: ColumnsType<HeaderRecord> = [
    {
        title: '参数名称',
        dataIndex: 'key',
        key: 'key',
        width: 150,
    },
    {
        title: '参数值',
        dataIndex: 'value',
        key: 'value',
        width: 180,
        ellipsis: {
            showTitle: false,
        },
        render: (text: string) => (
            <Tooltip placement="topLeft" title={text || ''}>
                {text || ''}
            </Tooltip>
        ),
    },
    {
        title: '是否必须',
        dataIndex: 'required',
        key: 'required',
        width: 100,
        render: (text: any, record: HeaderRecord) => {
            return record.required === true ? '是' : '否';
        },
    },
    {
        title: '示例',
        dataIndex: 'example',
        key: 'example',
        width: 200,
        ellipsis: {
            showTitle: false,
        },
        render: (text: string) => (
            <Tooltip placement="topLeft" title={text || ''}>
                {text || ''}
            </Tooltip>
        ),
    },
    {
        title: '备注',
        dataIndex: 'description',
        key: 'description',
        ellipsis: {
            showTitle: false,
        },
        render: (text: string) => (
            <Tooltip placement="topLeft" title={text || ''}>
                {text || ''}
            </Tooltip>
        ),
    },
];

/**
 * 获取枚举项的展示标签（固定取 code 和 message）
 */
const getEnumLabel = (item: EnumItem): string => {
    if (!item) return '';

    // 固定取 code 和 message（优先级：message > description）
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
    
    // 兜底：显示第一个非空字段
    const firstValue = Object.values(item).find(v => v !== undefined && v !== null);
    return firstValue ? String(firstValue) : 'N/A';
};

/**
 * 渲染枚举信息的组件
 */
const EnumRenderer: React.FC<{ enumData: EnumItem[] }> = ({enumData}) => {
    if (!enumData || enumData.length === 0) return null;

    // 简单枚举（≤3个）- 行内标签展示
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

    // 中等枚举（4-8个）- Tooltip悬浮展示
    if (enumData.length <= 8) {
        const tooltipContent = (
            <div style={{maxWidth: '400px'}}>
                <div style={{fontWeight: 600, marginBottom: '8px', fontSize: '13px'}}>枚举值：</div>
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
                    {enumData.length} 个枚举值
                </Tag>
            </Tooltip>
        );
    }

    // 复杂枚举（>8个）- 可展开详情
    return (
        <Collapse
            ghost
            className="enum-collapse"
        >
            <Panel
                header={
                    <span style={{fontSize: '12px', color: '#6366f1', fontWeight: 600}}>
            <InfoCircleOutlined style={{marginRight: '4px'}}/>
            查看 {enumData.length} 个枚举值
          </span>
                }
                key="1"
            >
                <div className="enum-grid">
                    {enumData.map((item, index) => {
                        // 检测枚举项有哪些字段
                        const fields = Object.keys(item).filter(k => item[k] !== undefined);

                        // 常见的基本字段集合（用于判断是否需要简化显示）
                        const commonFields = ['name', 'code', 'value', 'message', 'desc', 'label', 'id', 'key'];

                        // 如果只有常见基本字段（≤3个），使用简化显示
                        if (fields.length <= 3 && fields.every(f => commonFields.includes(f))) {
                            const label = getEnumLabel(item);
                            return (
                                <div key={index} className="enum-item">
                                    {label}
                                </div>
                            );
                        }

                        // 复杂枚举项，展示所有字段
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

/**
 * 渲染验证信息的组件
 */
const ValidationRenderer: React.FC<{ validations: ValidateItem[] }> = ({ validations }) => {
  if (!validations || validations.length === 0) return null;

  // 简单验证（1-2个）- 行内展示
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
                    {validation.type || '验证规则'}
                  </span>
                  {validation.code && (
                    <Tooltip title="错误编码：用于前端错误处理和国际化">
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
                        错误码:{validation.code}
                      </Tag>
                    </Tooltip>
                  )}
                  {validation.level !== undefined && (
                    <Tooltip title={`验证优先级：${validation.level}（数值越小优先级越高，最先执行）`}>
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
                        {validation.level === 1 ? '最优先' : validation.level === 2 ? '次优先' : `优先级${validation.level}`}
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
                    {isRegex ? `正则: ${validation.pattern}` : validation.pattern}
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

  // 复杂验证（3个以上）- 折叠展示
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
            {validations.length} 个验证规则
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
                    {validation.type || '验证规则'}
                  </span>
                  {validation.code && (
                    <Tooltip title="错误编码：用于前端错误处理和国际化">
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
                        错误码:{validation.code}
                      </Tag>
                    </Tooltip>
                  )}
                  {validation.level !== undefined && (
                    <Tooltip title={`验证优先级：${validation.level}（数值越小优先级越高，最先执行）`}>
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
                        {validation.level === 1 ? '最优先' : validation.level === 2 ? '次优先' : `优先级${validation.level}`}
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
                    {isRegex ? `正则表达式: ${validation.pattern}` : `规则: ${validation.pattern}`}
                  </div>
                )}
                
                {validation.message && (
                  <div style={{ 
                    color: '#8c8c8c',
                    fontSize: '11px',
                    marginLeft: '24px',
                    padding: '3px 0'
                  }}>
                    💬 错误提示: {validation.message}
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

export const columnsBody: ColumnsType<BodyRecord> = [
    {
        title: '参数名称',
        dataIndex: 'name',
        key: 'name',
        width: 200,
        ellipsis: {
            showTitle: false,
        },
        render: (text: string, record: BodyRecord) => {
            const tooltipTitle = record.required 
                ? `${text} (必填)` 
                : `${text} (可选)`;
            
            return (
                <Tooltip placement="topLeft" title={tooltipTitle}>
                    <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                        {record.required ? (
                            <ExclamationCircleFilled style={{ color: '#ff4d4f', fontSize: '12px' }} />
                        ) : (
                            <CheckCircleOutlined style={{ color: '#d9d9d9', fontSize: '12px' }} />
                        )}
                        <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                            {text || ''}
                        </span>
                    </span>
                </Tooltip>
            );
        },
    },
    {
        title: '数据类型',
        dataIndex: 'dataType',
        key: 'dataType',
        width: 180,
        ellipsis: {
            showTitle: false,
        },
        render: (text: string) => (
            <Tooltip placement="topLeft" title={text || ''}>
                {text || ''}
            </Tooltip>
        ),
    },
    {
        title: '默认值',
        dataIndex: 'defaultValue',
        key: 'defaultValue',
        width: 120,
        ellipsis: {
            showTitle: false,
        },
        render: (text: string) => (
            <Tooltip placement="topLeft" title={text || ''}>
                {text || ''}
            </Tooltip>
        ),
    },
    {
        title: '示例',
        dataIndex: 'example',
        key: 'example',
        width: 150,
        ellipsis: {
            showTitle: false,
        },
        render: (text: string) => (
            <Tooltip placement="topLeft" title={text || ''}>
                {text || ''}
            </Tooltip>
        ),
    },
    {
        title: '备注',
        dataIndex: 'value',
        key: 'value',
        width: 200,
        ellipsis: {
            showTitle: false,
        },
        render: (text: string) => (
            <Tooltip placement="topLeft" title={text || ''}>
                {text || ''}
            </Tooltip>
        ),
    },
    {
        title: '描述',
        dataIndex: 'description',
        key: 'description',
        ellipsis: {
            showTitle: false,
        },
        render: (text: string, record: BodyRecord) => (
            <div>
                <Tooltip placement="topLeft" title={text || ''}>
                    <div style={{
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap'
                    }}>
                        {text || ''}
                    </div>
                </Tooltip>
                {/* 显示枚举信息 */}
                {record.extendDescr && record.extendDescr.length > 0 && (
                    <EnumRenderer enumData={record.extendDescr}/>
                )}
                {/* 显示验证信息 */}
                {record.validate && record.validate.length > 0 && (
                    <ValidationRenderer validations={record.validate}/>
                )}
            </div>
        ),
    },
];

export const apiOptions = [];

// 返回数据专用的列定义（参数名称不显示必填/可选标记）
export const columnsBodyResponse: ColumnsType<BodyRecord> = [
    {
        title: '参数名称',
        dataIndex: 'name',
        key: 'name',
        width: 200,
        ellipsis: {
            showTitle: false,
        },
        render: (text: string) => (
            <Tooltip placement="topLeft" title={text || ''}>
                <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {text || ''}
                </span>
            </Tooltip>
        ),
    },
    {
        title: '数据类型',
        dataIndex: 'dataType',
        key: 'dataType',
        width: 180,
        ellipsis: {
            showTitle: false,
        },
        render: (text: string) => (
            <Tooltip placement="topLeft" title={text || ''}>
                {text || ''}
            </Tooltip>
        ),
    },
    {
        title: '默认值',
        dataIndex: 'defaultValue',
        key: 'defaultValue',
        width: 100,
        ellipsis: {
            showTitle: false,
        },
        render: (text: string) => (
            <Tooltip placement="topLeft" title={text || ''}>
                {text || ''}
            </Tooltip>
        ),
    },
    {
        title: '示例',
        dataIndex: 'example',
        key: 'example',
        width: 150,
        ellipsis: {
            showTitle: false,
        },
        render: (text: string) => (
            <Tooltip placement="topLeft" title={text || ''}>
                {text || ''}
            </Tooltip>
        ),
    },
    {
        title: '备注',
        dataIndex: 'value',
        key: 'value',
        width: 150,
        ellipsis: {
            showTitle: false,
        },
        render: (text: string) => (
            <Tooltip placement="topLeft" title={text || ''}>
                {text || ''}
            </Tooltip>
        ),
    },
    {
        title: '描述',
        dataIndex: 'description',
        key: 'description',
        ellipsis: {
            showTitle: false,
        },
        render: (text: string, record: BodyRecord) => (
            <div>
                <Tooltip placement="topLeft" title={text || ''}>
                    <div style={{
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap'
                    }}>
                        {text || ''}
                    </div>
                </Tooltip>
                {/* 显示枚举信息 */}
                {record.extendDescr && record.extendDescr.length > 0 && (
                    <EnumRenderer enumData={record.extendDescr}/>
                )}
                {/* 显示验证信息 */}
                {record.validate && record.validate.length > 0 && (
                    <ValidationRenderer validations={record.validate}/>
                )}
            </div>
        ),
    },
];

