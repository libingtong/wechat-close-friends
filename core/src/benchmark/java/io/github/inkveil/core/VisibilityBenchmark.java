package io.github.inkveil.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class VisibilityBenchmark {
    private static final int OPERATIONS = 1_000_000;
    private static volatile long sink;

    public static void main(String[] arguments) {
        System.out.println("主机 JVM 热路径基准；非 Android 真机指标；单位 ns/op；7 轮中位值。");
        for (int size : new int[] {100, 1_000, 10_000}) {
            List<String> identifiers = new ArrayList<>(size);
            for (int index = 0; index < size; index++) {
                identifiers.add("wxid_" + index);
            }
            String hidden = identifiers.get(size / 2);
            String temporary = identifiers.get(size - 1);
            VisibilityEngine active = new VisibilityEngine();
            active.publish(VisibilitySnapshot.create(true, Surface.ALL_MASK, identifiers,
                    java.util.Collections.singletonList(temporary)));
            VisibilityEngine disabled = new VisibilityEngine();
            disabled.publish(VisibilitySnapshot.create(false, Surface.ALL_MASK, identifiers,
                    java.util.Collections.emptyList()));
            benchmark(size, "可见对象", active, "wxid_unknown", false);
            benchmark(size, "隐藏对象", active, hidden, true);
            benchmark(size, "临时显示", active, temporary, false);
            benchmark(size, "总开关关闭", disabled, hidden, false);
        }
        System.out.println("防消除校验：" + sink);
    }

    private static void benchmark(int size, String scenario, VisibilityEngine engine,
            String identifier, boolean expected) {
        for (int warmup = 0; warmup < 5; warmup++) {
            run(engine, identifier);
        }
        long[] timings = new long[7];
        for (int round = 0; round < timings.length; round++) {
            long started = System.nanoTime();
            long result = run(engine, identifier);
            timings[round] = System.nanoTime() - started;
            if (result != (expected ? OPERATIONS : 0)) {
                throw new IllegalStateException("基准正确性校验失败：" + scenario);
            }
        }
        Arrays.sort(timings);
        System.out.printf(Locale.ROOT, "名单=%d 场景=%s 中位值=%.2f ns/op%n",
                size, scenario, timings[timings.length / 2] / (double) OPERATIONS);
    }

    private static long run(VisibilityEngine engine, String identifier) {
        long matches = 0;
        for (int operation = 0; operation < OPERATIONS; operation++) {
            if (engine.shouldHide(identifier, Surface.CONVERSATION)) {
                matches++;
            }
        }
        sink += matches;
        return matches;
    }
}
