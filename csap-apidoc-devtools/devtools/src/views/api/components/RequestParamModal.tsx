import {useState, forwardRef, useImperativeHandle, useRef} from 'react'
import {Modal, Table, Button, message, Tag, Card, Space, Col, Input, Empty, Select, Checkbox, Tooltip, Badge, Switch} from 'antd'
import {
    ApiOutlined,
    FileTextOutlined,
    SettingOutlined,
    DatabaseOutlined,
    FieldStringOutlined,
    SearchOutlined,
    RightOutlined,
    LeftOutlined,
    DeleteOutlined,
    SafetyOutlined,
    NumberOutlined,
    CheckSquareOutlined,
    UnorderedListOutlined,
    CalendarOutlined,
    FileOutlined
} from '@ant-design/icons'
import type {ColumnsType} from 'antd/es/table'
import type {ResizeCallbackData} from 'react-resizable'
import {getParamList} from '@/api/fieldName'
import type {MethodModel, ParamModel, ControllerModel} from '@/api/devtools'
import {addRequestParam, getFields} from '@/api/devtools'
import FieldValidateModal from './FieldValidateModal'
import {ResizableTitle} from '@/components/table'
import '@/components/table/resizable.scss'

const {Option} = Select

export interface RequestParamModalRef {
    open: (controller: ControllerModel, method: MethodModel) => void
}

interface FieldData {
    key: string
    name: string
    dataType: string
    value?: string
    required: boolean
    paramType?: string
    parameters?: FieldData[]
    keyName: string
    validate?: any[]
}

// 扩展 ParamModel 以支持运行时添加的属性
interface ExtendedParamModel extends ParamModel {
    parameters?: FieldData[]
    required?: boolean
    value?: string
}

