// Package postman 把文档模型导出为 Postman Collection v2.1(对应 Java /csap/openapi/postman,
// 即 ai.csap.apidoc.service.standard.PostmanConverterService#convertToPostmanCollection)。
//
// 输出逐项对齐 Java:字段顺序、默认值、示例值规则、URL 拆分、响应示例,以及 Java 手写 JSON
// 序列化的格式(紧凑无缩进、只转义 \ " \n \r \t、中文与其它字符原样输出)。Java 实现里没有 UUID、
// 时间戳等随机值,所以输出是确定性的,可以逐字节一致。已知差别见 postman.go 顶部注释。
package postman
