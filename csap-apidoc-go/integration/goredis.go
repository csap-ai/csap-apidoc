package integration

import (
	"context"
	"errors"

	"github.com/redis/go-redis/v9"
)

// GoRedis 把 go-redis v9 适配成 strategy/redis 的 Client 接口(文档里的示例就是这段)。
type GoRedis struct{ C redis.UniversalClient }

func (g GoRedis) Eval(ctx context.Context, script string, keys []string, args ...any) (int64, error) {
	return redis.NewScript(script).Run(ctx, g.C, keys, args...).Int64()
}

func (g GoRedis) Get(ctx context.Context, key string) (string, bool, error) {
	v, err := g.C.Get(ctx, key).Result()
	if errors.Is(err, redis.Nil) {
		return "", false, nil
	}
	return v, err == nil, err
}

func (g GoRedis) HGet(ctx context.Context, key, field string) (string, bool, error) {
	v, err := g.C.HGet(ctx, key, field).Result()
	if errors.Is(err, redis.Nil) {
		return "", false, nil
	}
	return v, err == nil, err
}

func (g GoRedis) HGetAll(ctx context.Context, key string) (map[string]string, error) {
	return g.C.HGetAll(ctx, key).Result()
}

func (g GoRedis) LRange(ctx context.Context, key string, start, stop int64) ([]string, error) {
	return g.C.LRange(ctx, key, start, stop).Result()
}

func (g GoRedis) Subscribe(ctx context.Context, channel string, fn func(string)) error {
	ps := g.C.Subscribe(ctx, channel)
	defer ps.Close()
	for {
		select {
		case <-ctx.Done():
			return ctx.Err()
		case m, ok := <-ps.Channel():
			if !ok {
				return errors.New("subscription closed")
			}
			fn(m.Payload)
		}
	}
}
