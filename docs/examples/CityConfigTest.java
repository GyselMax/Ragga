import be.magycks.ragga.generation.CityConfig;
import be.magycks.ragga.generation.CityConfig.Starter;

import java.util.List;

public class CityConfigTest {
    static int failures = 0;

    static void check(String label, boolean ok) {
        System.out.println((ok ? "PASS " : "FAIL ") + label);
        if (!ok) failures++;
    }

    static void expectReject(String label, Runnable r) {
        try {
            r.run();
            check(label + " (not rejected!)", false);
        } catch (IllegalArgumentException e) {
            System.out.println("PASS " + label + " -> " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        // matches application.properties + the hardcoded constants + the starter in GraphGenerator
        CityConfig defaults = new CityConfig(1500, 1500, 40, 80, 16, 1, 4, 120, 30, 4,
                List.of(new Starter(750, 750, 5.5)), 67);

        String code = defaults.encode();
        System.out.println("default code: " + code + "  (" + code.length() + " chars)");

        check("round trip", CityConfig.decode(code).equals(defaults));
        check("plain number = defaults + seed", CityConfig.parse("67", defaults).equals(defaults));
        check("parse full code", CityConfig.parse(code, defaults).equals(defaults));
        check("parse with whitespace", CityConfig.parse("  " + code + "\n", defaults).equals(defaults));
        check("normalized() of clean config is a no-op", defaults.normalized().equals(defaults));
        check("starts with A", code.startsWith("A"));

        CityConfig odd = new CityConfig(1500, 1500, 40.04, 80, 16, 1.23, 4, 120, 30.4, 4,
                List.of(new Starter(750.4, 750, 5.44)), 67);
        System.out.println("odd normalized: " + odd.normalized());
        check("normalized is stable", odd.normalized().normalized().equals(odd.normalized()));
        check("odd != normalized (quantization happened)", !odd.equals(odd.normalized()));

        CityConfig multi = new CityConfig(1500, 1500, 40, 80, 16, 1, 4, 120, 30, 4,
                List.of(new Starter(750, 750, 5.5), new Starter(700, 500, 2.5), new Starter(500, 300, 2.5)),
                -123456789012345L);
        String multiCode = multi.encode();
        System.out.println("3 starters, negative seed: " + multiCode + "  (" + multiCode.length() + " chars)");
        check("multi round trip", CityConfig.decode(multiCode).equals(multi));

        CityConfig max = new CityConfig(65535, 65535, 6553.5, 6553.5, 255, 25.5, 25.5, 65535, 180, 255,
                List.of(new Starter(65534, 65534, 25.5)), Long.MAX_VALUE);
        check("max values round trip", CityConfig.decode(max.encode()).equals(max));

        // every single-character typo in the default code
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
        int typos = 0, caught = 0;
        for (int i = 0; i < code.length(); i++) {
            for (char c : alphabet.toCharArray()) {
                if (c == code.charAt(i)) continue;
                String broken = code.substring(0, i) + c + code.substring(i + 1);
                typos++;
                try {
                    CityConfig.decode(broken);
                } catch (IllegalArgumentException e) {
                    caught++;
                }
            }
        }
        System.out.printf("single-char typos caught: %d / %d (%.2f%%)%n", caught, typos, 100.0 * caught / typos);

        expectReject("truncated", () -> CityConfig.decode(code.substring(0, 20)));
        expectReject("garbage", () -> CityConfig.decode("hello world!"));
        expectReject("empty", () -> CityConfig.decode(""));
        expectReject("width too big", () -> new CityConfig(70000, 1500, 40, 80, 16, 1, 4, 120, 30, 4,
                List.of(new Starter(750, 750, 5.5)), 67));
        expectReject("starter outside grid", () -> new CityConfig(1500, 1500, 40, 80, 16, 1, 4, 120, 30, 4,
                List.of(new Starter(1600, 750, 5.5)), 67));
        expectReject("NaN weight", () -> new CityConfig(1500, 1500, 40, 80, 16, Double.NaN, 4, 120, 30, 4,
                List.of(new Starter(750, 750, 5.5)), 67));
        expectReject("no starters", () -> new CityConfig(1500, 1500, 40, 80, 16, 1, 4, 120, 30, 4, List.of(), 67));

        System.out.println(failures == 0 ? "ALL PASSED" : failures + " FAILED");
    }
}
