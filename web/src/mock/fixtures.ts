// mock 用的 /csap/apidoc 文档夹具(结构取自开源版文档 JSON)。仅开发 / 测试使用。
import type { DocParam, DocPayload } from '@/api/types';

const p = (name: string, dataType: string, value: string, extra: Partial<DocParam> = {}): DocParam => ({
  name, dataType, value, description: value, required: false, example: '', defaultValue: '', length: 0, decimals: 0, hidden: false, ...extra,
});

interface M { key: string; value: string; desc: string; methods: string[]; paths: string[]; req: { name: string; params: DocParam[] }[]; res: DocParam[] }
interface C { value: string; name: string; desc: string; path: string; methods: M[] }

function build(title: string, version: string, ctrls: C[]): DocPayload {
  return {
    code: '0', message: '成功',
    data: {
      apiInfo: { title, version, description: `${title} 的接口文档` },
      apiList: ctrls.map((c) => ({
        value: c.value, name: c.name, simpleName: c.name.split('.').pop(), description: c.desc, path: [c.path],
        methodList: c.methods.map((m) => ({
          key: m.key, value: m.value, name: m.key.split('.').pop() as string, description: m.desc, methods: m.methods, paths: m.paths, apiPath: [c.path],
          request: m.req.map((r) => ({ name: r.name, modelType: 'T_OBJECT', parameters: r.params })),
          response: [{ name: 'Response', description: '通用响应结果', modelType: 'T_OBJECT', parameters: m.res }],
          methodHeaders: [{ key: 'Content-Type', value: 'application/json', required: true, description: '上下文类型' }],
        })),
      })),
    },
  };
}

const wrap = (dataType: string, children: DocParam[]): DocParam[] => [
  p('code', 'Integer', '状态码', { example: '200' }),
  p('message', 'String', '提示信息'),
  p('data', dataType, '响应数据', { parameters: children, children: { parameters: children } }),
];

const orderFields = [
  p('orderId', 'Long', '订单号'),
  p('amount', 'BigDecimal', '订单金额', { decimals: 2, example: '99.50' }),
  p('status', 'String', '订单状态', { example: 'PAID' }),
  p('items', 'List<OrderItem>', '订单明细', { parameters: [p('sku', 'String', '商品编码'), p('count', 'Integer', '数量')] }),
];

export function fixtureDocs(): Record<string, DocPayload> {
  return {
    'order-service': build('订单服务', '1.5.0', [
      {
        value: '订单管理', name: 'com.demo.order.OrderController', desc: '订单的创建、查询与取消', path: '/api/orders',
        methods: [
          { key: 'com.demo.order.OrderController.create', value: '创建订单', desc: '根据购物车创建订单', methods: ['POST'], paths: ['/create'],
            req: [{ name: 'CreateOrderRequest', params: [
              p('userId', 'Long', '用户 ID', { required: true }),
              p('amount', 'BigDecimal', '订单金额', { required: true, decimals: 2, example: '99.50' }),
              p('remark', 'String', '备注', { length: 200 }),
              p('items', 'List<OrderItem>', '订单明细', { required: true, parameters: [p('sku', 'String', '商品编码', { required: true, length: 32 }), p('count', 'Integer', '数量', { required: true })] }),
            ] }],
            res: wrap('Order', orderFields) },
          { key: 'com.demo.order.OrderController.detail', value: '订单详情', desc: '按订单号查询订单', methods: ['GET'], paths: ['/{orderId}'],
            req: [{ name: 'Long', params: [p('orderId', 'Long', '订单号', { required: true })] }], res: wrap('Order', orderFields) },
          { key: 'com.demo.order.OrderController.cancel', value: '取消订单', desc: '取消尚未支付的订单', methods: ['POST'], paths: ['/{orderId}/cancel'],
            req: [{ name: 'CancelRequest', params: [p('orderId', 'Long', '订单号', { required: true }), p('reason', 'String', '取消原因', { length: 100 })] }], res: wrap('Void', []) },
        ],
      },
    ]),
    'user-service': build('用户服务', '1.5.0', [
      {
        value: '用户管理', name: 'com.demo.user.UserController', desc: '用户注册、资料查询', path: '/api/users',
        methods: [
          { key: 'com.demo.user.UserController.register', value: '用户注册', desc: '通过手机号注册用户', methods: ['POST'], paths: ['/register'],
            req: [{ name: 'RegisterRequest', params: [
              p('phone', 'String', '手机号', { required: true, length: 11 }),
              p('password', 'String', '密码', { required: true, length: 32 }),
              p('nickname', 'String', '昵称', { length: 20 }),
            ] }],
            res: wrap('User', [p('userId', 'Long', '用户 ID'), p('nickname', 'String', '昵称')]) },
          { key: 'com.demo.user.UserController.profile', value: '用户资料', desc: '查询当前用户资料', methods: ['GET'], paths: ['/profile'],
            req: [], res: wrap('User', [p('userId', 'Long', '用户 ID'), p('nickname', 'String', '昵称'), p('phone', 'String', '手机号')]) },
        ],
      },
    ]),
    'gateway-go': build('网关服务(Go)', '0.3.0', [
      {
        value: '路由', name: 'gateway.RouteHandler', desc: '路由配置查询与刷新', path: '/admin/routes',
        methods: [
          { key: 'gateway.RouteHandler.List', value: '路由列表', desc: '列出全部路由', methods: ['GET'], paths: [''],
            req: [], res: wrap('List<Route>', [p('path', 'string', '匹配路径'), p('upstream', 'string', '上游地址')]) },
          { key: 'gateway.RouteHandler.Reload', value: '重载路由', desc: '重新加载路由表', methods: ['POST'], paths: ['/reload'],
            req: [], res: wrap('Void', []) },
        ],
      },
    ]),
  };
}

export const seedRules: Record<string, Record<string, string>> = {
  'order-service': {
    'com.demo.order.OrderController.create': JSON.stringify({ amount: { required: true, min: 0.01, message: '订单金额必须大于 0' }, remark: { maxLength: 200 } }),
    'com.demo.order.OrderController.cancel': JSON.stringify({ reason: { maxLength: 100 } }),
  },
  'user-service': {
    'com.demo.user.UserController.register': JSON.stringify({ phone: { required: true, pattern: '^1\\d{10}$' }, password: { minLength: 8, maxLength: 32 } }),
  },
};
