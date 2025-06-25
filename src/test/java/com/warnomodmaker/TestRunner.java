package test.java.com.warnomodmaker;

import java.util.*;

/**
 * Enhanced Test Framework for E2E Testing
 */
public class TestRunner {
    private List<TestCase> testCases = new ArrayList<>();
    private List<TestProgressListener> progressListeners = new ArrayList<>();
    private TestCategory currentCategory = TestCategory.GENERAL;

    public enum TestCategory {
        SETUP("Setup & Initialization"),
        PARSING("File Parsing"),
        MODEL("Model Integrity"),
        MODIFICATIONS("Modifications"),
        FEATURES("New Features"),
        WRITING("File Writing"),
        STRESS("Stress Tests"),
        GENERAL("General");

        private final String displayName;
        TestCategory(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    public interface TestProgressListener {
        void onTestStarted(String testName, TestCategory category);
        void onTestCompleted(TestResult result);
        void onAllTestsCompleted(TestResults results);
    }

    public void addProgressListener(TestProgressListener listener) {
        progressListeners.add(listener);
    }

    public void setCurrentCategory(TestCategory category) {
        this.currentCategory = category;
    }

    public void addTest(String name, TestExecutor executor) {
        testCases.add(new TestCase(name, executor, currentCategory));
    }

    public void addTest(String name, TestExecutor executor, TestCategory category) {
        testCases.add(new TestCase(name, executor, category));
    }
    
    public List<TestCase> getTestCases() {
        return new ArrayList<>(testCases);
    }

    public TestResults runAll() {
        List<TestResult> results = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        for (TestCase testCase : testCases) {
            // Notify listeners test is starting
            for (TestProgressListener listener : progressListeners) {
                listener.onTestStarted(testCase.name, testCase.category);
            }

            long testStart = System.currentTimeMillis();
            TestResult result;
            try {
                testCase.executor.execute();
                long duration = System.currentTimeMillis() - testStart;
                result = new TestResult(testCase.name, true, null, duration, testCase.category);
                System.out.println("+ " + testCase.name + " (" + duration + "ms)");
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - testStart;
                result = new TestResult(testCase.name, false, e, duration, testCase.category);
                System.err.println("X " + testCase.name + " (" + duration + "ms): " + e.getMessage());
            }
            
            results.add(result);
            
            // Notify listeners test completed
            for (TestProgressListener listener : progressListeners) {
                listener.onTestCompleted(result);
            }
        }

        long totalDuration = System.currentTimeMillis() - startTime;
        TestResults testResults = new TestResults(results, totalDuration);
        
        // Notify listeners all tests completed
        for (TestProgressListener listener : progressListeners) {
            listener.onAllTestsCompleted(testResults);
        }
        
        return testResults;
    }

    @FunctionalInterface
    public interface TestExecutor {
        void execute() throws Exception;
    }

    public static class TestCase {
        public final String name;
        public final TestExecutor executor;
        public final TestCategory category;

        public TestCase(String name, TestExecutor executor, TestCategory category) {
            this.name = name;
            this.executor = executor;
            this.category = category;
        }
    }

    public static class TestResult {
        public final String testName;
        public final boolean passed;
        public final Exception failure;
        public final long durationMs;
        public final TestCategory category;

        public TestResult(String testName, boolean passed, Exception failure, long durationMs, TestCategory category) {
            this.testName = testName;
            this.passed = passed;
            this.failure = failure;
            this.durationMs = durationMs;
            this.category = category;
        }
    }

    public static class TestResults {
        public final List<TestResult> results;
        public final long totalDurationMs;

        public TestResults(List<TestResult> results, long totalDurationMs) {
            this.results = results;
            this.totalDurationMs = totalDurationMs;
        }

        public int getPassedCount() {
            return (int) results.stream().filter(r -> r.passed).count();
        }

        public int getFailedCount() {
            return (int) results.stream().filter(r -> !r.passed).count();
        }

        public Map<TestCategory, List<TestResult>> getResultsByCategory() {
            Map<TestCategory, List<TestResult>> categoryMap = new HashMap<>();
            for (TestResult result : results) {
                categoryMap.computeIfAbsent(result.category, k -> new ArrayList<>()).add(result);
            }
            return categoryMap;
        }

        public double getAverageDuration() {
            return results.stream().mapToLong(r -> r.durationMs).average().orElse(0.0);
        }

        public long getTotalDuration() {
            return totalDurationMs;
        }
    }
}
