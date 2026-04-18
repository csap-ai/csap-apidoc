import {useState, forwardRef, useImperativeHandle} from 'react'
import {Modal, Table, Button, message, Tag, Card, Space, Input, Empty, Select, Checkbox, Tooltip, Switch} from 'antd'
import {
    ApiOutlined,
    FileTextOutlined,
    SettingOutlined,
    DatabaseOutlined,
    FieldStringOutlined,
    SearchOutlined,
    RightOutlined,
    DeleteOutlined,
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
import {addResponseParam, getFields} from '@/api/devtools'
import {ResizableTitle} from '@/components/table'
import '@/components/table/resizable.scss'

const {Option} = Select

export interface ResponseParamModalRef {
    open: (controller: ControllerModel, method: MethodModel) => void
}

interface FieldData {
    key: string
    name: string
    dataType: string
    value?: string
    required: boolean
    include?: boolean
    parameters?: FieldData[]
    children?: FieldData
    childrenField?: any[]
    keyName: string,
}

// 扩展 ParamModel 以支持运行时添加的属性
interface ExtendedParamModel extends ParamModel {
    parameters?: FieldData[]
    value?: string
}

const ResponseParamModal = forwardRef<ResponseParamModalRef>((_props, ref) => {
    const [visible, setVisible] = useState(false)
    const [loading, setLoading] = useState(false)
    const [_responseParams, setResponseParams] = useState<ExtendedParamModel[]>([])
    const [currentMethod, setCurrentMethod] = useState<any>(null)
    const [currentController, setCurrentController] = useState<ControllerModel | null>(null)

    // 选中的参数
    const [selectedParam, setSelectedParam] = useState<ExtendedParamModel | null>(null)
    const [_selectedParamIndex, setSelectedParamIndex] = useState<number>(-1)

    // 字段相关状态
    const [selectedFields, setSelectedFields] = useState<FieldData[]>([])
    const [availableFields, setAvailableFields] = useState<any[]>([])
    const [filteredFields, setFilteredFields] = useState<any[]>([])
    const [searchText, setSearchText] = useState('')
    const [loadingFields, setLoadingFields] = useState(false)
    const [filedNames, setFiledNames] = useState<Record<string, any>>({})
    const [fieldFilter, setFieldFilter] = useState<'all' | 'available' | 'selected'>('all')
    const [fieldSort, setFieldSort] = useState<'selected-last' | 'selected-first'>('selected-last')
    const [expandedKeys, setExpandedKeys] = useState<Set<string>>(new Set())
    const [showDataType, setShowDataType] = useState<boolean>(false) // 默认隐藏字段类型

    // 已选字段搜索
    const [selectedFieldsSearchText, setSelectedFieldsSearchText] = useState('')
    const [filteredSelectedFields, setFilteredSelectedFields] = useState<FieldData[]>([])

    // 多选删除相关状态
    const [selectedRowKeys, setSelectedRowKeys] = useState<string[]>([])

    // 列宽状态
    const [columnWidths, setColumnWidths] = useState<Record<string, number>>({
        name: 200,
        dataType: 150,
        value: 200,
        required: 80,
        action: 100
    })

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

    // 根据数据类型返回对应的图标和颜色
    const getFieldIcon = (dataType: string) => {
        const type = dataType?.toLowerCase() || ''

        if (type.includes('int') || type.includes('long') || type.includes('short') ||
            type.includes('byte') || type.includes('number') || type.includes('double') ||
            type.includes('float') || type.includes('bigdecimal') || type.includes('biginteger')) {
            return {icon: <NumberOutlined/>, color: '#52c41a'}
        }

        if (type.includes('boolean') || type.includes('bool')) {
            return {icon: <CheckSquareOutlined/>, color: '#fa8c16'}
        }

        if (type.includes('date') || type.includes('time') || type.includes('localdate') ||
            type.includes('localdatetime') || type.includes('timestamp')) {
            return {icon: <CalendarOutlined/>, color: '#722ed1'}
        }

        if (type.includes('list') || type.includes('array') || type.includes('collection') ||
            type.includes('set') || type === '[]') {
            return {icon: <UnorderedListOutlined/>, color: '#13c2c2'}
        }

        if (type.includes('object') || type.includes('map') || type === '{}') {
            return {icon: <FileOutlined/>, color: '#eb2f96'}
        }

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
    const loadAvailableFields = async (row: any, clzName: string, pName: string, methodName: string) => {
        try {
            setLoadingFields(true)
            // 后端现在直接返回完整的类型信息，不需要再做特殊处理
            const className = row.name
            const usePName = pName

            console.log('loadAvailableFields 输入 - row.name:', row.name, 'pName:', pName)
            console.log('🔥🔥🔥 [ResponseParamModal] 调用getFields')
            console.log('  - className(第1个参数):', className)
            console.log('  - controllerClassName(第3个参数/clzName):', clzName)
            console.log('  - methodName(第4个参数):', methodName)
            console.log('  - parameterIndex(第5个参数): -1 (返回值)')

            const fields = await getFields(className, [], clzName, methodName, -1)

            const processFields = (fieldList: any[], parentKey = ''): any[] => {
                return fieldList.map(field => {
                    const key = parentKey ? `${parentKey}.${field.name}` : field.name

                    // 构建 fieldKeyName：如果 usePName 为空，则直接使用 key
                    let fieldKeyName: string
                    if (!usePName || usePName.trim() === '') {
                        fieldKeyName = key
                    } else {
                        const separator = usePName.endsWith('.') ? '' : '.'
                        fieldKeyName = usePName + separator + key
                    }

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
            console.log('📂 [ResponseParamModal.open] controller:', controller.name, 'method:', method.name)
            setVisible(true)
            setLoading(true)
            setCurrentController(controller)

            try {
                const api = await getParamList(controller.name, method.name)
                console.log('📂 [ResponseParamModal.open] getParamList返回:', api)
                api.className = controller.name
                setCurrentMethod(api)

                if (api.response == null) {
                    api.response = []
                }
                const filteredResponse = api.response.filter((i: ParamModel) => !i.$ref)
                setResponseParams(filteredResponse)

                // 自动选中第一个参数
                if (filteredResponse.length > 0) {
                    const firstParam = filteredResponse[0]
                    console.log('=== 返回参数初始化 ===')
                    console.log('firstParam.name:', firstParam.name)
                    console.log('firstParam.modelType:', firstParam.modelType)
                    console.log('firstParam.methodParamName:', firstParam.methodParamName)
                    console.log('firstParam.parameters 数量:', firstParam.parameters?.length || 0)
                    console.log('firstParam 完整结构:', firstParam)

                    setSelectedParam(firstParam)
                    setSelectedParamIndex(0)

                    // 返回参数使用空字符串作为前缀
                    const paramName = ''
                    // 解析参数结构，处理$ref和children
                    const resolvedData = resolveParameters(firstParam.parameters || [])
                    const cleanedData = cleanEmptyParameters(resolvedData)
                    setSelectedFields(cleanedData)

                    // 构建已选字段的映射
                    const names: Record<string, any> = {}
                    addKeyName(cleanedData, names)

                    // 加载可选字段（传递方法名）
                    const loadedFields = await loadAvailableFields(firstParam, api.className, paramName, api.name)

                    console.log('📊 loadedFields 返回数量:', loadedFields?.length || 0)

                    // 设置 filedNames
                    setFiledNames(names)

                    // 立即应用排序（使用加载返回的字段）
                    applyFilters('', 'all', 'selected-last', names, loadedFields || [])
                }
            } catch (error: any) {
                message.error('加载返回参数失败：' + error.message)
            } finally {
                setLoading(false)
            }
        }
    }))

    const handleClose = () => {
        setVisible(false)
        setResponseParams([])
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
        setExpandedKeys(new Set())
        setSelectedFieldsSearchText('')
        setFilteredSelectedFields([])
        setSelectedRowKeys([])
        setShowDataType(false)
        // 注意：不清除编辑状态，保留高亮状态，直到用户点击其他接口
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

        if (filterType === 'available') {
            result = result.filter(field => !names[field.keyName])
        } else if (filterType === 'selected') {
            result = result.filter(field => names[field.keyName])
        }

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

    const handleFieldSearch = (value: string) => {
        setSearchText(value)
        applyFilters(value, fieldFilter)
    }

    const handleFieldFilterChange = (value: 'all' | 'available' | 'selected') => {
        setFieldFilter(value)
        applyFilters(searchText, value)
    }

    const handleFieldSortChange = (value: 'selected-last' | 'selected-first') => {
        setFieldSort(value)
        applyFilters(searchText, fieldFilter, value)
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

            // 递归检查子字段
            let matchedChildren: FieldData[] = []
            if (field.parameters && field.parameters.length > 0) {
                matchedChildren = filterSelectedFields(field.parameters, searchValue)
            }

            // 如果当前字段匹配或有匹配的子字段，则包含该字段
            if (nameMatch || typeMatch || valueMatch || matchedChildren.length > 0) {
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

    // 递归转换 childrenField 为 parameters 结构
    const convertChildrenToParameters = (field: any): FieldData => {
        console.log('convertChildrenToParameters - field:', field.name, 'childrenField:', field.childrenField?.length || 0)

        const newField: FieldData = {
            name: field.name,
            dataType: field.dataType,
            key: field.name,
            keyName: field.keyName,
            value: field.value,
            required: false,
            include: true
        }

        // 递归处理子字段
        if (field.childrenField && field.childrenField.length > 0) {
            console.log(`  -> ${field.name} 有 ${field.childrenField.length} 个子字段`)
            newField.parameters = field.childrenField.map((child: any) => convertChildrenToParameters(child))
            console.log(`  -> 转换后 ${field.name}.parameters 长度:`, newField.parameters?.length || 0)
        }

        return newField
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
        setSelectedFields(newFields)

        // 递归删除该字段及其所有子字段从 filedNames 中移除
        const names = {...filedNames}
        removeFromFiledNames(fieldToDelete, names)

        // 检查并清理空的父字段
        const cleanEmptyParents = (fields: FieldData[]) => {
            const fieldsToRemove: string[] = []
            const checkField = (f: FieldData) => {
                if (f.parameters && f.parameters.length === 0 && !names[f.keyName]) {
                    fieldsToRemove.push(f.keyName)
                } else if (f.parameters && f.parameters.length > 0) {
                    f.parameters.forEach(checkField)
                }
            }
            fields.forEach(checkField)
            fieldsToRemove.forEach(key => delete names[key])
        }
        cleanEmptyParents(newFields)

        console.log('删除后 filedNames 剩余 keys:', Object.keys(names).length)

        // 先更新 filedNames 状态
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

    // 添加字段到正确的位置（支持子字段单选）
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
            // 创建父字段路径
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
                            include: true,
                            parameters: []
                        }
                        pathToCreate.push({field: newParent, index: i})
                        names[currentKeyName] = newParent
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

                // 将路径中的所有字段添加到 names
                pathToCreate.forEach(p => {
                    names[p.field.keyName] = p.field
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
            include: true
        }

        // 如果有子字段，转换它们
        if (field.childrenField && field.childrenField.length > 0) {
            newField.parameters = field.childrenField.map((child: any) => convertChildrenToParameters(child))
        }

        // 添加当前字段到正确的位置
        // 判断是否应该添加到父字段下：使用更新后的 names 对象而不是旧的 filedNames 状态
        const parentNowExists = !!names[parentKeyName]

        if (parentNowExists && parentKeyName) {
            // 父字段存在（或刚被创建），添加到父字段下
            const addToParent = (fields: FieldData[], parentKey: string, child: FieldData): boolean => {
                for (const f of fields) {
                    if (f.keyName === parentKey) {
                        if (!f.parameters) f.parameters = []
                        f.parameters.push(child)
                        return true
                    }
                    if (f.parameters && f.parameters.length > 0) {
                        if (addToParent(f.parameters, parentKey, child)) return true
                    }
                }
                return false
            }

            const added = addToParent(newFields, parentKeyName, newField)
            if (!added) {
                console.warn(`无法将字段 '${field.name}' 添加到父字段 '${parentKeyName}' 下，将添加到根级别`)
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
        // 优化：只收集需要添加的字段，如果父字段会被添加，则不单独收集子字段（避免重复）
        const collectAllFields = (fields: any[], parentWillBeAdded: Set<string> = new Set()): any[] => {
            const result: any[] = []

            fields.forEach(field => {
                // 如果字段的父字段会被添加，则跳过该字段及其子字段（因为父字段会包含它们）
                // 检查该字段是否在某个会被添加的父字段路径下
                const fieldPathParts = field.keyName.split('.')
                let shouldSkip = false
                for (let i = 1; i < fieldPathParts.length; i++) {
                    const parentPath = fieldPathParts.slice(0, i).join('.')
                    if (parentWillBeAdded.has(parentPath)) {
                        shouldSkip = true
                        break
                    }
                }
                if (shouldSkip) {
                    return // 跳过该字段，不再递归处理其子字段
                }

                // 只收集不在 filedNames 中的字段
                if (!filedNames[field.keyName]) {
                    result.push(field)
                    // 标记该字段会被添加，其子字段将不会单独添加（因为会通过 convertChildrenToParameters 包含在父字段中）
                    parentWillBeAdded.add(field.keyName)
                    // 不再递归处理子字段，因为它们会通过 convertChildrenToParameters 自动包含在父字段中
                } else {
                    // 字段已存在，但需要递归检查其子字段（因为可能有新的子字段需要添加）
                    if (field.childrenField && field.childrenField.length > 0) {
                        result.push(...collectAllFields(field.childrenField, parentWillBeAdded))
                    }
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
            // 双重检查：在循环过程中，字段可能已经被添加（作为父字段的子字段）
            if (currentNames[field.keyName]) {
                console.log(`跳过已存在的字段: ${field.keyName}`)
                return
            }

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
                                include: true,
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
                include: true
            }

            // 如果有子字段，转换它们
            if (field.childrenField && field.childrenField.length > 0) {
                newField.parameters = field.childrenField.map((child: any) => convertChildrenToParameters(child))
            }

            // 添加当前字段到正确的位置
            // 判断是否应该添加到父字段下：使用更新后的 currentNames 对象
            const parentNowExists = !!currentNames[parentKeyName]

            if (parentNowExists && parentKeyName) {
                // 父字段存在（或刚被创建），添加到父字段下
                const addToParent = (fields: FieldData[], parentKey: string, child: FieldData): boolean => {
                    for (const f of fields) {
                        if (f.keyName === parentKey) {
                            if (!f.parameters) f.parameters = []
                            f.parameters.push(child)
                            return true
                        }
                        if (f.parameters && f.parameters.length > 0) {
                            if (addToParent(f.parameters, parentKey, child)) return true
                        }
                    }
                    return false
                }

                const added = addToParent(currentFields, parentKeyName, newField)
                if (!added) {
                    console.warn(`无法将字段 '${field.name}' 添加到父字段 '${parentKeyName}' 下，将添加到根级别`)
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
    const removeFromFiledNames = (field: FieldData, names: Record<string, any>) => {
        delete names[field.keyName]
        if (field.parameters && field.parameters.length > 0) {
            field.parameters.forEach(child => removeFromFiledNames(child, names))
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
        setSelectedFields(newFields)

        // 递归删除该字段及其所有子字段从 filedNames 中移除
        const names = {...filedNames}
        removeFromFiledNames(record, names)

        // 检查并清理空的父字段
        const cleanEmptyParents = (fields: FieldData[]) => {
            const fieldsToRemove: string[] = []
            const checkField = (f: FieldData) => {
                if (f.parameters && f.parameters.length === 0 && !names[f.keyName]) {
                    fieldsToRemove.push(f.keyName)
                } else if (f.parameters && f.parameters.length > 0) {
                    f.parameters.forEach(checkField)
                }
            }
            fields.forEach(checkField)
            fieldsToRemove.forEach(key => delete names[key])
        }
        cleanEmptyParents(newFields)

        console.log('删除后 filedNames 剩余 keys:', Object.keys(names).length)

        // 先更新 filedNames 状态
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

    // 递归收集字段及其所有子字段的 keyName
    const _collectAllKeyNames = (field: FieldData): string[] => {
        const keys = [field.keyName]
        if (field.parameters && field.parameters.length > 0) {
            field.parameters.forEach(child => {
                keys.push(..._collectAllKeyNames(child))
            })
        }
        return keys
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
        const names = {...filedNames}

        // 对每个要删除的字段，执行跟单个删除一样的操作
        fieldsToDelete.forEach(field => {
            newFields = removeFieldByKeyName(newFields, field.keyName)
            removeFromFiledNames(field, names)
        })

        setSelectedFields(newFields)
        setFiledNames(names)

        console.log('批量删除后 filedNames 剩余 keys:', Object.keys(names).length)

        // 清空选中状态
        setSelectedRowKeys([])

        // 跟单个删除完全一样的刷新逻辑
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

    const handleRequiredChange = (checked: boolean, record: FieldData) => {
        record.required = checked
        setSelectedFields([...selectedFields])
    }

    // 切换展开/折叠
    const toggleExpand = (keyName: string) => {
        const newExpanded = new Set(expandedKeys)
        if (newExpanded.has(keyName)) {
            newExpanded.delete(keyName)
        } else {
            newExpanded.add(keyName)
        }
        setExpandedKeys(newExpanded)
    }

    // 检查字段的选择状态：'all'(全选), 'partial'(半选), 'none'(未选)
    const getFieldSelectionStatus = (field: any): 'all' | 'partial' | 'none' => {
        const isFieldSelected = !!filedNames[field.keyName]

        if (!field.childrenField || field.childrenField.length === 0) {
            return isFieldSelected ? 'all' : 'none'
        }

        // 递归检查子字段的选择情况
        const checkChildren = (children: any[]): { selected: number; total: number } => {
            let selected = 0
            let total = 0

            children.forEach(child => {
                if (filedNames[child.keyName]) {
                    selected++
                }
                total++

                if (child.childrenField && child.childrenField.length > 0) {
                    const childResult = checkChildren(child.childrenField)
                    selected += childResult.selected
                    total += childResult.total
                }
            })

            return {selected, total}
        }

        const {selected, total} = checkChildren(field.childrenField)

        if (selected === 0 && !isFieldSelected) {
            return 'none'
        } else if (selected === total && isFieldSelected) {
            return 'all'
        } else {
            return 'partial'
        }
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
                            {/* 第一行：操作图标 + 复选框 + 字段名 + 备注 + 状态标签 */}
                            <div style={{display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8, minWidth: 0}}>
                                <div style={{display: 'flex', alignItems: 'center', gap: 8, minWidth: 0, flex: 1, overflow: 'hidden'}}>
                                    {hasChildren && (
                                        <span
                                            onClick={(e) => {
                                                e.stopPropagation()
                                                toggleExpand(field.keyName)
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

    // 获取模型类名 - 后端已返回完整类型，直接使用
    const getModelClassName = () => {
        if (!selectedParam) return ''
        return selectedParam.name
    }

    // 获取 appendName - 返回参数使用空字符串
    const getAppendName = () => {
        return ''
    }

    // 保存字段配置
    const handleSaveFields = async () => {
        if (!selectedParam) return

        try {
            const submitData = {
                methodName: currentMethod.name,
                className: currentMethod.className,
                returnType: {
                    fields: selectedFields,
                    appendName: getAppendName(),
                    modelType: selectedParam.modelType,
                    packageName: getModelClassName()
                }
            }

            await addResponseParam(submitData)
            message.success('保存成功')

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
            ellipsis: true,
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
            ellipsis: true,
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
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap'
                    }}>
                        {text}
                    </Tag>
                </Tooltip>
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
                <Tooltip title={text || '无备注'}>
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
            ellipsis: true,
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
            ellipsis: true,
            align: 'center',
            onHeaderCell: () => ({
                width: columnWidths.action,
                onResize: handleResize('action'),
            }),
            render: (_, record, index) => (
                <Button
                    danger
                    size="small"
                    icon={<DeleteOutlined/>}
                    onClick={() => handleDeleteField(record, index)}
                />
            ),
        },
    ]

    return (
        <Modal
            title={
                <Space>
                    <DatabaseOutlined style={{color: '#52c41a'}}/>
                    <span>返回参数管理</span>
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

            {loading ? (
                <div style={{textAlign: 'center', padding: '100px 0', color: '#999'}}>
                    <DatabaseOutlined style={{fontSize: 64, marginBottom: 16}}/>
                    <div>加载中...</div>
                </div>
            ) : !selectedParam ? (
                <div style={{
                    minHeight: '550px',
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
                            暂无返回参数
                        </div>
                        <div style={{fontSize: 14, color: '#999'}}>
                            该方法没有配置返回参数信息
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
                    {/* 返回参数信息头部 */}
                    <div style={{
                        padding: '10px 14px',
                        background: 'linear-gradient(135deg, #11998e 0%, #38ef7d 100%)',
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
                                    <span style={{fontWeight: 600}}>返回参数</span>
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
                                <Space size={6}>
                                    <span>类型：</span>
                                    <Tag color="rgba(255,255,255,0.2)" style={{
                                        color: '#fff',
                                        border: '1px solid rgba(255,255,255,0.3)',
                                        margin: 0,
                                        fontSize: '11px',
                                        padding: '0 6px',
                                        lineHeight: '18px'
                                    }}>
                                        {selectedParam.modelType}
                                    </Tag>
                                </Space>
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
                            border: '1px solid #f0f0f0',
                            borderRadius: '6px',
                            background: '#fafafa',
                            padding: '12px',
                            display: 'flex',
                            flexDirection: 'column',
                            minHeight: 0
                        }}>
                            <div style={{marginBottom: 10, flexShrink: 0}}>
                                <Space direction="vertical" style={{width: '100%'}} size={8}>
                                    <div style={{fontWeight: 600, fontSize: 13, color: '#333', display: 'flex', justifyContent: 'space-between', alignItems: 'center'}}>
                                        <Space>
                                            <DatabaseOutlined style={{color: '#52c41a'}}/>
                                            可选字段库
                                            {filteredFields.length > 0 && (
                                                <>
                                                    <Tag color="green" style={{fontSize: '11px'}}>
                                                        可添加 {filteredFields.filter(f => !filedNames[f.keyName]).length}
                                                    </Tag>
                                                    <Tag color="blue" style={{fontSize: '11px'}}>
                                                        已添加 {filteredFields.filter(f => filedNames[f.keyName]).length}
                                                    </Tag>
                                                </>
                                            )}
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
                                flex: 1,
                                minHeight: 0,
                                overflowY: 'auto',
                                overflowX: 'hidden',
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
        </Modal>
    )
})

ResponseParamModal.displayName = 'ResponseParamModal'

export default ResponseParamModal
