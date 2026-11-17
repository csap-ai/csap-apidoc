#!/bin/bash
echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║         Checkstyle 违规统计报告                               ║"
echo "╚═══════════════════════════════════════════════════════════════╝"
echo ""

total=0
modules_with_violations=0

for file in $(find . -name "checkstyle-result.xml" -path "*/target/*"); do
    module=$(echo $file | sed 's|./||' | sed 's|/target/.*||')
    count=$(grep -c 'severity="warning"' "$file" 2>/dev/null || echo "0")
    
    if [ "$count" -gt 0 ]; then
        echo "📦 $module: $count 个警告"
        total=$((total + count))
        modules_with_violations=$((modules_with_violations + 1))
        
        # 显示前3个警告详情
        echo "   主要问题："
        grep 'severity="warning"' "$file" | head -3 | sed -n 's/.*source="\([^"]*\)".*/   - \1/p'
        echo ""
    fi
done

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📊 总计: $total 个警告 (分布在 $modules_with_violations 个模块)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
