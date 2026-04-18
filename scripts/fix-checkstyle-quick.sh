#!/bin/bash

##############################################################################
# Checkstyle 快速修复脚本
# 
# 这个脚本会自动修复大部分可以自动修复的 Checkstyle 问题
##############################################################################

set -e

# 颜色
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_step() {
    echo -e "${BLUE}==>${NC} $1"
}

print_success() {
    echo -e "${GREEN}✓${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

# 检查是否在项目根目录
if [ ! -f "pom.xml" ]; then
    print_error "请在项目根目录运行此脚本"
    exit 1
fi

echo ""
echo "╔════════════════════════════════════════════════════════════╗"
echo "║     Checkstyle 快速修复工具                               ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""

# 步骤 1: 备份当前代码
print_step "步骤 1/5: 检查 Git 状态"
if git diff --quiet; then
    print_success "工作区干净，可以继续"
else
    print_warning "工作区有未提交的更改"
    echo "建议先提交或暂存更改，按 Ctrl+C 取消，或按回车继续"
    read -p ""
fi

# 步骤 2: 运行 Spotless 格式化
print_step "步骤 2/5: 运行 Spotless 自动格式化"
echo "正在格式化代码..."
if mvn spotless:apply -q; then
    print_success "Spotless 格式化完成"
    echo "  - 已删除未使用的导入"
    echo "  - 已删除尾部空格"
    echo "  - 已调整缩进"
    echo "  - 已调整导入顺序"
else
    print_error "Spotless 格式化失败"
    exit 1
fi

# 步骤 3: 运行 Checkstyle 检查（第一次）
print_step "步骤 3/5: 运行 Checkstyle 检查（修复前）"
echo "正在检查代码..."

BEFORE_COUNT=0
if mvn checkstyle:check -q 2>&1 | tee /tmp/checkstyle-before.log; then
    print_success "Checkstyle 检查通过！"
else
    BEFORE_COUNT=$(grep -c "WARN" /tmp/checkstyle-before.log || echo "0")
    print_warning "发现 $BEFORE_COUNT 个 Checkstyle 警告"
fi

# 步骤 4: 启用临时抑制规则
print_step "步骤 4/5: 应用临时抑制规则"
if [ -f "checkstyle-suppressions.xml" ]; then
    print_success "checkstyle-suppressions.xml 已存在"
    echo "临时抑制以下问题："
    echo "  - JavaDoc 警告（稍后手动添加）"
    echo "  - 测试代码的魔术数字"
else
    print_warning "checkstyle-suppressions.xml 不存在，请先创建"
fi

# 步骤 5: 再次运行 Checkstyle（验证）
print_step "步骤 5/5: 运行 Checkstyle 检查（修复后）"
echo "正在验证..."

if mvn checkstyle:check -q 2>&1 | tee /tmp/checkstyle-after.log; then
    print_success "Checkstyle 检查通过！✨"
    AFTER_COUNT=0
else
    AFTER_COUNT=$(grep -c "WARN" /tmp/checkstyle-after.log || echo "0")
    print_warning "剩余 $AFTER_COUNT 个 Checkstyle 警告"
fi

# 显示统计
echo ""
echo "╔════════════════════════════════════════════════════════════╗"
echo "║     修复结果统计                                           ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""
echo "修复前警告数: $BEFORE_COUNT"
echo "修复后警告数: $AFTER_COUNT"

if [ "$BEFORE_COUNT" -gt "$AFTER_COUNT" ]; then
    FIXED=$((BEFORE_COUNT - AFTER_COUNT))
    print_success "已修复 $FIXED 个警告！"
fi

echo ""

# 生成详细报告
if [ -f "target/checkstyle-result.xml" ]; then
    print_step "生成详细报告"
    echo "Checkstyle 报告位置："
    echo "  - target/checkstyle-result.xml"
    echo "  - target/site/checkstyle.html"
    echo ""
    echo "查看报告："
    echo "  mvn checkstyle:checkstyle"
    echo "  open target/site/checkstyle.html"
fi

# 下一步建议
echo ""
echo "╔════════════════════════════════════════════════════════════╗"
echo "║     下一步操作建议                                         ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""

if [ "$AFTER_COUNT" -gt 0 ]; then
    echo "剩余警告需要手动修复："
    echo ""
    echo "1. 在 IDEA 中打开 Checkstyle 窗口："
    echo "   View → Tool Windows → Checkstyle"
    echo ""
    echo "2. 点击 'Check Project' 查看所有警告"
    echo ""
    echo "3. 双击警告跳转到对应位置"
    echo ""
    echo "4. 使用 Alt+Enter 快速修复"
    echo ""
    echo "5. 查看详细修复指南："
    echo "   cat CHECKSTYLE_FIX_GUIDE.md"
else
    print_success "恭喜！所有 Checkstyle 警告已修复！"
    echo ""
    echo "可以提交代码了："
    echo "  git add ."
    echo "  git commit -m \"style: fix checkstyle warnings\""
fi

echo ""
print_success "脚本执行完成！"
echo ""

