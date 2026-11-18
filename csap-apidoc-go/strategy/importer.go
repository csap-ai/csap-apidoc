package strategy

import (
	"context"
	"errors"
	"fmt"
	"reflect"
	"sort"
)

// Store 是既能读又能写的规则来源(SQL、Redis、Memory)。
type Store interface {
	Strategy
	Saver
}

// ImportOptions 是 Import 的参数。
type ImportOptions struct {
	// Overwrite 为 true 时覆盖目标里内容不同的规则;默认 false,这些规则列为冲突、不写入。
	Overwrite bool
	// DryRun 为 true 时只计算结果,不写入。
	DryRun bool
	// By 是写入时记的修改人,默认 "migrate"。
	By string
}

// ImportResult 是导入结果,各列表里是规则键(已排序)。
type ImportResult struct {
	Created   []string // 目标里没有,新增
	Updated   []string // 目标里内容不同,已覆盖(Overwrite)
	Unchanged []string // 目标里已有且内容相同,跳过
	Conflicts []string // 目标里已有且内容不同,没有覆盖(未设 Overwrite)
}

// Import 把 src 里的规则导入 dst,用于把 YAML 等来源迁到 SQL / Redis。
// 默认不覆盖已有的不同规则,返回冲突列表;空规则(没有任何字段)跳过;内容按结构比较,相同的不写,不产生修改记录。
// 导入本身用 Saver.Save 写入,记修改人 "migrate",所以目标支持修改记录时可以回滚。
// 某条写入失败不会中断,最后与其他失败一起返回(errors.Join),已成功的保留在结果里。
func Import(ctx context.Context, src Strategy, dst Store, opts ImportOptions) (*ImportResult, error) {
	if opts.By == "" {
		opts.By = "migrate"
	}
	from, err := src.Load(ctx)
	if err != nil {
		return nil, fmt.Errorf("apidoc: 读取来源 %s: %w", src.Name(), err)
	}
	existing, err := dst.Load(ctx)
	if err != nil {
		return nil, fmt.Errorf("apidoc: 读取目标 %s: %w", dst.Name(), err)
	}
	keys := make([]string, 0, len(from))
	for k, mr := range from {
		if !mr.IsEmpty() {
			keys = append(keys, k)
		}
	}
	sort.Strings(keys)
	res := &ImportResult{}
	var errs []error
	for _, k := range keys {
		mr := from[k]
		cur, ok := existing[k]
		switch {
		case !ok || cur.IsEmpty():
			if !opts.DryRun {
				if _, err := dst.Save(ctx, k, mr, opts.By); err != nil {
					errs = append(errs, fmt.Errorf("%s: %w", k, err))
					continue
				}
			}
			res.Created = append(res.Created, k)
		case sameMethodRules(cur, mr):
			res.Unchanged = append(res.Unchanged, k)
		case !opts.Overwrite:
			res.Conflicts = append(res.Conflicts, k)
		default:
			if !opts.DryRun {
				if _, err := dst.Save(ctx, k, mr, opts.By); err != nil {
					errs = append(errs, fmt.Errorf("%s: %w", k, err))
					continue
				}
			}
			res.Updated = append(res.Updated, k)
		}
	}
	return res, errors.Join(errs...)
}

func sameMethodRules(a, b MethodRules) bool {
	if len(a.Request) == 0 {
		a.Request = nil
	}
	if len(a.Response) == 0 {
		a.Response = nil
	}
	if len(b.Request) == 0 {
		b.Request = nil
	}
	if len(b.Response) == 0 {
		b.Response = nil
	}
	return reflect.DeepEqual(a, b)
}
