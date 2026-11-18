package mock

// 可复现的随机数(docs/design/1.4.0-mock-schema.md 2.5.1):不使用语言自带的随机数,
// 每个节点用自己的路径派生一个 64 位无符号数,与生成顺序无关。

const (
	fnvOffset = 0xcbf29ce484222325
	fnvPrime  = 0x100000001b3
)

func fnv1a64(b []byte) uint64 {
	h := uint64(fnvOffset)
	for _, c := range b {
		h = (h ^ uint64(c)) * fnvPrime
	}
	return h
}

func splitmix64(x uint64) uint64 {
	x += 0x9e3779b97f4a7c15
	z := x
	z = (z ^ (z >> 30)) * 0xbf58476d1ce4e5b9
	z = (z ^ (z >> 27)) * 0x94d049bb133111eb
	return z ^ (z >> 31)
}

// rnd 返回种子和路径对应的随机数:splitmix64(fnv1a64(UTF-8(seed + "\n" + path)))。
func rnd(seed, path string) uint64 { return splitmix64(fnv1a64([]byte(seed + "\n" + path))) }
