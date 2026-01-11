package fr.uga.pddl4j.examples.asp;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * BenchmarkRunner:
 * Lance automatiquement ASP (A*) et RWPlanner sur les 4 domaines:
 * blocks, depot, gripper, logistics.
 * Output: results/results.csv
 */
public class BenchmarkRunner {

    private static final Path PDDL_ROOT = Paths.get("resources/benchmarks/pddl");
    private static final List<String> DOMAINS = List.of("blocks", "depot", "gripper", "logistics");

    // si un planner bloque, on coupe.
    private static final long PROCESS_TIMEOUT_MS = 120_000; // 2 min max

    // MCTSPlanner params
    // MCTSPlanner params
    private static final List<String> MCTS_PARAMS = List.of(
            "-I", "300",
            "-R", "40",
            "-P", "500",
            "-C", "1.4"
    );


    // Optionnel: forcer timeout interne
    private static final List<String> COMMON_PLANNER_PARAMS = List.of(
            // "-t", "600"
    );

    // Résultats
    private static final Pattern RE_SUCCESS = Pattern.compile("^RESULT:\\s*SUCCESS\\s*$", Pattern.MULTILINE);
    private static final Pattern RE_FAILURE = Pattern.compile("^RESULT:\\s*FAILURE\\s*$", Pattern.MULTILINE);
    private static final Pattern RE_LEN = Pattern.compile("^RESULT:\\s*PLAN_LENGTH=(\\d+)\\s*$", Pattern.MULTILINE);
    private static final Pattern RE_RUNTIME = Pattern.compile("^RESULT:\\s*RUNTIME_MS=(\\d+)\\s*$", Pattern.MULTILINE);

    private static class RunResult {
        final boolean success;
        final long runtimeMs;
        final int planLength;
        final int exitCode;
        final boolean killedByTimeout;
        final String output;

        RunResult(boolean success, long runtimeMs, int planLength, int exitCode, boolean killedByTimeout, String output) {
            this.success = success;
            this.runtimeMs = runtimeMs;
            this.planLength = planLength;
            this.exitCode = exitCode;
            this.killedByTimeout = killedByTimeout;
            this.output = output;
        }
    }

