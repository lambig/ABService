package com.abservice.quality;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.lang.document.FileId;
import net.sourceforge.pmd.lang.rule.RuleSet;

/**
 * Runs the production rule against accepted and rejected source shapes without
 * application dependencies.
 */
public final class PolicyRuleRegression {
    private static final String PRELUDE = """
            package com.abservice.domain.model.policy;
            interface Policy<T> {
                static <T> Policy<T> of(java.util.function.Predicate<T> predicate) { return null; }
                default T verify(T value) { return value; }
            }
            """;

    public static void main(String[] args) throws Exception {
        Path reportPath = Path.of(args[1]);
        Files.deleteIfExists(reportPath);
        Map<String, String> cases = new LinkedHashMap<>();
        cases.put("SplitFields", """
                record Query(int page, String albumId) {
                    private static final Policy<Integer> PAGE = Policy.of(v -> v >= 0);
                    private static final Policy<String> ALBUM_ID = Policy.of(v -> !v.isEmpty());
                    Object validate() { // violation
                        return java.util.Optional.of(PAGE.verify(page))
                            .map(p -> ALBUM_ID.verify(albumId));
                    }
                }
                """);
        cases.put("SplitMethods", """
                class Query {
                    private Policy<Integer> page() { return Policy.of(v -> v >= 0); }
                    private Policy<String> id() { return Policy.of(v -> !v.isEmpty()); }
                    void validate() { page().verify(0); id().verify("id"); } // violation
                }
                """);
        cases.put("MixedQualified", """
                class Query {
                    private static final Policy<Integer> PAGE = Policy.of(v -> true);
                    private Policy<Integer> id() { return Policy.of(v -> true); }
                    void validate() { Query.PAGE.verify(0); this.id().verify(0); } // violation
                }
                """);
        cases.put("SplitInitializer", """
                class Query {
                    private static final Policy<Integer> A = Policy.of(v -> true);
                    private static final Policy<Integer> B = Policy.of(v -> true);
                    private static final Object BOTH = java.util.List.of(A, B); // violation
                }
                """);
        cases.put("MethodReferences", """
                class Query {
                    private Policy<Integer> a() { return Policy.of(v -> true); }
                    private Policy<Integer> b() { return Policy.of(v -> true); }
                    void validate() { // violation
                        java.util.function.Supplier<Policy<Integer>> x = this::a;
                        java.util.function.Supplier<Policy<Integer>> y = this::b;
                    }
                }
                """);
        cases.put("Inline", """
                class Query {
                    void validate() {
                        Policy.<Integer>of(v -> v >= 0).verify(0);
                        Policy.<String>of(v -> !v.isEmpty()).verify("id");
                    }
                }
                """);
        cases.put("WholePolicy", """
                class Query {
                    private static final Policy<Integer> INPUT = Policy.of(v -> v >= 0 && v <= 10);
                    void validate() { INPUT.verify(0); }
                }
                """);
        cases.put("Shared", """
                class Query {
                    private static final Policy<Integer> PAGE = Policy.of(v -> true);
                    private Policy<Integer> id() { return Policy.of(v -> true); }
                    void first() { PAGE.verify(0); id().verify(0); }
                    void second() { PAGE.verify(1); id().verify(1); }
                }
                """);
        cases.put("SeparateStages", """
                class Query {
                    private static final Policy<Integer> PAGE = Policy.of(v -> true);
                    private static final Policy<Integer> ID = Policy.of(v -> true);
                    void first() { PAGE.verify(0); }
                    void second() { ID.verify(0); }
                }
                """);
        cases.put("ShadowedNames", """
                class Query {
                    private static final Policy<Integer> PAGE = Policy.of(v -> true);
                    private static final Policy<Integer> ID = Policy.of(v -> true);
                    void validate(Policy<Integer> PAGE, Policy<Integer> ID) { PAGE.verify(0); ID.verify(0); }
                    void first() { PAGE.verify(0); }
                    void second() { ID.verify(0); }
                }
                """);
        cases.put("UnrelatedType", """
                class Query {
                    private static final String PAGE = "page";
                    private static final String ID = "id";
                    void validate() { System.out.println(PAGE + ID); }
                }
                """);
        cases.put("NestedScopes", """
                class Outer {
                    private static final Policy<Integer> PAGE = Policy.of(v -> true);
                    void first() { PAGE.verify(0); }
                    static class Inner {
                        private static final Policy<Integer> PAGE = Policy.of(v -> true);
                        private static final Policy<Integer> ID = Policy.of(v -> true);
                        void validate() { PAGE.verify(0); ID.verify(0); } // violation
                    }
                }
                """);
        cases.put("Constructor", """
                class Query {
                    private static final Policy<Integer> PAGE = Policy.of(v -> true);
                    private static final Policy<Integer> ID = Policy.of(v -> true);
                    Query() { PAGE.verify(0); ID.verify(0); } // violation
                }
                """);
        cases.put("OverloadedMethods", """
                class Query {
                    private Policy<Integer> page() { return Policy.of(v -> true); }
                    private Policy<Integer> page(int value) { return Policy.of(v -> true); }
                    private Policy<Integer> id() { return Policy.of(v -> true); }
                    void validate() { page().verify(0); id().verify(0); } // violation
                    void other() { page(1).verify(1); }
                }
                """);
        cases.put("ReusedReference", """
                class Query {
                    private Policy<Integer> page() { return Policy.of(v -> true); }
                    private Policy<Integer> id() { return Policy.of(v -> true); }
                    void validate() { page().verify(0); id().verify(0); }
                    java.util.function.Supplier<Policy<Integer>> other() { return this::page; }
                }
                """);
        StringBuilder results = new StringBuilder();
        for (var entry : cases.entrySet()) {
            String source = PRELUDE + entry.getValue();
            var expected = source.lines().toList();
            var expectedLines = java.util.stream.IntStream.range(0, expected.size())
                    .filter(i -> expected.get(i).contains("// violation")).map(i -> i + 1).boxed().toList();
            PMDConfiguration config = new PMDConfiguration();
            config.setIgnoreIncrementalAnalysis(true);
            config.setThreads(1);
            try (PmdAnalysis analysis = PmdAnalysis.create(config)) {
                var rule = analysis.newRuleSetLoader().loadFromResource(args[0]).getRuleByName("ScatteredPolicy");
                if (rule == null) {
                    throw new AssertionError("Production ruleset must enable ScatteredPolicy");
                }
                analysis.addRuleSet(RuleSet.forSingleRule(rule));
                analysis.files().addSourceFile(FileId.fromPathLikeString(entry.getKey() + ".java"), source);
                var report = analysis.performAnalysisAndCollectReport();
                if (!report.getProcessingErrors().isEmpty() || !report.getConfigurationErrors().isEmpty()) {
                    throw new AssertionError(report.getProcessingErrors() + " " + report.getConfigurationErrors());
                }
                var actual = report.getViolations().stream().map(v -> v.getBeginLine()).sorted().toList();
                if (!actual.equals(expectedLines)) {
                    throw new AssertionError(entry.getKey() + ": expected " + expectedLines + ", actual " + actual);
                }
                results.append(entry.getKey()).append(": PASS ").append(actual).append('\n');
            }
        }
        Files.createDirectories(reportPath.getParent());
        Files.writeString(reportPath, results);
        System.out.print(results);
    }
}
