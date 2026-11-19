package ai.csap.apidoc.team.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 行级统一差异(unified diff)。对规则 JSON 先 {@link Json#pretty} 再比较。 */
public final class UnifiedDiff {

    private static final int CONTEXT = 3;
    private static final long MAX_CELLS = 4_000_000L;

    private UnifiedDiff() {
    }

    /** 两份规则 JSON(null 视为空)的差异;完全相同返回空字符串。 */
    public static String ofRules(String before, String after, String fromLabel, String toLabel) {
        return diff(lines(Json.pretty(before)), lines(Json.pretty(after)), fromLabel, toLabel);
    }

    private static List<String> lines(String s) {
        return s == null || s.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(s.split("\r?\n", -1)));
    }

    public static String diff(List<String> a, List<String> b, String fromLabel, String toLabel) {
        int pre = 0;
        while (pre < a.size() && pre < b.size() && a.get(pre).equals(b.get(pre))) {
            pre++;
        }
        int suf = 0;
        while (suf < a.size() - pre && suf < b.size() - pre && a.get(a.size() - 1 - suf).equals(b.get(b.size() - 1 - suf))) {
            suf++;
        }
        if (pre == a.size() && pre == b.size()) {
            return "";
        }
        List<String> ma = a.subList(pre, a.size() - suf);
        List<String> mb = b.subList(pre, b.size() - suf);
        // 中间不同的部分:op 序列 ' ' 相同 '-' 删除 '+' 新增
        List<int[]> ops = new ArrayList<>(); // {type, indexInA, indexInB}
        for (int i = 0; i < pre; i++) {
            ops.add(new int[] {0, i, i});
        }
        if ((long) ma.size() * mb.size() > MAX_CELLS) {
            for (int i = 0; i < ma.size(); i++) {
                ops.add(new int[] {-1, pre + i, -1});
            }
            for (int j = 0; j < mb.size(); j++) {
                ops.add(new int[] {1, -1, pre + j});
            }
        } else {
            int n = ma.size();
            int m = mb.size();
            int[][] lcs = new int[n + 1][m + 1];
            for (int i = n - 1; i >= 0; i--) {
                for (int j = m - 1; j >= 0; j--) {
                    lcs[i][j] = ma.get(i).equals(mb.get(j)) ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
                }
            }
            int i = 0;
            int j = 0;
            while (i < n && j < m) {
                if (ma.get(i).equals(mb.get(j))) {
                    ops.add(new int[] {0, pre + i, pre + j});
                    i++;
                    j++;
                } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                    ops.add(new int[] {-1, pre + i, -1});
                    i++;
                } else {
                    ops.add(new int[] {1, -1, pre + j});
                    j++;
                }
            }
            while (i < n) {
                ops.add(new int[] {-1, pre + i++, -1});
            }
            while (j < m) {
                ops.add(new int[] {1, -1, pre + j++});
            }
        }
        for (int k = 0; k < suf; k++) {
            ops.add(new int[] {0, a.size() - suf + k, b.size() - suf + k});
        }
        return render(ops, a, b, fromLabel, toLabel);
    }

    private static String render(List<int[]> ops, List<String> a, List<String> b, String fromLabel, String toLabel) {
        StringBuilder sb = new StringBuilder();
        sb.append("--- ").append(fromLabel).append('\n').append("+++ ").append(toLabel).append('\n');
        int idx = 0;
        int size = ops.size();
        while (idx < size) {
            // 找下一个变更
            while (idx < size && ops.get(idx)[0] == 0) {
                idx++;
            }
            if (idx >= size) {
                break;
            }
            int start = Math.max(0, idx - CONTEXT);
            int end = idx;
            int lastChange = idx;
            while (end < size) {
                if (ops.get(end)[0] != 0) {
                    lastChange = end;
                } else if (end - lastChange > CONTEXT * 2) {
                    break;
                }
                end++;
            }
            int hunkEnd = Math.min(size, lastChange + CONTEXT + 1);
            int aStart = -1;
            int bStart = -1;
            int aCount = 0;
            int bCount = 0;
            StringBuilder body = new StringBuilder();
            for (int k = start; k < hunkEnd; k++) {
                int[] op = ops.get(k);
                if (op[0] != 1) {
                    if (aStart < 0) {
                        aStart = op[1];
                    }
                    aCount++;
                }
                if (op[0] != -1) {
                    if (bStart < 0) {
                        bStart = op[2];
                    }
                    bCount++;
                }
                if (op[0] == 0) {
                    body.append(' ').append(a.get(op[1])).append('\n');
                } else if (op[0] == -1) {
                    body.append('-').append(a.get(op[1])).append('\n');
                } else {
                    body.append('+').append(b.get(op[2])).append('\n');
                }
            }
            if (aStart < 0) {
                aStart = lineAfter(ops, start, true);
            }
            if (bStart < 0) {
                bStart = lineAfter(ops, start, false);
            }
            sb.append("@@ -").append(aCount == 0 ? aStart : aStart + 1).append(',').append(aCount)
              .append(" +").append(bCount == 0 ? bStart : bStart + 1).append(',').append(bCount).append(" @@\n").append(body);
            idx = hunkEnd;
        }
        return sb.toString();
    }

    private static int lineAfter(List<int[]> ops, int start, boolean left) {
        for (int k = start; k < ops.size(); k++) {
            int v = left ? ops.get(k)[1] : ops.get(k)[2];
            if (v >= 0) {
                return v;
            }
        }
        return 0;
    }
}