const RequestParamModal = forwardRef<RequestParamModalRef>((_props, ref) => {
    const [visible, setVisible] = useState(false)
    const [loading, setLoading] = useState(false)
    const [requestParams, setRequestParams] = useState<ExtendedParamModel[]>([])
    const [currentMethod, setCurrentMethod] = useState<any>(null)
    const [currentController, setCurrentController] = useState<ControllerModel | null>(null)

    // 选中的参数
    const [selectedParam, setSelectedParam] = useState<ExtendedParamModel | null>(null)
    const [selectedParamIndex, setSelectedParamIndex] = useState<number>(-1)

    // 字段相关状态
    const [selectedFields, setSelectedFields] = useState<FieldData[]>([])
    const [availableFields, setAvailableFields] = useState<any[]>([])
    const [filteredFields, setFilteredFields] = useState<any[]>([])
    const [searchText, setSearchText] = useState('')
    const [loadingFields, setLoadingFields] = useState(false)
    const [filedNames, setFiledNames] = useState<Record<string, any>>({})
    const [fieldFilter, setFieldFilter] = useState<'all' | 'available' | 'selected'>('all') // 字段筛选状态
    const [fieldSort, setFieldSort] = useState<'selected-last' | 'selected-first'>('selected-last')

    // 已选字段搜索
    const [selectedFieldsSearchText, setSelectedFieldsSearchText] = useState('')
    const [filteredSelectedFields, setFilteredSelectedFields] = useState<FieldData[]>([])

    // 多选删除相关状态
    const [selectedRowKeys, setSelectedRowKeys] = useState<string[]>([])

    // 展开/收起状态
    const [expandedKeys, setExpandedKeys] = useState<Set<string>>(new Set())
    const [showDataType, setShowDataType] = useState<boolean>(false) // 默认隐藏字段类型

    // 列宽状态
    const [columnWidths, setColumnWidths] = useState<Record<string, number>>({
        name: 200,
        dataType: 150,
        paramType: 150,
        value: 200,
        required: 80,
        action: 160
    })

    const fieldValidateRef = useRef<any>(null)
    const paramScrollRef = useRef<HTMLDivElement | null>(null)

    // 递归统计字段总数（包括所有嵌套字段）
    const countAllFields = (fields: FieldData[]): number => {
        let count = fields.length
        fields.forEach(field => {
            if (field.parameters && field.parameters.length > 0) {
                count += countAllFields(field.parameters)
            }
        })
        return count
    }

    const paramTypeOptions = [
        {value: 'QUERY', label: 'QUERY'},
        {value: 'BODY', label: 'BODY'},
        {value: 'PATH', label: 'PATH'},
        {value: 'FORM_DATA', label: 'FORM_DATA'},
        {value: 'FORM', label: 'FORM'},
        {value: 'HEADER', label: 'HEADER'},
    ]

    // 根据数据类型返回对应的图标和颜色
    const getFieldIcon = (dataType: string) => {
        const type = dataType?.toLowerCase() || ''

        // 数字类型
        if (type.includes('int') || type.includes('long') || type.includes('short') ||
            type.includes('byte') || type.includes('number') || type.includes('double') ||
            type.includes('float') || type.includes('bigdecimal') || type.includes('biginteger')) {
            return {icon: <NumberOutlined/>, color: '#52c41a'}
        }

        // 布尔类型
        if (type.includes('boolean') || type.includes('bool')) {
            return {icon: <CheckSquareOutlined/>, color: '#fa8c16'}
        }

        // 日期时间类型
        if (type.includes('date') || type.includes('time') || type.includes('localdate') ||
            type.includes('localdatetime') || type.includes('localdatetime') || type.includes('timestamp')) {
            return {icon: <CalendarOutlined/>, color: '#722ed1'}
        }

        // 数组/列表类型
        if (type.includes('list') || type.includes('array') || type.includes('collection') ||
            type.includes('set') || type === '[]') {
            return {icon: <UnorderedListOutlined/>, color: '#13c2c2'}
        }

        // 对象类型
        if (type.includes('object') || type.includes('map') || type === '{}') {
            return {icon: <FileOutlined/>, color: '#eb2f96'}
        }

        // 字符串类型（默认）
        return {icon: <FieldStringOutlined/>, color: '#1890ff'}
    }

    // 递归解析参数，处理$ref引用、children结构和childrenField
    const resolveParameters = (fields: any[]): any[] => {
        if (!fields || !Array.isArray(fields)) return []

        return fields
            .filter(field => !field.$ref) // 过滤掉纯$ref对象
            .map(field => {
                const resolved = {...field}

                // 优先处理 childrenField（新数据格式）
                if (resolved.childrenField && Array.isArray(resolved.childrenField) && resolved.childrenField.length > 0) {
                    resolved.parameters = resolveParameters(resolved.childrenField)
                    // 清理 childrenField，避免重复
                    delete resolved.childrenField
                }
                // 如果parameters包含$ref，但children.parameters有真实数据，使用children的数据
                else if (resolved.parameters && Array.isArray(resolved.parameters)) {
                    const hasRef = resolved.parameters.some((p: any) => p.$ref)
                    if (hasRef && resolved.children?.parameters) {
                        // 使用children.parameters的真实数据
                        resolved.parameters = resolveParameters(resolved.children.parameters)
                    } else if (hasRef && resolved.children?.childrenField) {
                        // 使用children.childrenField的真实数据
                        resolved.parameters = resolveParameters(resolved.children.childrenField)
                    } else {
                        // 递归处理正常的parameters
                        resolved.parameters = resolveParameters(resolved.parameters)
                    }
                }
                // 如果没有parameters但有children.parameters，使用children的数据
                else if (resolved.children?.parameters && Array.isArray(resolved.children.parameters)) {
                    resolved.parameters = resolveParameters(resolved.children.parameters)
                }
                // 如果没有parameters但有children.childrenField，使用children的数据
                else if (resolved.children?.childrenField && Array.isArray(resolved.children.childrenField)) {
                    resolved.parameters = resolveParameters(resolved.children.childrenField)
                }

                return resolved
            })
    }

    // 清理空的parameters属性
    const cleanEmptyParameters = (fields: FieldData[]): FieldData[] => {
        return fields.map(field => {
            const cleaned = {...field}
            if (cleaned.parameters && cleaned.parameters.length === 0) {
                delete cleaned.parameters
            } else if (cleaned.parameters && cleaned.parameters.length > 0) {
                cleaned.parameters = cleanEmptyParameters(cleaned.parameters)
            }
            return cleaned
        })
    }

    // 添加keyName到filedNames
    const addKeyName = (fields: FieldData[], names: Record<string, any> = {}) => {
        if (fields && fields.length > 0) {
            fields.forEach(field => {
                names[field.keyName] = field
                if (field.parameters && field.parameters.length > 0) {
                    addKeyName(field.parameters, names)
                }
            })
        }
        return names
    }

    // 加载可选字段
    const loadAvailableFields = async (className: string, clzName: string, pName: string, methodName: string, paramIndex?: number) => {
        try {
            setLoadingFields(true)
            console.log('🔥🔥🔥 [RequestParamModal] 调用getFields')
            console.log('  - className(第1个参数):', className)
            console.log('  - controllerClassName(第3个参数/clzName):', clzName)
            console.log('  - methodName(第4个参数):', methodName)
            console.log('  - parameterIndex(第5个参数):', paramIndex)
            const fields = await getFields(className, [], clzName, methodName, paramIndex)

            const processFields = (fieldList: any[], parentKey = ''): any[] => {
                return fieldList.map(field => {
                    const key = parentKey ? `${parentKey}.${field.name}` : field.name
                    const separator = pName.endsWith('.') ? '' : '.'
                    const fieldKeyName = pName + separator + key

                    return {
                        ...field,
                        id: key,
                        keyName: fieldKeyName,
                        childrenField: field.childrenField && field.childrenField.length > 0
                            ? processFields(field.childrenField, key)
                            : undefined
                    }
                })
            }

            const processedFields = processFields(fields)
            setAvailableFields(processedFields)

            console.log('✅ loadAvailableFields 完成，字段数量:', processedFields.length)

            // 返回处理后的字段，供外部使用
            return processedFields
        } catch (error: any) {
            message.error('加载字段失败：' + error.message)
            return []
        } finally {
            setLoadingFields(false)
        }
    }

    useImperativeHandle(ref, () => ({
        open: async (controller: ControllerModel, method: MethodModel) => {
            setVisible(true)
            setLoading(true)
            setCurrentController(controller)

            try {
                // 使用getParamList获取完整的方法信息，与Vue代码一致
                const api = await getParamList(controller.name, method.name)
                api.className = controller.name
                setCurrentMethod(api)

                // 过滤掉$ref和Page参数
                if (api.request == null) {
                    api.request = []
                }
                const filteredRequest = api.request.filter(
                    (i: ParamModel) => !i.$ref && i.name !== 'com.csap.mybatisplus.page.Page'
                )
                setRequestParams(filteredRequest)

                // 自动选中第一个参数
                if (filteredRequest.length > 0) {
                    const firstParam = filteredRequest[0]
                    setSelectedParam(firstParam)
                    setSelectedParamIndex(0)

                    // 加载第一个参数的字段
                    const paramName = api.paramNames?.[0] || firstParam.methodParamName
                    // 解析参数结构，处理$ref和children
                    const resolvedData = resolveParameters(firstParam.parameters || [])
                    const cleanedData = cleanEmptyParameters(resolvedData)
                    setSelectedFields(cleanedData)

                    // 更新 filedNames
                    const names: Record<string, any> = {}
                    addKeyName(cleanedData, names)

                    // 加载可选字段（传递方法名和参数索引 0）
                    const loadedFields = await loadAvailableFields(firstParam.name, api.className, paramName, api.name, 0)

                    console.log('📊 loadedFields 返回数量:', loadedFields?.length || 0)

                    // 设置 filedNames
                    setFiledNames(names)

                    // 立即应用排序（使用加载返回的字段）
                    applyFilters('', 'all', 'selected-last', names, loadedFields || [])
                }
            } catch (error: any) {
                message.error('加载请求参数失败：' + error.message)
            } finally {
                setLoading(false)
            }
        }
    }))

    const handleClose = () => {
        setVisible(false)
        setRequestParams([])
        setCurrentMethod(null)
        setSelectedParam(null)
        setSelectedParamIndex(-1)
        setSelectedFields([])
        setAvailableFields([])
        setFilteredFields([])
        setSearchText('')
        setFiledNames({})
        setFieldFilter('all')
        setFieldSort('selected-last')
        setSelectedFieldsSearchText('')
        setFilteredSelectedFields([])
        setSelectedRowKeys([])
        setShowDataType(false)
        // 注意：不清除编辑状态，保留高亮状态，直到用户点击其他接口
    }

    // 点击参数行，加载字段详情
    const handleParamClick = async (record: ExtendedParamModel, index: number) => {
        setSelectedParam(record)
        setSelectedParamIndex(index)

        // 加载字段
        const paramName = currentMethod.paramNames?.[index] || record.methodParamName
        // 解析参数结构，处理$ref和children
        const resolvedData = resolveParameters(record.parameters || [])
        const cleanedData = cleanEmptyParameters(resolvedData)
        setSelectedFields(cleanedData)

        // 更新 filedNames
        const names: Record<string, any> = {}
        addKeyName(cleanedData, names)

        // 加载可选字段（传递方法名和当前参数的索引）
        const loadedFields = await loadAvailableFields(record.name, currentMethod.className, paramName, currentMethod.name, index)

        console.log('📊 handleParamClick loadedFields 返回数量:', loadedFields?.length || 0)

        // 设置 filedNames
        setFiledNames(names)

        // 立即应用排序（使用加载返回的字段）
        applyFilters('', 'all', 'selected-last', names, loadedFields || [])
    }

    // 应用搜索和筛选（支持传入自定义的 filedNames 和字段列表）
    const applyFilters = (
        searchValue: string,
        filterType: 'all' | 'available' | 'selected',
        sortType?: 'selected-last' | 'selected-first',
        customFiledNames?: Record<string, any>,
        customFields?: any[]
    ) => {
        const names = customFiledNames || filedNames
        const fields = customFields || availableFields
        let result = [...fields]

        // 应用筛选条件
        if (filterType === 'available') {
            result = result.filter(field => !names[field.keyName])
        } else if (filterType === 'selected') {
            result = result.filter(field => names[field.keyName])
        }

        // 应用搜索条件
        if (searchValue.trim()) {
            const searchLower = searchValue.toLowerCase()
            result = result.filter(field => {
                const nameMatch = field.name.toLowerCase().includes(searchLower)
                const typeMatch = field.dataType?.toLowerCase().includes(searchLower)
                const valueMatch = field.value?.toLowerCase().includes(searchLower)
                return nameMatch || typeMatch || valueMatch
            })
        }

        // 应用排序：已选字段排到最后或最前
        const currentSort = sortType || fieldSort
        result.sort((a, b) => {
            const aSelected = !!names[a.keyName]
            const bSelected = !!names[b.keyName]

            if (aSelected === bSelected) return 0 // 相同状态保持原顺序

            if (currentSort === 'selected-last') {
                return aSelected ? 1 : -1 // 已选的排后面
            } else {
                return aSelected ? -1 : 1 // 已选的排前面
            }
        })

        setFilteredFields(result)
    }

    // 递归过滤已选字段
    const filterSelectedFields = (fields: FieldData[], searchValue: string): FieldData[] => {
        if (!searchValue.trim()) {
            return fields
        }

        const searchLower = searchValue.toLowerCase()
        const result: FieldData[] = []

        for (const field of fields) {
            const nameMatch = field.name.toLowerCase().includes(searchLower)
            const typeMatch = field.dataType?.toLowerCase().includes(searchLower)
            const valueMatch = field.value?.toLowerCase().includes(searchLower)
            const paramTypeMatch = field.paramType?.toLowerCase().includes(searchLower)

            // 递归检查子字段
            let matchedChildren: FieldData[] = []
            if (field.parameters && field.parameters.length > 0) {
                matchedChildren = filterSelectedFields(field.parameters, searchValue)
            }

            // 如果当前字段匹配或有匹配的子字段，则包含该字段
            if (nameMatch || typeMatch || valueMatch || paramTypeMatch || matchedChildren.length > 0) {
                const filteredField = {...field}
                if (matchedChildren.length > 0) {
                    filteredField.parameters = matchedChildren
                }
                result.push(filteredField)
            }
        }

        return result
    }

    // 应用已选字段搜索
    const applySelectedFieldsSearch = (searchValue: string) => {
        const filtered = filterSelectedFields(selectedFields, searchValue)
        setFilteredSelectedFields(filtered)
    }

    // 搜索已选字段
    const handleSelectedFieldsSearch = (value: string) => {
        setSelectedFieldsSearchText(value)
        applySelectedFieldsSearch(value)
    }

    // 搜索字段
    const handleFieldSearch = (value: string) => {
        setSearchText(value)
        applyFilters(value, fieldFilter)
    }

    // 筛选字段
    const handleFieldFilterChange = (value: 'all' | 'available' | 'selected') => {
        setFieldFilter(value)
        applyFilters(searchText, value)
    }

    // 排序字段
    const handleFieldSortChange = (value: 'selected-last' | 'selected-first') => {
        setFieldSort(value)
        applyFilters(searchText, fieldFilter, value)
    }

    // 递归转换 childrenField 为 parameters 结构
    const convertChildrenToParameters = (field: any): FieldData => {
        const newField: FieldData = {
            name: field.name,
            dataType: field.dataType,
            key: field.name,
            keyName: field.keyName,
            value: field.value,
            required: false,
            paramType: currentMethod?.paramType || 'QUERY',
            validate: []
        }

        // 递归处理子字段
        if (field.childrenField && field.childrenField.length > 0) {
            newField.parameters = field.childrenField.map((child: any) => convertChildrenToParameters(child))
        }

        return newField
    }

    // 检查字段的选择状态：'all'(全选), 'partial'(半选), 'none'(未选)
    const getFieldSelectionStatus = (field: any): 'all' | 'partial' | 'none' => {
        const isFieldSelected = !!filedNames[field.keyName]

        if (!field.childrenField || field.childrenField.length === 0) {
            return isFieldSelected ? 'all' : 'none'
        }

        // 检查子字段的选择情况
        const selectedChildrenCount = field.childrenField.filter((child: any) =>
            filedNames[child.keyName]
        ).length

        if (selectedChildrenCount === 0 && !isFieldSelected) {
            return 'none'
        } else if (selectedChildrenCount === field.childrenField.length && isFieldSelected) {
            return 'all'
        } else {
            return 'partial'
        }
    }

    // 处理字段复选框切换（添加或删除）
    const handleFieldCheckboxToggle = (field: any, checked: boolean, isPartial?: boolean) => {
        // 如果是半选状态，点击后应该全选（添加所有子字段）
        if (isPartial) {
            // 半选状态：添加所有未选中的子字段
            handleAddField(field)
            return
        }

        if (checked) {
            // 勾选：添加字段
            handleAddField(field)
        } else {
            // 取消勾选：删除字段（如果已添加）
            const fieldToDelete = findFieldByKeyName(selectedFields, field.keyName)
            if (fieldToDelete) {
                handleDeleteFieldByKeyName(field.keyName, field.name)
            }
        }
    }

    // 通过 keyName 删除字段（用于复选框取消勾选）
    const handleDeleteFieldByKeyName = (keyName: string, fieldName?: string) => {
        console.log('=== handleDeleteFieldByKeyName ===')
        console.log('删除字段 keyName:', keyName)

        // 先找到要删除的字段（在删除前获取信息）
        const fieldToDelete = findFieldByKeyName(selectedFields, keyName)
        if (!fieldToDelete) {
            console.warn('未找到要删除的字段:', keyName)
            return
        }

        // 使用 keyName 递归删除字段
        const newFields = removeFieldByKeyName([...selectedFields], keyName)

        // 重新构建 filedNames，只包含实际存在于 newFields 中的字段
        const names: Record<string, any> = {}
        const rebuildNames = (fields: FieldData[]) => {
            fields.forEach(field => {
                names[field.keyName] = field
                if (field.parameters && field.parameters.length > 0) {
                    rebuildNames(field.parameters)
                }
            })
        }
        rebuildNames(newFields)

        console.log('删除后 filedNames 剩余 keys:', Object.keys(names).length)

        // 更新状态
        setSelectedFields(newFields)
        setFiledNames(names)

        // 清除选中状态
        setSelectedRowKeys(prev => prev.filter(key => key !== keyName))

        // 使用 setTimeout 确保状态更新后再刷新显示
        setTimeout(() => {
            applyFilters(searchText, fieldFilter, fieldSort, names)
            applySelectedFieldsSearch(selectedFieldsSearchText)
        }, 0)

        message.success(`已删除字段 "${fieldName || fieldToDelete.name}"${fieldToDelete.parameters?.length ? ` 及其 ${fieldToDelete.parameters.length} 个子字段` : ''}`)
    }

    // 添加字段（支持单独添加子字段）
    const handleAddField = (field: any, availableFieldsMap?: Map<string, any>) => {
        if (filedNames[field.keyName]) {
            message.warning(`字段 "${field.name}" 已存在`)
            return
        }

        // 如果没有传入 availableFieldsMap，从 availableFields 构建
        if (!availableFieldsMap) {
            availableFieldsMap = new Map()
            const buildMap = (fields: any[]) => {
                fields.forEach(f => {
                    availableFieldsMap!.set(f.keyName, f)
                    if (f.childrenField && f.childrenField.length > 0) {
                        buildMap(f.childrenField)
                    }
                })
            }
            buildMap(availableFields)
        }

        // 查找或创建父字段路径
        const parts = field.keyName.split('.')
        const newFields = [...selectedFields]
        const names = {...filedNames}

        // 检查父字段路径
        const parentKeyName = parts.slice(0, -1).join('.')

        // 判断父字段是否真实存在（不是前缀）
        const parentExistsInSelected = !!filedNames[parentKeyName]
        const parentExistsInAvailable = availableFieldsMap.has(parentKeyName)

        // 如果父字段在可选字段中但不在已选字段中，需要创建父字段路径
        if (parentExistsInAvailable && !parentExistsInSelected) {
            // 父字段不存在，需要创建整个路径
            const pathToCreate: { field: FieldData; index: number }[] = []

            for (let i = 1; i <= parts.length - 1; i++) {
                const currentKeyName = parts.slice(0, i).join('.')
                if (!filedNames[currentKeyName]) {
                    const fieldDef = availableFieldsMap.get(currentKeyName)
                    if (fieldDef) {
                        const newParent: FieldData = {
                            name: fieldDef.name,
                            dataType: fieldDef.dataType,
                            key: fieldDef.name,
                            keyName: currentKeyName,
                            value: fieldDef.value,
                            required: false,
                            paramType: currentMethod?.paramType || 'QUERY',
                            validate: [],
                            parameters: []
                        }
                        pathToCreate.push({field: newParent, index: i})
                        names[currentKeyName] = newParent
                    }
                }
            }

            // 将路径添加到 selectedFields
            if (pathToCreate.length > 0) {
                // 递归构建嵌套结构
                const buildNestedStructure = (index: number): FieldData => {
                    const current = pathToCreate[index].field
                    if (index < pathToCreate.length - 1) {
                        current.parameters = [buildNestedStructure(index + 1)]
                    }
                    return current
                }

                const nestedStructure = buildNestedStructure(0)
                const firstMissingIndex = pathToCreate[0].index

                // 找到应该挂载的位置
                const existingParentKeyName = parts.slice(0, firstMissingIndex - 1).join('.')
                if (existingParentKeyName && filedNames[existingParentKeyName]) {
                    // 挂载到已存在的父节点下
                    const addToExistingParent = (fields: FieldData[], parentKey: string, child: FieldData): boolean => {
                        for (const f of fields) {
                            if (f.keyName === parentKey) {
                                if (!f.parameters) f.parameters = []
                                f.parameters.push(child)
                                return true
                            }
                            if (f.parameters && f.parameters.length > 0) {
                                if (addToExistingParent(f.parameters, parentKey, child)) return true
                            }
                        }
                        return false
                    }
                    addToExistingParent(newFields, existingParentKeyName, nestedStructure)
                } else {
                    // 添加到根级别
                    newFields.push(nestedStructure)
                }
            }
        }

        // 创建当前字段
        const newField: FieldData = {
            name: field.name,
            dataType: field.dataType,
            key: field.name,
            keyName: field.keyName,
            value: field.value,
            required: false,
            paramType: currentMethod?.paramType || 'QUERY',
            validate: []
        }

        // 如果字段有子字段，转换它们
        if (field.childrenField && field.childrenField.length > 0) {
            newField.parameters = field.childrenField.map((child: any) => convertChildrenToParameters(child))
        }

        // 添加当前字段到正确的位置
        // 重新检查父字段是否存在（使用更新后的 names，而不是旧的 filedNames）
        const parentNowExists = !!names[parentKeyName]

        if (parentNowExists && parentKeyName) {
            // 父字段存在（可能是原本就存在，也可能是刚刚创建的），添加到父字段下
            const addChildToParent = (fields: FieldData[], parentKey: string, child: FieldData): boolean => {
                for (const f of fields) {
                    if (f.keyName === parentKey) {
                        if (!f.parameters) f.parameters = []
                        f.parameters.push(child)
                        return true
                    }
                    if (f.parameters && f.parameters.length > 0) {
                        if (addChildToParent(f.parameters, parentKey, child)) {
                            return true
                        }
                    }
                }
                return false
            }
            const added = addChildToParent(newFields, parentKeyName, newField)
            if (!added) {
                // 如果没有成功添加到父字段下，输出警告并添加到根级别
                console.warn(`⚠️ 无法将字段 "${field.name}" 添加到父字段 "${parentKeyName}" 下，将添加到根级别`)
                newFields.push(newField)
            }
        } else {
            // 父字段不存在，添加到根级别
            newFields.push(newField)
        }

        names[field.keyName] = newField

        // 如果有子字段，也添加到 names
        if (newField.parameters && newField.parameters.length > 0) {
            const addToNames = (f: FieldData) => {
                names[f.keyName] = f
                if (f.parameters && f.parameters.length > 0) {
                    f.parameters.forEach(child => addToNames(child))
                }
            }
            newField.parameters.forEach(child => addToNames(child))
        }

        setSelectedFields(newFields)
        setFiledNames(names)

        // 使用新的 names 重新应用筛选和排序
        applyFilters(searchText, fieldFilter, fieldSort, names)
        setTimeout(() => applySelectedFieldsSearch(selectedFieldsSearchText), 0)

        message.success(`已添加字段 "${field.name}"`)
    }

    // 全选添加（添加所有可选字段中未选择的字段）
    const handleAddAllFields = () => {
        // 递归收集所有可选字段（从 availableFields）
        const collectAllFields = (fields: any[]): any[] => {
            const result: any[] = []
            fields.forEach(field => {
                if (!filedNames[field.keyName]) {
                    result.push(field)
                }
                if (field.childrenField && field.childrenField.length > 0) {
                    result.push(...collectAllFields(field.childrenField))
                }
            })
            return result
        }

        // 从所有可用字段中收集未添加的字段
        const availableFieldsToAdd = collectAllFields(availableFields)

        if (availableFieldsToAdd.length === 0) {
            message.warning('没有可添加的字段')
            return
        }

        // 构建 availableFieldsMap 用于查找父字段定义
        const availableFieldsMap = new Map()
        const buildMap = (fields: any[]) => {
            fields.forEach(f => {
                availableFieldsMap.set(f.keyName, f)
                if (f.childrenField && f.childrenField.length > 0) {
                    buildMap(f.childrenField)
                }
            })
        }
        buildMap(availableFields)

        // 按层级深度排序：先添加父字段，再添加子字段
        availableFieldsToAdd.sort((a, b) => {
            const depthA = a.keyName.split('.').length
            const depthB = b.keyName.split('.').length
            return depthA - depthB
        })

        console.log('=== handleAddAllFields ===')
        console.log('要添加的字段数量:', availableFieldsToAdd.length)

        // 逐个添加字段，使用和 handleAddField 相同的逻辑
        const currentFields = [...selectedFields]
        const currentNames = {...filedNames}

        availableFieldsToAdd.forEach(field => {
            const parts = field.keyName.split('.')
            const parentKeyName = parts.slice(0, -1).join('.')

            // 判断父字段是否真实存在（不是前缀）
            const parentExistsInSelected = !!currentNames[parentKeyName]
            const parentExistsInAvailable = availableFieldsMap.has(parentKeyName)

            // 如果父字段在可选字段中但不在已选字段中，需要创建父字段路径
            if (parentExistsInAvailable && !parentExistsInSelected) {
                // 创建父字段路径
                const pathToCreate: { field: FieldData; index: number }[] = []

                for (let i = 1; i <= parts.length - 1; i++) {
                    const currentKeyName = parts.slice(0, i).join('.')
                    if (!currentNames[currentKeyName]) {
                        const fieldDef = availableFieldsMap.get(currentKeyName)
                        if (fieldDef) {
                            const newParent: FieldData = {
                                name: fieldDef.name,
                                dataType: fieldDef.dataType,
                                key: fieldDef.name,
                                keyName: currentKeyName,
                                value: fieldDef.value,
                                required: false,
                                paramType: currentMethod?.paramType || 'QUERY',
                                validate: [],
                                parameters: []
                            }
                            pathToCreate.push({field: newParent, index: i})
                            currentNames[currentKeyName] = newParent
                        }
                    }
                }

                // 构建嵌套结构
                if (pathToCreate.length > 0) {
                    const buildNested = (index: number): FieldData => {
                        const current = pathToCreate[index].field
                        if (index < pathToCreate.length - 1) {
                            current.parameters = [buildNested(index + 1)]
                        }
                        return current
                    }

                    const nestedStructure = buildNested(0)
                    const firstMissingIndex = pathToCreate[0].index

                    // 找到应该挂载的位置
                    const existingParentKeyName = parts.slice(0, firstMissingIndex - 1).join('.')
                    if (existingParentKeyName && currentNames[existingParentKeyName]) {
                        // 挂载到已存在的父节点下
                        const addToExistingParent = (fields: FieldData[], parentKey: string, child: FieldData): boolean => {
                            for (const f of fields) {
                                if (f.keyName === parentKey) {
                                    if (!f.parameters) f.parameters = []
                                    f.parameters.push(child)
                                    return true
                                }
                                if (f.parameters && f.parameters.length > 0) {
                                    if (addToExistingParent(f.parameters, parentKey, child)) return true
                                }
                            }
                            return false
                        }
                        addToExistingParent(currentFields, existingParentKeyName, nestedStructure)
                    } else {
                        // 添加到根级别
                        currentFields.push(nestedStructure)
                    }

                    // 将路径中的所有字段添加到 names
                    pathToCreate.forEach(p => {
                        currentNames[p.field.keyName] = p.field
                    })
                }
            }

            // 创建当前字段
            const newField: FieldData = {
                name: field.name,
                dataType: field.dataType,
                key: field.name,
                keyName: field.keyName,
                value: field.value,
                required: false,
                paramType: currentMethod?.paramType || 'QUERY',
                validate: []
            }

            // 如果字段有子字段，转换它们
            if (field.childrenField && field.childrenField.length > 0) {
                newField.parameters = field.childrenField.map((child: any) => convertChildrenToParameters(child))
            }

            // 添加当前字段到正确的位置
            // 重新检查父字段是否存在（使用更新后的 currentNames）
            const parentNowExists = !!currentNames[parentKeyName]

            if (parentNowExists && parentKeyName) {
                // 父字段存在（可能是原本就存在，也可能是刚刚创建的），添加到父字段下
                const addChildToParent = (fields: FieldData[], parentKey: string, child: FieldData): boolean => {
                    for (const f of fields) {
                        if (f.keyName === parentKey) {
                            if (!f.parameters) f.parameters = []
                            f.parameters.push(child)
                            return true
                        }
                        if (f.parameters && f.parameters.length > 0) {
                            if (addChildToParent(f.parameters, parentKey, child)) {
                                return true
                            }
                        }
                    }
                    return false
                }
                const added = addChildToParent(currentFields, parentKeyName, newField)
                if (!added) {
                    // 如果没有成功添加到父字段下，输出警告并添加到根级别
                    console.warn(`⚠️ 无法将字段 "${field.name}" 添加到父字段 "${parentKeyName}" 下，将添加到根级别`)
                    currentFields.push(newField)
                }
            } else {
                // 父字段不存在，添加到根级别
                currentFields.push(newField)
            }

            currentNames[field.keyName] = newField

            // 如果有子字段，也添加到 currentNames
            if (newField.parameters && newField.parameters.length > 0) {
                const addToNames = (f: FieldData) => {
                    currentNames[f.keyName] = f
                    if (f.parameters && f.parameters.length > 0) {
                        f.parameters.forEach(child => addToNames(child))
                    }
                }
                newField.parameters.forEach(child => addToNames(child))
            }
        })

        setSelectedFields(currentFields)
        setFiledNames(currentNames)

        console.log('更新后 filedNames 包含的 keys:', Object.keys(currentNames).length)

        // 使用新的 names 重新应用筛选和排序
        applyFilters(searchText, fieldFilter, fieldSort, currentNames)
        setTimeout(() => applySelectedFieldsSearch(selectedFieldsSearchText), 0)

        message.success(`已添加 ${availableFieldsToAdd.length} 个字段`)
    }

    // 递归删除字段及其所有子字段从 filedNames 中移除
    const _removeFromFiledNames = (field: FieldData, names: Record<string, any>) => {
        delete names[field.keyName]
        if (field.parameters && field.parameters.length > 0) {
            field.parameters.forEach(child => _removeFromFiledNames(child, names))
        }
    }

    // 递归查找并删除字段
    const removeFieldByKeyName = (fields: FieldData[], keyName: string): FieldData[] => {
        return fields.filter(field => {
            if (field.keyName === keyName) {
                return false // 删除匹配的字段
            }
            // 递归处理子字段
            if (field.parameters && field.parameters.length > 0) {
                field.parameters = removeFieldByKeyName(field.parameters, keyName)
                // 如果删除子字段后，父字段的 parameters 为空，也删除父字段（自动清理空父字段）
                if (field.parameters.length === 0) {
                    return false
                }
            }
            return true
        })
    }

    // 删除字段
    const handleDeleteField = (record: FieldData, _index: number) => {
        console.log('=== handleDeleteField ===')
        console.log('删除字段 keyName:', record.keyName)
        console.log('删除字段 name:', record.name)

        // 使用 keyName 递归删除字段
        const newFields = removeFieldByKeyName([...selectedFields], record.keyName)

        // 重新构建 filedNames，只包含实际存在于 newFields 中的字段
        const names: Record<string, any> = {}
        const rebuildNames = (fields: FieldData[]) => {
            fields.forEach(field => {
                names[field.keyName] = field
                if (field.parameters && field.parameters.length > 0) {
                    rebuildNames(field.parameters)
                }
            })
        }
        rebuildNames(newFields)

        console.log('删除后 filedNames 剩余 keys:', Object.keys(names).length)
        console.log('删除后 filedNames keys:', Object.keys(names))

        // 更新状态
        setSelectedFields(newFields)
        setFiledNames(names)

        // 清除选中状态
        setSelectedRowKeys(prev => prev.filter(key => key !== record.keyName))

        // 使用 setTimeout 确保状态更新后再刷新显示
        setTimeout(() => {
            applyFilters(searchText, fieldFilter, fieldSort, names)
            applySelectedFieldsSearch(selectedFieldsSearchText)
        }, 0)

        message.success(`已删除字段 "${record.name}"${record.parameters?.length ? ` 及其 ${record.parameters.length} 个子字段` : ''}`)
    }

    // 递归查找字段（包括嵌套字段）
    const findFieldByKeyName = (fields: FieldData[], keyName: string): FieldData | null => {
        for (const field of fields) {
            if (field.keyName === keyName) {
                return field
            }
            if (field.parameters && field.parameters.length > 0) {
                const found = findFieldByKeyName(field.parameters, keyName)
                if (found) return found
            }
        }
        return null
    }

    // 递归渲染字段树（多行布局，避免内容溢出）
    const renderFieldTree = (fields: any[], level: number): React.ReactNode => {
        return fields.map((field) => {
            const selectionStatus = getFieldSelectionStatus(field)
            const isAdded = selectionStatus === 'all'
            const isPartial = selectionStatus === 'partial'
            const hasChildren = field.childrenField && field.childrenField.length > 0
            const isExpanded = expandedKeys.has(field.keyName)
            const {icon, color} = getFieldIcon(field.dataType)

            // 确定边框和背景颜色
            let borderColor = '#e8e8e8'
            let background = '#fff'
            let opacity = 1

            if (isAdded) {
                borderColor = '#52c41a'
                background = '#f6ffed'
                opacity = 0.7
            } else if (isPartial) {
                borderColor = '#faad14'
                background = '#fffbe6'
                opacity = 0.85
            }

            return (
                <div key={field.keyName} style={{marginLeft: level * 16}}>
                    <div
                        style={{
                            padding: '10px',
                            marginBottom: '8px',
                            border: `1px solid ${borderColor}`,
                            borderRadius: '6px',
                            background,
                            cursor: 'pointer',
                            transition: 'all 0.2s',
                            opacity,
                            boxShadow: (isAdded || isPartial) ? 'none' : '0 1px 3px rgba(0,0,0,0.05)'
                        }}
                        onClick={(e) => {
                            e.stopPropagation()
                            if (!isAdded && !isPartial) {
                                handleAddField(field)
                            }
                        }}
                    >
                        {/* 多行布局 */}
                        <div style={{display: 'flex', flexDirection: 'column', gap: 6}}>
                            {/* 第一行：操作图标 + 复选框 + 字段名 + 备注 */}
                            <div style={{display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8, minWidth: 0}}>
                                <div style={{display: 'flex', alignItems: 'center', gap: 8, minWidth: 0, flex: 1, overflow: 'hidden'}}>
                                    {hasChildren && (
                                        <span
                                            onClick={(e) => {
                                                e.stopPropagation()
                                                const newExpandedKeys = new Set(expandedKeys)
                                                if (isExpanded) {
                                                    newExpandedKeys.delete(field.keyName)
                                                } else {
                                                    newExpandedKeys.add(field.keyName)
                                                }
                                                setExpandedKeys(newExpandedKeys)
                                            }}
                                            style={{
                                                cursor: 'pointer',
                                                fontSize: 12,
                                                color: '#666',
                                                width: 12,
                                                display: 'inline-block',
                                                flexShrink: 0
                                            }}
                                        >
                      <RightOutlined style={{
                          transition: 'transform 0.2s',
                          transform: isExpanded ? 'rotate(90deg)' : 'rotate(0deg)'
                      }}/>
                    </span>
                                    )}
                                    {!hasChildren && <span style={{width: 12, display: 'inline-block', flexShrink: 0}}/>}

                                    <Checkbox
                                        checked={isAdded}
                                        indeterminate={isPartial}
                                        onChange={(e) => {
                                            e.stopPropagation()
                                            const checked = e.target.checked
                                            // 如果当前是半选状态，无论点击什么，都应该全选（添加所有子字段）
                                            if (isPartial) {
                                                handleFieldCheckboxToggle(field, true, isPartial)
                                            } else {
                                                // 非半选状态：正常处理勾选/取消勾选
                                                handleFieldCheckboxToggle(field, checked, isPartial)
                                            }
                                        }}
                                        onClick={(e) => {
                                            e.stopPropagation()
                                        }}
                                        style={{flexShrink: 0, cursor: 'pointer'}}
                                    />

                                    <span style={{color, fontSize: 14, flexShrink: 0}}>{icon}</span>

                                    <div style={{display: 'flex', alignItems: 'center', gap: 6, minWidth: 0, flex: 1, overflow: 'hidden'}}>
                                        <Tooltip title={field.name}>
                                            <span style={{fontWeight: 600, fontSize: 14, color: '#333', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis'}}>
                                                {field.name}
                                            </span>
                                        </Tooltip>
                                        {field.value && (
                                            <Tooltip title={field.value}>
                                                <span style={{
                                                    fontSize: 12,
                                                    color: '#999',
                                                    whiteSpace: 'nowrap',
                                                    overflow: 'hidden',
                                                    textOverflow: 'ellipsis',
                                                    flexShrink: 1,
                                                    maxWidth: '60%'
                                                }}>
                                                    {field.value}
                                                </span>
                                            </Tooltip>
                                        )}
                                    </div>
                                </div>

                                <div style={{display: 'flex', alignItems: 'center', gap: 6, flexShrink: 0}}>
                                    {/* 移除右侧状态文字显示 */}
                                </div>
                            </div>

                            {/* 第二行：类型标签（根据showDataType决定是否显示） */}
                            {showDataType && (
                                <div style={{paddingLeft: 52, display: 'flex', alignItems: 'center'}}>
                                    <Tooltip title={field.dataType}>
                                        <Tag
                                            color={field.dataType?.includes('List') || field.dataType?.includes('[]') ? 'blue' : 'default'}
                                            style={{
                                                fontSize: '11px',
                                                padding: '2px 8px',
                                                lineHeight: '18px',
                                                borderRadius: '3px',
                                                maxWidth: '100%',
                                                overflow: 'hidden',
                                                textOverflow: 'ellipsis',
                                                whiteSpace: 'nowrap'
                                            }}
                                        >
                                            {field.dataType || 'unknown'}
                                        </Tag>
                                    </Tooltip>
                                </div>
                            )}
                        </div>
                    </div>
                    {hasChildren && isExpanded && renderFieldTree(field.childrenField, level + 1)}
                </div>
            )
        })
    }

    // 批量删除选中的字段
    const handleBatchDelete = () => {
        if (selectedRowKeys.length === 0) {
            message.warning('请先选择要删除的字段')
            return
        }

        console.log('=== handleBatchDelete ===')
        console.log('选中的 keyNames:', selectedRowKeys)

        // 递归收集所有要删除的字段（包括嵌套字段）
        const fieldsToDelete: FieldData[] = []
        selectedRowKeys.forEach(keyName => {
            const field = findFieldByKeyName(selectedFields, keyName)
            if (field) {
                fieldsToDelete.push(field)
            }
        })

        console.log('找到要删除的字段数量:', fieldsToDelete.length)

        if (fieldsToDelete.length === 0) {
            message.warning('未找到要删除的字段')
            return
        }

        // 使用跟单个删除完全一样的逻辑
        let newFields = [...selectedFields]

        // 对每个要删除的字段，执行删除操作
        fieldsToDelete.forEach(field => {
            newFields = removeFieldByKeyName(newFields, field.keyName)
        })

        // 重新构建 filedNames，只包含实际存在于 newFields 中的字段
        const names: Record<string, any> = {}
        const rebuildNames = (fields: FieldData[]) => {
            fields.forEach(field => {
                names[field.keyName] = field
                if (field.parameters && field.parameters.length > 0) {
                    rebuildNames(field.parameters)
                }
            })
        }
        rebuildNames(newFields)

        console.log('批量删除后 filedNames 剩余 keys:', Object.keys(names).length)

        // 更新状态
        setSelectedFields(newFields)
        setFiledNames(names)

        // 清空选中状态
        setSelectedRowKeys([])

        // 刷新显示
        setTimeout(() => {
            applyFilters(searchText, fieldFilter, fieldSort, names)
            applySelectedFieldsSearch(selectedFieldsSearchText)
        }, 0)

        message.success(`已删除 ${fieldsToDelete.length} 个字段`)
    }

    // 全部删除
    const handleDeleteAll = () => {
        if (selectedFields.length === 0) {
            message.warning('没有可删除的字段')
            return
        }

        console.log('=== handleDeleteAll ===')

        setSelectedFields([])
        setFiledNames({})
        setSelectedRowKeys([])

        // 使用 setTimeout 确保状态更新后再刷新显示
        setTimeout(() => {
            applyFilters(searchText, fieldFilter, fieldSort, {})
            applySelectedFieldsSearch(selectedFieldsSearchText)
        }, 0)

        message.success('已清空所有字段')
    }

    // 修改字段属性
    const handleParamTypeChange = (value: string, record: FieldData) => {
        record.paramType = value
        setSelectedFields([...selectedFields])
    }

    const handleRequiredChange = (checked: boolean, record: FieldData) => {
        record.required = checked
        setSelectedFields([...selectedFields])
    }

    const handleFieldValidate = (record: FieldData) => {
        if (!record.validate) {
            record.validate = []
        }
        fieldValidateRef.current?.open(record, currentMethod)
    }

    // 保存字段配置
    const handleSaveFields = async () => {
        if (!selectedParam) return

        try {
            const paramName = currentMethod.paramNames?.[selectedParamIndex] || selectedParam.methodParamName
            await addRequestParam({
                methodName: currentMethod.name,
                className: currentMethod.className,
                methodParamName: paramName,
                requestParams: [{
                    packageName: selectedParam.name,
                    modelType: selectedParam.modelType,
                    methodParamName: selectedParam.methodParamName,
                    fields: selectedFields
                }]
            })
            message.success('保存成功')

            // 更新参数的 parameters
            selectedParam.parameters = selectedFields as any
        } catch (error: any) {
            message.error('保存失败：' + error.message)
        }
    }


    // 列宽调整处理
    const handleResize = (key: string) => (_: React.SyntheticEvent, {size}: ResizeCallbackData) => {
        setColumnWidths((prev) => ({
            ...prev,
            [key]: size.width
        }))
    }

    // 右侧字段配置的列配置
    const fieldColumns: ColumnsType<FieldData> = [
        {
            title: '字段名称',
            dataIndex: 'name',
            key: 'name',
            width: columnWidths.name,
            ellipsis: {
                showTitle: false
            },
            onHeaderCell: () => ({
                width: columnWidths.name,
                onResize: handleResize('name'),
            }),
            render: (text, record) => {
                const {icon, color} = getFieldIcon(record.dataType)
                return (
                    <Tooltip title={text}>
                        <Space>
                            <span style={{color}}>{icon}</span>
                            <span style={{fontWeight: 500, fontSize: '13px'}}>{text}</span>
                        </Space>
                    </Tooltip>
                )
            },
        },
        {
            title: '类型',
            dataIndex: 'dataType',
            key: 'dataType',
            width: columnWidths.dataType,
            ellipsis: {
                showTitle: false
            },
            onHeaderCell: () => ({
                width: columnWidths.dataType,
                onResize: handleResize('dataType'),
            }),
            render: (text) => (
                <Tooltip title={text}>
                    <Tag color="cyan" style={{
                        borderRadius: '4px',
                        fontSize: '11px',
                        maxWidth: '100%',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis'
                    }}>
                        {text}
                    </Tag>
                </Tooltip>
            ),
        },
        {
            title: '请求类型',
            key: 'paramType',
            width: columnWidths.paramType,
            onHeaderCell: () => ({
                width: columnWidths.paramType,
                onResize: handleResize('paramType'),
            }),
            render: (_, record) => (
                <Select
                    value={record.paramType}
                    style={{width: '100%'}}
                    size="small"
                    onChange={(value) => handleParamTypeChange(value, record)}
                >
                    {paramTypeOptions.map(opt => (
                        <Option key={opt.value} value={opt.value}>{opt.label}</Option>
                    ))}
                </Select>
            ),
        },
        {
            title: '备注',
            dataIndex: 'value',
            key: 'value',
            width: columnWidths.value,
            ellipsis: true,
            onHeaderCell: () => ({
                width: columnWidths.value,
                onResize: handleResize('value'),
            }),
            render: (text) => (
                <Tooltip title={text}>
                <span style={{color: text ? '#666' : '#ccc', fontSize: '12px'}}>
          {text || '无'}
        </span>
                </Tooltip>
            ),
        },
        {
            title: '必传',
            key: 'required',
            width: columnWidths.required,
            align: 'center',
            onHeaderCell: () => ({
                width: columnWidths.required,
                onResize: handleResize('required'),
            }),
            render: (_, record) => (
                <Checkbox
                    checked={record.required}
                    onChange={(e) => handleRequiredChange(e.target.checked, record)}
                />
            ),
        },
        {
            title: '操作',
            key: 'action',
            width: columnWidths.action,
            align: 'center',
            onHeaderCell: () => ({
                width: columnWidths.action,
                onResize: handleResize('action'),
            }),
            render: (_, record, index) => (
                <Space size={4}>
                    <Button
                        danger
                        size="small"
                        icon={<DeleteOutlined/>}
                        onClick={() => handleDeleteField(record, index)}
                    />
                    {record.required && (
                        <Button
                            size="small"
                            icon={<SafetyOutlined/>}
                            onClick={() => handleFieldValidate(record)}
                        >
                            验证
                        </Button>
                    )}
                </Space>
            ),
        },
    ]

    return (
        <>
            <Modal
                title={
                    <Space>
                        <ApiOutlined style={{color: '#1890ff'}}/>
                        <span>请求参数管理</span>
                    </Space>
                }
                open={visible}
                onCancel={handleClose}
                width="90%"
                style={{top: 10, maxWidth: 1800, marginLeft: '105px'}}
                footer={
                    <div style={{textAlign: 'center'}}>
                        <Button onClick={handleClose} size="large">
                            关闭
                        </Button>
                    </div>
                }
                destroyOnClose
            >
                {currentMethod && currentController && (
                    <Card
                        size="small"
                        style={{
                            marginBottom: 8,
                            background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                            border: 'none',
                            borderRadius: '6px',
                            boxShadow: '0 2px 8px rgba(102, 126, 234, 0.15)'
                        }}
                        bodyStyle={{padding: '8px 12px'}}
                    >
                        <Space direction="vertical" size={4} style={{width: '100%', color: '#fff'}}>
                            <Space size={16} wrap style={{fontSize: '12px'}}>
                                <Space size={4}>
                                    <DatabaseOutlined style={{fontSize: 13}}/>
                                    <strong>类名：</strong>
                                    <span>{currentController.simpleName}</span>
                                </Space>
                                {currentController.description && (
                                    <Space size={4}>
                                        <FileTextOutlined style={{fontSize: 13}}/>
                                        <strong>描述：</strong>
                                        <span>{currentController.description}</span>
                                    </Space>
                                )}
                                <Space size={4}>
                                    <FileTextOutlined style={{fontSize: 13}}/>
                                    <strong>方法：</strong>
                                    <span>{currentMethod.name}</span>
                                </Space>
                                <Space size={4}>
                                    <ApiOutlined style={{fontSize: 13}}/>
                                    <strong>路径：</strong>
                                    <Tag color="rgba(255,255,255,0.2)" style={{
                                        color: '#fff',
                                        border: '1px solid rgba(255,255,255,0.3)',
                                        fontSize: '11px',
                                        padding: '0 6px',
                                        lineHeight: '18px'
                                    }}>
                                        {currentMethod.paths}
                                    </Tag>
                                </Space>
                                <Space size={4}>
                                    <SettingOutlined style={{fontSize: 13}}/>
                                    <strong>方式：</strong>
                                    <Tag color="rgba(255,255,255,0.2)" style={{
                                        color: '#fff',
                                        border: '1px solid rgba(255,255,255,0.3)',
                                        fontWeight: 'bold',
                                        fontSize: '11px',
                                        padding: '0 6px',
                                        lineHeight: '18px'
                                    }}>
                                        {currentMethod.methods}
                                    </Tag>
                                </Space>
                                {currentMethod.value && (
                                    <Space size={4}>
                                        <strong>说明：</strong>
                                        <span style={{opacity: 0.9}}>{currentMethod.value}</span>
                                    </Space>
                                )}
                            </Space>
                        </Space>
                    </Card>
                )}

                <div style={{display: 'flex', flexDirection: 'column', gap: 12, minHeight: '650px', maxHeight: 'calc(100vh - 180px)'}}>
                    {/* 顶部：参数列表（横向） */}
                        <div style={{
                        position: 'relative',
                        zIndex: 5,
                        border: '1px solid #eaeaea',
                        borderRadius: 8,
                                background: '#fff',
                        padding: '8px 40px',
                        boxShadow: '0 2px 8px rgba(0,0,0,0.04)',
                        marginBottom: 8
                    }}>
                        {/* 左侧滚动按钮 */}
                        <Button
                            size="small"
                            icon={<LeftOutlined/>}
                            style={{
                                position: 'absolute',
                                left: 8,
                                top: '50%',
                                transform: 'translateY(-50%)',
                                zIndex: 6,
                                background: '#fff'
                            }}
                            onClick={() => paramScrollRef.current?.scrollBy({left: -260, behavior: 'smooth'})}
                        />

                        {/* 滚动容器 */}
                        <div
                            ref={paramScrollRef}
                            style={{
                                overflowX: 'auto',
                                whiteSpace: 'nowrap',
                                scrollbarWidth: 'thin'
                            }}
                        >
                        <Space style={{minWidth: '100%'}} size={6} wrap={false}>
                            <Space size={6} style={{marginRight: 8}}>
                                        <DatabaseOutlined style={{color: '#1890ff', fontSize: 14}}/>
                                        <span style={{fontWeight: 600, fontSize: 13}}>参数列表</span>
                                <Tag color="blue" style={{fontSize: 11, padding: '0 6px', lineHeight: '18px'}}>{requestParams.length}</Tag>
                                    </Space>
                                {loading ? (
                                <span style={{color: '#999'}}>加载中...</span>
                                ) : requestParams.length === 0 ? (
                                <span style={{color: '#999'}}>暂无参数</span>
                                ) : (
                                    requestParams.map((param, index) => {
                                        const isSelected = selectedParam === param
                                        const fieldsCount = param.parameters?.length || 0
                                        return (
                                            <div
                                                key={`${param.methodParamName}-${index}`}
                                                style={{
                                                display: 'inline-block',
                                                padding: '6px 10px',
                                                border: `1px solid ${isSelected ? '#1677ff' : '#e6e6e6'}`,
                                                borderRadius: 999,
                                                background: isSelected ? 'rgba(22,119,255,0.08)' : '#fff',
                                                    cursor: 'pointer',
                                                    transition: 'all 0.3s',
                                                boxShadow: isSelected ? '0 2px 6px rgba(22,119,255,0.15)' : 'none'
                                                }}
                                                onClick={() => handleParamClick(param, index)}
                                                onMouseEnter={(e) => {
                                                    if (!isSelected) {
                                                    e.currentTarget.style.borderColor = '#1677ff'
                                                    e.currentTarget.style.background = '#f6faff'
                                                    }
                                                }}
                                                onMouseLeave={(e) => {
                                                    if (!isSelected) {
                                                        e.currentTarget.style.borderColor = '#e8e8e8'
                                                        e.currentTarget.style.background = '#fff'
                                                    }
                                                }}
                                            >
                                            <Tooltip title={
                                                <div>
                                                    <div>参数名：{param.methodParamName}</div>
                                                    <div>包名：{param.name}</div>
                                                </div>
                                            }>
                                                <Space size={6} align="center">
                                                    <Badge count={fieldsCount} size="small" offset={[4, -2]}>
                                                        <span style={{
                                                            display: 'inline-flex',
                                                            alignItems: 'center',
                                                            gap: 6,
                                                            color: isSelected ? '#1677ff' : '#333',
                                                            fontWeight: 600,
                                                            fontSize: 13
                                                }}>
                                                        <FileTextOutlined/>
                                                            <span style={{maxWidth: 160, overflow: 'hidden', textOverflow: 'ellipsis'}}>{param.methodParamName}</span>
                                                        </span>
                                                    </Badge>
                                                    {/* 移除在参数列表中展示的参数类型 */}
                                                    {param.required && (
                                                        <Tag color="error" style={{margin: 0, fontSize: 10}}>必传</Tag>
                                                    )}
                                                </Space>
                                            </Tooltip>
                                            </div>
                                        )
                                    })
                                )}
                        </Space>
                            </div>

                        {/* 右侧滚动按钮 */}
                        <Button
                            size="small"
                            icon={<RightOutlined/>}
                            style={{
                                position: 'absolute',
                                right: 8,
                                top: '50%',
                                transform: 'translateY(-50%)',
                                zIndex: 6,
                                background: '#fff'
                            }}
                            onClick={() => paramScrollRef.current?.scrollBy({left: 260, behavior: 'smooth'})}
                        />
                        </div>

                    {/* 下方：字段配置区域（占满宽度） */}
                    <div>
                    {/* 右侧：字段配置区域 */}
                        <Col span={24}>
                        {!selectedParam ? (
                            <div style={{
                                height: '100%',
                                minHeight: '650px',
                                border: '2px dashed #d9d9d9',
                                borderRadius: '8px',
                                display: 'flex',
                                flexDirection: 'column',
                                alignItems: 'center',
                                justifyContent: 'center',
                                background: 'linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%)',
                                gap: 20
                            }}>
                                <DatabaseOutlined style={{fontSize: 64, color: '#bbb'}}/>
                                <div style={{textAlign: 'center'}}>
                                    <div style={{fontSize: 18, fontWeight: 500, color: '#666', marginBottom: 8}}>
                                        请选择一个参数开始配置
                                    </div>
                                    <div style={{fontSize: 14, color: '#999'}}>
                                        点击左侧参数列表中的任意参数，即可在此处配置其字段信息
                                    </div>
                                </div>
                            </div>
                        ) : (
                            <div style={{
                                height: '100%',
                                minHeight: '650px',
                                maxHeight: 'calc(100vh - 180px)',
                                border: '2px solid #f0f0f0',
                                borderRadius: '8px',
                                background: '#fff',
                                display: 'flex',
                                flexDirection: 'column',
                                overflow: 'hidden'
                            }}>
                                {/* 参数信息头部 */}
                                <div style={{
                                    padding: '10px 14px',
                                    background: 'linear-gradient(135deg, #1890ff 0%, #36cfc9 100%)',
                                    borderRadius: '6px 6px 0 0',
                                    flexShrink: 0
                                }}>
                                    <div style={{
                                        display: 'flex',
                                        justifyContent: 'space-between',
                                        alignItems: 'center',
                                        color: '#fff'
                                    }}>
                                        <Space size={12} wrap style={{flex: 1, fontSize: '12px'}}>
                                            <Space size={6}>
                                                <DatabaseOutlined style={{fontSize: 14}}/>
                                                <span style={{fontWeight: 600}}>{selectedParam.methodParamName}</span>
                                                <Tag color="rgba(255,255,255,0.25)" style={{
                                                    color: '#fff',
                                                    border: '1px solid rgba(255,255,255,0.4)',
                                                    fontSize: 11,
                                                    padding: '0 6px',
                                                    lineHeight: '18px'
                                                }}>
                                                    已配置 {selectedFields.length} 个
                                                </Tag>
                                            </Space>
                                            <Space size={6}>
                                                <span>包名：</span>
                                                <span style={{
                                                    fontFamily: 'monospace',
                                                    background: 'rgba(255,255,255,0.15)',
                                                    padding: '1px 6px',
                                                    borderRadius: '3px',
                                                    fontSize: '11px'
                                                }}>
                          {selectedParam.name}
                        </span>
                                            </Space>
                                            {/* 取消在头部展示参数类型 */}
                                            {selectedParam.required && (
                                                <Tag color="rgba(255,69,58,0.3)" style={{
                                                    color: '#fff',
                                                    border: '1px solid rgba(255,255,255,0.4)',
                                                    margin: 0,
                                                    fontSize: '11px',
                                                    padding: '0 6px',
                                                    lineHeight: '18px'
                                                }}>
                                                    ⚠️ 必传
                                                </Tag>
                                            )}
                                            {selectedParam.value && (
                                                <Space size={4}>
                                                    <span>说明：</span>
                                                    <span style={{opacity: 0.9}}>{selectedParam.value}</span>
                                                </Space>
                                            )}
                                        </Space>
                                        <Button
                                            type="primary"
                                            onClick={handleSaveFields}
                                            ghost
                                            size="small"
                                            style={{borderColor: '#fff', color: '#fff', fontWeight: 500}}
                                        >
                                            💾 保存
                                        </Button>
                                    </div>
                                </div>

                                {/* 字段配置区域 */}
                                <div style={{flex: 1, minHeight: 0, display: 'flex', padding: '14px', gap: '14px'}}>
                                    {/* 可选字段库 */}
                                    <div style={{
                                        width: '25%',
                                        height: '100%',
                                        maxHeight: 'calc(100vh - 220px)',
                                        minHeight: 0,
                                        border: '1px solid #f0f0f0',
                                        borderRadius: '6px',
                                        background: '#fafafa',
                                        padding: '12px',
                                        display: 'flex',
                                        flexDirection: 'column',
                                        overflow: 'hidden'
                                    }}>
                                        <div style={{marginBottom: 10, flexShrink: 0}}>
                                            <Space direction="vertical" style={{width: '100%'}} size={8}>
                                                <div style={{fontWeight: 600, fontSize: 13, color: '#333', display: 'flex', justifyContent: 'space-between', alignItems: 'center'}}>
                                                    <Space>
                                                        <DatabaseOutlined style={{color: '#1890ff'}}/>
                                                        可选字段库
                                                    </Space>
                                                    <Space size={4}>
                                                        <span style={{fontSize: 12, color: '#666'}}>显示类型</span>
                                                        <Switch
                                                            size="small"
                                                            checked={showDataType}
                                                            onChange={setShowDataType}
                                                        />
                                                    </Space>
                                                </div>
                                                <Select
                                                    value={fieldFilter}
                                                    onChange={handleFieldFilterChange}
                                                    size="small"
                                                    style={{width: '100%'}}
                                                >
                                                    <Option value="all">全部字段</Option>
                                                    <Option value="available">仅可选字段</Option>
                                                    <Option value="selected">仅已选字段</Option>
                                                </Select>
                                                <Select
                                                    value={fieldSort}
                                                    onChange={handleFieldSortChange}
                                                    size="small"
                                                    style={{width: '100%'}}
                                                >
                                                    <Option value="selected-last">✓ 已选排最后</Option>
                                                    <Option value="selected-first">✓ 已选排最前</Option>
                                                </Select>
                                                <Input
                                                    placeholder="搜索字段名、类型..."
                                                    prefix={<SearchOutlined/>}
                                                    value={searchText}
                                                    onChange={(e) => handleFieldSearch(e.target.value)}
                                                    allowClear
                                                    size="small"
                                                />
                                                <Button
                                                    type="primary"
                                                    size="small"
                                                    block
                                                    onClick={handleAddAllFields}
                                                    icon={<RightOutlined/>}
                                                    style={{fontWeight: 500}}
                                                >
                                                    全选添加
                                                </Button>
                                            </Space>
                                        </div>

                                        <div style={{
                                            // 独立滚动区：始终可滚动
                                            flex: 1,
                                            minHeight: 0,
                                            maxHeight: '100%',
                                            overflowY: 'scroll',
                                            overflowX: 'hidden',
                                            scrollbarGutter: 'stable both-edges',
                                            overscrollBehavior: 'contain',
                                            WebkitOverflowScrolling: 'touch',
                                            background: '#fff',
                                            borderRadius: '4px',
                                            padding: '8px'
                                        }}>
                                            {loadingFields ? (
                                                <div style={{
                                                    textAlign: 'center',
                                                    padding: '30px 0',
                                                    color: '#999',
                                                    fontSize: '13px'
                                                }}>
                                                    加载中...
                                                </div>
                                            ) : filteredFields.length === 0 ? (
                                                <Empty
                                                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                                                    description="暂无字段"
                                                    style={{marginTop: 20}}
                                                />
                                            ) : (
                                                renderFieldTree(filteredFields, 0)
                                            )}
                                        </div>
                                    </div>

                                    {/* 已选字段配置 */}
                                    <div style={{
                                        width: '75%',
                                        height: '100%',
                                        maxHeight: 'calc(100vh - 220px)',
                                        minHeight: 0,
                                        border: '1px solid #f0f0f0',
                                        borderRadius: '6px',
                                        background: '#fff',
                                        padding: '12px',
                                        display: 'flex',
                                        flexDirection: 'column',
                                        overflow: 'hidden'
                                    }}>
                                        <div style={{
                                            marginBottom: 10,
                                            flexShrink: 0
                                        }}>
                                            <Space direction="vertical" style={{width: '100%'}} size={8}>
                                                <div style={{fontWeight: 600, fontSize: 13, color: '#333'}}>
                                                    <Space>
                                                        <FieldStringOutlined style={{color: '#52c41a'}}/>
                                                        已选字段配置
                                                        {selectedFields.length > 0 && (
                                                            <Tag color="green" style={{fontSize: '11px'}}>
                                                                共 {countAllFields(selectedFields)} 个字段
                                                            </Tag>
                                                        )}
                                                        {selectedRowKeys.length > 0 && (
                                                            <Tag color="blue"
                                                                 style={{fontSize: '11px'}}>已选 {selectedRowKeys.length}</Tag>
                                                        )}
                                                    </Space>
                                                </div>
                                                <Input
                                                    placeholder="搜索已选字段名、类型..."
                                                    prefix={<SearchOutlined/>}
                                                    value={selectedFieldsSearchText}
                                                    onChange={(e) => handleSelectedFieldsSearch(e.target.value)}
                                                    allowClear
                                                    size="small"
                                                    style={{width: '100%'}}
                                                />
                                                <Space style={{width: '100%'}} size={8}>
                                                    <Button
                                                        danger
                                                        size="small"
                                                        icon={<DeleteOutlined/>}
                                                        onClick={handleBatchDelete}
                                                        disabled={selectedRowKeys.length === 0}
                                                        style={{flex: 1}}
                                                    >
                                                        批量删除 {selectedRowKeys.length > 0 && `(${selectedRowKeys.length})`}
                                                    </Button>
                                                    <Button
                                                        danger
                                                        size="small"
                                                        onClick={handleDeleteAll}
                                                        disabled={selectedFields.length === 0}
                                                        style={{flex: 1}}
                                                    >
                                                        全部删除
                                                    </Button>
                                                </Space>
                                            </Space>
                                        </div>
                                        <div style={{flex: 1, minHeight: 0, overflow: 'hidden'}} className="resizable-table">
                                            <Table
                                                columns={fieldColumns}
                                                dataSource={selectedFieldsSearchText ? filteredSelectedFields : selectedFields}
                                                rowKey="keyName"
                                                pagination={false}
                                                size="small"
                                                bordered
                                                scroll={{ y: 'calc(100vh - 400px)' }}
                                                components={{
                                                    header: {
                                                        cell: ResizableTitle,
                                                    },
                                                }}
                                                rowSelection={{
                                                    selectedRowKeys,
                                                    onChange: (selectedKeys) => setSelectedRowKeys(selectedKeys as string[]),
                                                    checkStrictly: false,
                                                }}
                                                expandable={{
                                                    childrenColumnName: 'parameters',
                                                    defaultExpandAllRows: true,
                                                }}
                                                locale={{
                                                    emptyText: (
                                                        <Empty
                                                            image={Empty.PRESENTED_IMAGE_SIMPLE}
                                                            description={
                                                                <span style={{color: '#999'}}>
                                  {selectedFieldsSearchText ? '未找到匹配的字段' : '👈 从左侧选择字段添加'}
                                </span>
                                                            }
                                                        />
                                                    )
                                                }}
                                            />
                                        </div>
                                    </div>
                                </div>
                            </div>
                        )}
                    </Col>
                    </div>
                </div>
            </Modal>

            <FieldValidateModal ref={fieldValidateRef}/>
        </>
    )
})

RequestParamModal.displayName = 'RequestParamModal'

export default RequestParamModal
