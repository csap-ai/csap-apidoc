import {Layout, Select, Dropdown, Button, message} from 'antd';
import {useState, forwardRef, useImperativeHandle} from 'react';
import {DownloadOutlined, FileTextOutlined, ApiOutlined, DownOutlined} from '@ant-design/icons';
import type {MenuProps} from 'antd';
import CSAP from '@/assets/ICON.png';
import EnvironmentSwitcher from '@/components/EnvironmentSwitcher';
import HeadersButton from '@/components/HeadersButton';
import AuthButton from '@/components/AuthButton';
import './index.less';

const {Header} = Layout;
const {Option} = Select;

interface IOptionitem {
    url: string;
    name: string;
    version: string;
}

interface IProps {
    apiOptions: IOptionitem[];
    onChangeValue: (value: string) => void;
    apiInfo?: any;
    apiList?: any[];
    onExportOpenApi?: (apiKey: string, parentKey: string) => Promise<Record<string, any>>;
    onExport?: (format: 'openapi' | 'postman' | 'markdown') => Promise<void>;
}

const LayoutHeader = (props: IProps, ref) => {
    const [value, setValue] = useState(null);
    const [exporting, setExporting] = useState(false);

    useImperativeHandle(ref, () => ({
        value,
    }));

    const onChangeValue = (value: string) => {
        if (value) {
            props.onChangeValue(value);
        }
        setValue(() => value);
    };

    const handleExport = async (format: 'openapi' | 'postman' | 'markdown') => {
        if (!props.onExport) {
            message.warning('导出功能暂未配置');
            return;
        }

        setExporting(true);
        try {
            await props.onExport(format);
        } catch (error) {
            console.error('导出失败:', error);
            message.error('导出失败，请稍后重试');
        } finally {
            setExporting(false);
        }
    };

    const exportMenuItems: MenuProps['items'] = [
        {
            key: 'openapi',
            label: 'OpenAPI 3.0',
            icon: <ApiOutlined />,
            onClick: () => handleExport('openapi'),
        },
        {
            key: 'postman',
            label: 'Postman Collection',
            icon: <FileTextOutlined />,
            onClick: () => handleExport('postman'),
        },
        {
            key: 'markdown',
            label: 'Markdown',
            icon: <FileTextOutlined />,
            onClick: () => handleExport('markdown'),
        },
    ];

    return (
        <Header>
            <div className="header-lf">
                <img src={CSAP} alt=""/>
                <span>API Documentation</span>
            </div>
            <div className="header-ri">
                <div className="apiList">
                    <div className="api-item">
                        <EnvironmentSwitcher />
                    </div>
                    <div className="api-item">
                        <div>切换服务</div>
                        <Select
                            placeholder="选择服务"
                            ref={ref}
                            allowClear
                            value={value}
                            onChange={onChangeValue}
                            style={{width: 160}}
                        >
                            {props.apiOptions.map((item, index) => {
                                return (
                                    <Option key={item.url} value={item.url}>
                                        {item.name}
                                    </Option>
                                );
                            })}
                        </Select>
                    </div>
                    <div className="api-item">
                        <div>分组</div>
                        <Select placeholder="选择分组" allowClear style={{width: 140}}>
                            <Option value="default0">default</Option>
                        </Select>
                    </div>
                    <div className="api-item">
                        <div>版本</div>
                        <Select placeholder="选择版本" allowClear style={{width: 120}}>
                            <Option value="default1">v1</Option>
                            <Option value="default2">v2</Option>
                        </Select>
                    </div>
                    <div className="api-item">
                        <HeadersButton knownServices={props.apiOptions?.map(o => ({ url: o.url, name: o.name }))} />
                    </div>
                    <div className="api-item">
                        <AuthButton knownServices={props.apiOptions?.map(o => ({ url: o.url, name: o.name }))} />
                    </div>
                    <div className="api-item">
                        <Dropdown menu={{items: exportMenuItems}} placement="bottomRight">
                            <Button 
                                type="primary" 
                                icon={<DownloadOutlined />}
                                loading={exporting}
                            >
                                导出文档 <DownOutlined />
                            </Button>
                        </Dropdown>
                    </div>
                </div>
            </div>
        </Header>
    );
};

export default forwardRef(LayoutHeader);