    public static void main(String[] args) throws Exception {
        if (!Files.exists(PDDL_ROOT)) {
            System.err.println("PDDL root not found: " + PDDL_ROOT.toAbsolutePath());
            System.err.println("Vérifie ton working directory IntelliJ (doit être la racine du projet).");
            return;
        }

        // Prépare dossier results
        Path resultsDir = Paths.get("results");
        Files.createDirectories(resultsDir);
        Path csvPath = resultsDir.resolve("results.csv");

        // Collecter des donnees
        List<ProblemInstance> instances = collectInstances();

        System.out.println("==== BenchmarkRunner ====");
        System.out.println("Found instances: " + instances.size());
        System.out.println("CSV -> " + csvPath.toAbsolutePath());
        System.out.println("Time: " + LocalDateTime.now());
        System.out.println();

        // CSV
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"domain", "suite", "problem", "planner", "success", "runtime_ms", "plan_length", "exit_code", "timeout_killed"});

        for (ProblemInstance inst : instances) {
            System.out.println("[" + inst.domain + " / " + inst.suite + "] " + inst.problemFile.getFileName());

            // ASP
            RunResult asp = runPlanner("ASP", "fr.uga.pddl4j.examples.asp.ASP",
                    inst.domainFile, inst.problemFile, List.of());
            rows.add(toRow(inst, "ASP", asp));
            printShort("ASP", asp);

            // MCTSPlanner
            RunResult mcts = runPlanner("MCTS", "fr.uga.pddl4j.examples.asp.MCTSPlanner",
                    inst.domainFile, inst.problemFile, MCTS_PARAMS);
            rows.add(toRow(inst, "MCTS", mcts));
            printShort("MCTS", mcts);

            System.out.println();
        }

        writeCsv(csvPath, rows);
        System.out.println("Done. Saved CSV: " + csvPath.toAbsolutePath());
    }

    private static void printShort(String tag, RunResult r) {
        System.out.printf("  %s: ok=%s runtime=%dms len=%d exit=%d timeoutKilled=%s%n",
                tag, r.success, r.runtimeMs, r.planLength, r.exitCode, r.killedByTimeout);
    }

    private static String[] toRow(ProblemInstance inst, String planner, RunResult r) {
        return new String[]{
                inst.domain,
                inst.suite,
                inst.problemFile.getFileName().toString(),
                planner,
                String.valueOf(r.success),
                String.valueOf(r.runtimeMs),
                String.valueOf(r.planLength),
                String.valueOf(r.exitCode),
                String.valueOf(r.killedByTimeout)
        };
    }

    private static void writeCsv(Path path, List<String[]> rows) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            for (String[] row : rows) {
                w.write(Arrays.stream(row)
                        .map(BenchmarkRunner::csvEscape)
                        .collect(Collectors.joining(",")));
                w.newLine();
            }
        }
    }

    private static String csvEscape(String s) {
        if (s == null) return "";
        boolean needQuotes = s.contains(",") || s.contains("\"") || s.contains("\n");
        String out = s.replace("\"", "\"\"");
        return needQuotes ? "\"" + out + "\"" : out;
    }

    private static class ProblemInstance {
        final String domain;     // blocks/depot/gripper/logistics
        final String suite;      // sous-dossier (ex: strips-typed)
        final Path domainFile;   // .../domain.pddl
        final Path problemFile;  // .../p001.pddl etc

        ProblemInstance(String domain, String suite, Path domainFile, Path problemFile) {
            this.domain = domain;
            this.suite = suite;
            this.domainFile = domainFile;
            this.problemFile = problemFile;
        }
    }

    private static List<ProblemInstance> collectInstances() throws IOException {
        List<ProblemInstance> all = new ArrayList<>();

        for (String d : DOMAINS) {
            Path domainDir = PDDL_ROOT.resolve(d);
            if (!Files.exists(domainDir)) {
                System.out.println("[SKIP] missing domain folder: " + domainDir);
                continue;
            }

            // Find all domain.pddl files under this domain
            List<Path> domainPddls;
            try (var walk = Files.walk(domainDir)) {
                domainPddls = walk
                        .filter(p -> p.getFileName().toString().equalsIgnoreCase("domain.pddl"))
                        .sorted()
                        .collect(Collectors.toList());
            }

            for (Path domPddl : domainPddls) {
                Path suiteDir = domPddl.getParent(); // problems usually in same folder
                String suiteName = domainDir.relativize(suiteDir).toString().replace("\\", "/");
                if (suiteName.isEmpty()) suiteName = ".";

                // List problem files next to domain.pddl
                List<Path> problems;
                try (var ls = Files.list(suiteDir)) {
                    problems = ls
                            .filter(p -> p.toString().endsWith(".pddl"))
                            .filter(p -> !p.getFileName().toString().equalsIgnoreCase("domain.pddl"))
                            .sorted()
                            .limit(10) // On ne teste que 10
                            .collect(Collectors.toList());
                }

                for (Path prob : problems) {
                    all.add(new ProblemInstance(d, suiteName, domPddl, prob));
                }
            }
        }

        // Optional: sort globally
        all.sort(Comparator
                .comparing((ProblemInstance x) -> x.domain)
                .thenComparing(x -> x.suite)
                .thenComparing(x -> x.problemFile.getFileName().toString())
        );

        return all;
    }

    // ---------- Run planners via ProcessBuilder ----------

    private static RunResult runPlanner(String label,
                                        String mainClass,
                                        Path domainPddl,
                                        Path problemPddl,
                                        List<String> extraParams) throws Exception {

        String javaBin = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("java.class.path");

        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin);
        cmd.add("-cp");
        cmd.add(classpath);
        cmd.add(mainClass);

        /// common params (optional)
        cmd.addAll(COMMON_PLANNER_PARAMS);

        // extra params (MCTS etc.) -> mettre AVANT domain/problem
        cmd.addAll(extraParams);

        // domain + problem (positional parameters)
        cmd.add(domainPddl.toString());
        cmd.add(problemPddl.toString());


        long t0 = System.currentTimeMillis();
//        System.out.println("CMD=" + String.join(" ", cmd));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);

        Process p = pb.start();

        String output;
        boolean killed = false;

        // read async-ish with timeout by polling
        try (InputStream is = p.getInputStream()) {
            output = readWithTimeout(is, p, PROCESS_TIMEOUT_MS);
            if (p.isAlive()) {
                killed = true;
                p.destroyForcibly();
            }
        }

        int exit = killed ? -1 : p.waitFor();
        long runtime = System.currentTimeMillis() - t0;

        // parse RESULT lines
        boolean success = RE_SUCCESS.matcher(output).find();
        boolean failure = RE_FAILURE.matcher(output).find();
        int len = extractInt(RE_LEN, output, 0);
        long rt = extractLong(RE_RUNTIME, output, runtime);

        // if neither success nor failure was found, fallback:
        if (!success && !failure) {
            success = output.contains("Goal reached") || output.toLowerCase().contains("found plan");
        }

        return new RunResult(success, rt, len, exit, killed, output);
    }

    private static int extractInt(Pattern p, String s, int def) {
        Matcher m = p.matcher(s);
        return m.find() ? Integer.parseInt(m.group(1)) : def;
    }

    private static long extractLong(Pattern p, String s, long def) {
        Matcher m = p.matcher(s);
        return m.find() ? Long.parseLong(m.group(1)) : def;
    }

    private static String readWithTimeout(InputStream is, Process proc, long timeoutMs) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];

        long start = System.currentTimeMillis();
        while (true) {
            while (is.available() > 0) {
                int n = is.read(buf);
                if (n < 0) break;
                baos.write(buf, 0, n);
            }

            boolean finished = !proc.isAlive();
            if (finished) {
                // read remaining
                while (is.available() > 0) {
                    int n = is.read(buf);
                    if (n < 0) break;
                    baos.write(buf, 0, n);
                }
                break;
            }

            if (System.currentTimeMillis() - start > timeoutMs) {
                break;
            }

            try {
                Thread.sleep(10);
            } catch (InterruptedException ignored) { }
        }

        return baos.toString(StandardCharsets.UTF_8);
    }
}
