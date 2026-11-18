# 规则导入

`strategy.Import` 把一个规则来源(YAML、SQL、Redis、Memory 等任何 `strategy.Strategy`)里的规则导入可写的目标(SQL、Redis、Memory),用于从 YAML 目录迁到数据库或 Redis。

```go
src := yamlstrategy.New(yamlstrategy.Options{Dir: "rules", Poll: -1})
res, err := strategy.Import(ctx, src, dst, strategy.ImportOptions{
    DryRun: true, // 先看会发生什么,不写入
})
fmt.Println(res.Created, res.Unchanged, res.Conflicts)
```

- **默认不覆盖**:目标里已有且内容不同的规则列入 `Conflicts`,不写入;确认后用 `Overwrite: true` 覆盖(列入 `Updated`)。
- **相同的不写**:按 JSON 结构比较,内容相同的列入 `Unchanged`,不加版本号、不产生修改记录。
- **空规则跳过**。
- **修改人**默认 `migrate`(`ImportOptions.By` 可改)。写入走 `Save`,所以导入也留修改记录,可以回滚。
- 某条写入失败不会中断,结果里保留已成功的,错误用 `errors.Join` 汇总返回。
- 来源按"全部规则"读取。只想导入一部分时,先包一层只返回所需规则的 `Strategy`。
- 目标的服务名由目标自己的 `Service` 决定;Go 的规则键是 `分组.接口名`,导入时原样保留。
