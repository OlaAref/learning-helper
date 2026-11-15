package com.olaaref.notionmirror.sync;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the difference between Notion's current Markdown and an edited local copy into targeted
 * search-and-replace edits for Notion's update_content command, so untouched blocks (images, columns,
 * child-page links) are never rewritten. Lines that differ only in signed-URL signatures count as equal.
 */
final class BodyDiff {
    private static final long MAX_CELLS = 16_000_000L;
    private static final int MAX_OPERATIONS = 100;

    private BodyDiff() {}

    /** A line holding a signed Notion file link. Its signature changes on every request, so it can never be matched. */
    static boolean signed(String line) {
        return line.contains("X-Amz-Signature=") || line.contains("X-Amz-Credential=");
    }

    /**
     * True when the edited text no longer contains a signed-link line (an existing image or file) that Notion's
     * text still has. Such a removal or change cannot be sent as a text edit.
     */
    static boolean removesSignedLine(String from, String to) {
        Map<String, Integer> left = new java.util.HashMap<>();
        for (String l : lines(to)) left.merge(PageFiles.canonical(l), 1, Integer::sum);
        for (String l : lines(from)) {
            if (!signed(l)) continue;
            String c = PageFiles.canonical(l);
            int have = left.getOrDefault(c, 0);
            if (have == 0) return true;
            left.put(c, have - 1);
        }
        return false;
    }

    /**
     * The edits, an empty list when nothing differs, or null when no safe set of edits could be built.
     * Every candidate is replayed here exactly as Notion applies it (each search text must match once, at its
     * turn) and must reproduce the edited text; otherwise more context is added, and finally the push is refused.
     */
    static List<Map<String, Object>> operations(String from, String to) {
        if (removesSignedLine(from, to)) return null;
        String expected = PageFiles.canonical(String.join("\n", lines(to)));
        for (int extra = 0; extra <= 6; extra++) {
            List<Map<String, Object>> ops = build(from, to, extra);
            if (ops == null) return null;
            if (ops.isEmpty()) return ops;
            String replay = replay(String.join("\n", lines(from)), ops);
            if (replay != null && PageFiles.canonical(replay).equals(expected)) return ops;
        }
        return null;
    }

    /** Applies the edits the way Notion documents it; null if one does not match exactly once at its turn. */
    static String replay(String text, List<Map<String, Object>> ops) {
        for (Map<String, Object> op : ops) {
            String o = (String) op.get("old_str");
            int first = text.indexOf(o);
            if (first < 0 || text.indexOf(o, first + 1) >= 0) return null;
            text = text.substring(0, first) + op.get("new_str") + text.substring(first + o.length());
        }
        return text;
    }

    private static List<Map<String, Object>> build(String from, String to, int extra) {
        List<String> a = lines(from);
        List<String> b = lines(to);
        List<String> ca = new ArrayList<>();
        for (String s : a) ca.add(PageFiles.canonical(s));
        List<String> cb = new ArrayList<>();
        for (String s : b) cb.add(PageFiles.canonical(s));

        int n = a.size(), m = b.size();
        int pre = 0;
        while (pre < n && pre < m && ca.get(pre).equals(cb.get(pre))) pre++;
        int suf = 0;
        while (suf < n - pre && suf < m - pre && ca.get(n - 1 - suf).equals(cb.get(m - 1 - suf))) suf++;
        int an = n - pre - suf, bn = m - pre - suf;
        if (an == 0 && bn == 0) return new ArrayList<>();
        if ((long) (an + 1) * (bn + 1) > MAX_CELLS) return null;

        int[][] dp = new int[an + 1][bn + 1];
        for (int i = an - 1; i >= 0; i--) {
            for (int j = bn - 1; j >= 0; j--) {
                dp[i][j] = ca.get(pre + i).equals(cb.get(pre + j))
                        ? dp[i + 1][j + 1] + 1
                        : Math.max(dp[i + 1][j], dp[i][j + 1]);
            }
        }

        // hunks as {fromStart, fromEnd, toStart, toEnd}, absolute line indexes, ends exclusive
        List<int[]> hunks = new ArrayList<>();
        int i = 0, j = 0;
        int[] open = null;
        while (i < an || j < bn) {
            if (i < an && j < bn && ca.get(pre + i).equals(cb.get(pre + j))) {
                if (open != null) {
                    open[1] = pre + i;
                    open[3] = pre + j;
                    hunks.add(open);
                    open = null;
                }
                i++;
                j++;
            } else {
                if (open == null) open = new int[] {pre + i, 0, pre + j, 0};
                if (j < bn && (i == an || dp[i][j + 1] >= dp[i + 1][j])) j++;
                else i++;
            }
        }
        if (open != null) {
            open[1] = pre + i;
            open[3] = pre + j;
            hunks.add(open);
        }
        if (hunks.size() > MAX_OPERATIONS) return null;

        String fromText = String.join("\n", a);
        List<Map<String, Object>> ops = new ArrayList<>();
        for (int k = 0; k < hunks.size(); k++) {
            int[] h = hunks.get(k);
            int low = k == 0 ? 0 : hunks.get(k - 1)[1];
            int high = k == hunks.size() - 1 ? n : hunks.get(k + 1)[0];
            int before = extra, after = extra;
            while (true) {
                int s = Math.max(low, h[0] - before);
                int e = Math.min(high, h[1] + after);
                // Context must never include a signed-link line: Notion could not match it.
                for (int p = h[0] - 1; p >= s; p--) {
                    if (signed(a.get(p))) {
                        s = p + 1;
                        break;
                    }
                }
                for (int p = h[1]; p < e; p++) {
                    if (signed(a.get(p))) {
                        e = p;
                        break;
                    }
                }
                String oldStr = String.join("\n", a.subList(s, e));
                List<String> replacement = new ArrayList<>(a.subList(s, h[0]));
                replacement.addAll(b.subList(h[2], h[3]));
                replacement.addAll(a.subList(h[1], e));
                // A deletion needs a neighbouring line in the edit, otherwise its line break would be left behind.
                if (!oldStr.isEmpty() && !replacement.isEmpty() && occurrences(fromText, oldStr) == 1) {
                    Map<String, Object> op = new LinkedHashMap<>();
                    op.put("old_str", oldStr);
                    op.put("new_str", String.join("\n", replacement));
                    op.put("replace_all_matches", false);
                    ops.add(op);
                    break;
                }
                boolean canBefore = h[0] - before > low && !signed(a.get(h[0] - before - 1));
                boolean canAfter = h[1] + after < high && !signed(a.get(h[1] + after));
                if (!canBefore && !canAfter) return null;
                if (canBefore) before++;
                if (canAfter) after++;
            }
        }
        return ops;
    }

    private static List<String> lines(String s) {
        return new ArrayList<>(List.of(s.replace("\r\n", "\n").strip().split("\n", -1)));
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + 1)) {
            if (++count > 1) break;
        }
        return count;
    }
}
