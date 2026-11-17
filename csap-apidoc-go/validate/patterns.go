package validate

import (
	"regexp"
	"sort"
	"strings"
	"sync"
)

// Pattern 是一条内置正则,对应 Java ValidatePatternType。
type Pattern struct {
	Name    string `json:"code"`    // PATTERN_1 …
	Descr   string `json:"descr"`   //
	Pattern string `json:"pattern"` // 正则(Go RE2 语法)
	Type    int    `json:"type"`    // 1 常规 2 数字 3 字符 4 特殊
	// Fixed 说明相对 Java 原版的修正,为空表示与 Java 相同。
	Fixed string `json:"-"`
}

// Patterns 是内置正则列表。Java 原版有几条写错了,这里修正并在 Fixed 里注明;
// PATTERN_43 用了 RE2 不支持的零宽断言,改写成等价写法。
var Patterns = []Pattern{
	{Name: "PATTERN_00", Descr: "不能为空", Pattern: "NotNull", Type: 1},
	{Name: "PATTERN_01", Descr: "不能为空字符串", Pattern: "NotEmpty", Type: 1},
	{Name: "PATTERN_1", Descr: "数字", Pattern: `^[0-9]*$`, Type: 2},
	{Name: "PATTERN_2", Descr: "n位的数字", Pattern: `^\d{13}$`, Type: 2},
	{Name: "PATTERN_3", Descr: "至少n位的数字", Pattern: `^\d{13,}$`, Type: 2},
	{Name: "PATTERN_4", Descr: "m-n位的数字", Pattern: `^\d{10,20}$`, Type: 2},
	{Name: "PATTERN_5", Descr: "零和非零开头的数字", Pattern: `^(0|[1-9][0-9]*)$`, Type: 2},
	{Name: "PATTERN_6", Descr: "非零开头的最多带两位小数的数字", Pattern: `^([1-9][0-9]*)+(\.[0-9]{1,2})?$`, Type: 2},
	{Name: "PATTERN_7", Descr: "带1-2位小数的正数或负数", Pattern: `^(\-)?\d+(\.\d{1,2})$`, Type: 2},
	{Name: "PATTERN_8", Descr: "正数、负数、和小数", Pattern: `^(\-|\+)?\d+(\.\d+)?$`, Type: 2},
	{Name: "PATTERN_9", Descr: "有两位小数的正实数", Pattern: `^[0-9]+(\.[0-9]{2})?$`, Type: 2},
	{Name: "PATTERN_10", Descr: "有1~3位小数的正实数", Pattern: `^[0-9]+(\.[0-9]{1,3})?$`, Type: 2},
	{Name: "PATTERN_11", Descr: "非零的正整数", Pattern: `^[1-9]\d*$`, Type: 2},
	{Name: "PATTERN_12", Descr: "非零的负整数", Pattern: `^\-[1-9][0-9]*$`, Type: 2, Fixed: `Java 原为 ^\-[1-9][]0-9"*$,字符类写错`},
	{Name: "PATTERN_13", Descr: "非负整数", Pattern: `^\d+$`, Type: 2},
	{Name: "PATTERN_14", Descr: "非正整数", Pattern: `^(-[1-9]\d*|0)$`, Type: 2, Fixed: `Java 原为 ^-[1-9]\d*|0$,| 优先级导致只要含 0 就匹配`},
	{Name: "PATTERN_15", Descr: "非负浮点数", Pattern: `^\d+(\.\d+)?$`, Type: 2},
	{Name: "PATTERN_16", Descr: "非正浮点数", Pattern: `^((-\d+(\.\d+)?)|(0+(\.0+)?))$`, Type: 2},
	{Name: "PATTERN_17", Descr: "正浮点数", Pattern: `^([1-9]\d*\.\d*|0\.\d*[1-9]\d*)$`, Type: 2, Fixed: "Java 原版两个分支没有括起来,^ $ 只约束了一边"},
	{Name: "PATTERN_18", Descr: "负浮点数", Pattern: `^-([1-9]\d*\.\d*|0\.\d*[1-9]\d*)$`, Type: 2},
	{Name: "PATTERN_19", Descr: "浮点数", Pattern: `^(-?\d+)(\.\d+)?$`, Type: 2},
	{Name: "PATTERN_20", Descr: "汉字", Pattern: `^[\x{4e00}-\x{9fa5}]{0,}$`, Type: 3},
	{Name: "PATTERN_21", Descr: "英文和数字", Pattern: `^[A-Za-z0-9]+$`, Type: 3, Fixed: "Java 原为两个正则用中文“或”连接,无法匹配任何正常输入"},
	{Name: "PATTERN_22", Descr: "长度为3-20的所有字符", Pattern: `^.{3,20}$`, Type: 3},
	{Name: "PATTERN_23", Descr: "由26个英文字母组成的字符串", Pattern: `^[A-Za-z]+$`, Type: 3},
	{Name: "PATTERN_24", Descr: "由26个大写英文字母组成的字符串", Pattern: `^[A-Z]+$`, Type: 3},
	{Name: "PATTERN_25", Descr: "由26个小写英文字母组成的字符串", Pattern: `^[a-z]+$`, Type: 3},
	{Name: "PATTERN_26", Descr: "由数字和26个英文字母组成的字符串", Pattern: `^[A-Za-z0-9]+$`, Type: 3},
	{Name: "PATTERN_27", Descr: "由数字、26个英文字母或者下划线组成的字符串", Pattern: `^\w+$`, Type: 3, Fixed: "Java 原版末尾多一个空格"},
	{Name: "PATTERN_28", Descr: "中文、英文、数字包括下划线", Pattern: `^[\x{4E00}-\x{9FA5}A-Za-z0-9_]+$`, Type: 3},
	{Name: "PATTERN_29", Descr: "由中文、英文、数字但不包括下划线等符号", Pattern: `^[\x{4E00}-\x{9FA5}A-Za-z0-9]+$`, Type: 3},
	{Name: "PATTERN_30", Descr: `不能含有^%&',;=?$"等字符`, Pattern: `^[^%&',;=?$\x22]+$`, Type: 3, Fixed: "Java 原版末尾多一个空格且没有锚定,描述也写反了"},
	{Name: "PATTERN_31", Descr: "禁止输入含有~的字符", Pattern: `^[^~\x22]+$`, Type: 3, Fixed: "Java 原版没有锚定(matches() 整串匹配,Go 需要显式 ^$)"},
	{Name: "PATTERN_32", Descr: "Email地址", Pattern: `^\w+([-+.]\w+)*@\w+([-.]\w+)*\.\w+([-.]\w+)*$`, Type: 4},
	{Name: "PATTERN_33", Descr: "域名", Pattern: `^[a-zA-Z0-9][-a-zA-Z0-9]{0,62}(\.[a-zA-Z0-9][-a-zA-Z0-9]{0,62})+\.?$`, Type: 4, Fixed: "Java 原版末尾多一个空格"},
	{Name: "PATTERN_34", Descr: "InternetURL", Pattern: `^https?://([\w-]+\.)+[\w-]+(/[\w\-./?%&=]*)?$`, Type: 4, Fixed: "Java 原版只认 http,不认 https"},
	{Name: "PATTERN_35", Descr: "手机号码", Pattern: `^1[3-9]\d{9}$`, Type: 4, Fixed: "Java 原版号段过时(不含 16x/17x/19x 等),且字符类里写了 |"},
	{Name: "PATTERN_36", Descr: "电话号码", Pattern: `^(\(\d{3,4}\)|\d{3,4}-)?\d{7,8}$`, Type: 4, Fixed: `Java 原为 ^(\(\d{3,4}-)|\d{3.4}-)?\d{7,8}$,括号不配对且 {3.4} 写错`},
	{Name: "PATTERN_37", Descr: "国内电话号码", Pattern: `^(\d{3}-\d{8}|\d{4}-\d{7})$`, Type: 4},
	{Name: "PATTERN_38", Descr: "身份证号(15位、18位数字),最后一位是校验位,可能为数字或字符X", Pattern: `^(\d{15}|\d{17}[\dXx])$`, Type: 4},
	{Name: "PATTERN_39", Descr: "日期格式", Pattern: `^\d{4}-\d{1,2}-\d{1,2}$`, Type: 4},
	{Name: "PATTERN_40", Descr: "一年的12个月(01～09和1～12)", Pattern: `^(0?[1-9]|1[0-2])$`, Type: 4},
	{Name: "PATTERN_41", Descr: "一个月的31天(01～09和1～31)", Pattern: `^((0?[1-9])|((1|2)[0-9])|30|31)$`, Type: 4, Fixed: `Java 原版末尾多一个 \n`},
	{Name: "PATTERN_42", Descr: "xml文件", Pattern: `^([a-zA-Z]+-?)+[a-zA-Z0-9]+\.[xX][mM][lL]$`, Type: 4, Fixed: `Java 原版 \\. 匹配的是反斜杠,字符类里写了 |`},
	{Name: "PATTERN_43", Descr: "中国邮政编码", Pattern: `^[1-9]\d{5}$`, Type: 4, Fixed: `Java 原为 [1-9]\d{5}(?!\d),RE2 不支持零宽断言,整串匹配下等价`},
	{Name: "PATTERN_44", Descr: "IPv4地址", Pattern: `^((2(5[0-5]|[0-4]\d))|[0-1]?\d{1,2})(\.((2(5[0-5]|[0-4]\d))|[0-1]?\d{1,2})){3}$`, Type: 3},
}

var (
	byName  = map[string]string{}
	reCache sync.Map // pattern -> *regexp.Regexp
)

func init() {
	for _, p := range Patterns {
		byName[p.Name] = p.Pattern
	}
}

// compile 编译正则并缓存。pattern 可以是内置规则名(如 PATTERN_35)。
// 与 Java Matcher.matches() 一致,要求整串匹配:没有 ^...$ 时自动加上。
func compile(pattern string) (*regexp.Regexp, error) {
	if p, ok := byName[strings.TrimSpace(pattern)]; ok {
		pattern = p
	}
	if re, ok := reCache.Load(pattern); ok {
		return re.(*regexp.Regexp), nil
	}
	re, err := regexp.Compile(`^(?:` + pattern + `)$`)
	if err != nil {
		return nil, err
	}
	reCache.Store(pattern, re)
	return re, nil
}

// PatternNames 返回内置规则名,按编号排序。
func PatternNames() []string {
	out := make([]string, 0, len(byName))
	for k := range byName {
		out = append(out, k)
	}
	sort.Strings(out)
	return out
}
