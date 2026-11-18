package ai.csap.apidoc.mock;

import java.nio.charset.StandardCharsets;

/**
 * 可复现的随机数:每个节点用自己的路径派生一个 64 位无符号数,与生成顺序无关
 * (规则见 docs/design/1.4.0-mock-schema.md 2.5.1)。
 *
 * @author ycf
 * @since 1.4.0
 */
public final class MockRandom {

    private MockRandom() {
    }

    /**
     * 节点的随机数 r = splitmix64(fnv1a64(UTF-8(seed + "\n" + path)))
     *
     * @param seed 种子
     * @param path 节点路径($、$.a.b、$.list[0])
     * @return 64 位无符号数(用 long 表示,取模请用 {@link Long#remainderUnsigned})
     */
    public static long of(String seed, String path) {
        return splitmix64(fnv1a64((seed + "\n" + path).getBytes(StandardCharsets.UTF_8)));
    }

    static long fnv1a64(byte[] data) {
        long h = 0xcbf29ce484222325L;
        for (byte b : data) {
            h = (h ^ (b & 0xffL)) * 0x100000001b3L;
        }
        return h;
    }

    static long splitmix64(long x) {
        x += 0x9e3779b97f4a7c15L;
        long z = x;
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    /**
     * r % n(无符号)
     *
     * @param r 随机数
     * @param n 正整数
     * @return 余数
     */
    public static long mod(long r, long n) {
        return Long.remainderUnsigned(r, n);
    }
}
