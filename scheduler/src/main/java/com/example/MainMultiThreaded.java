package com.example;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;

public class MainMultiThreaded {

    private static final int MATCHES_PER_WEEK = MatchMatrix.MATCHES_PER_WEEK;
    private static final int WEEKS            = Config.getWeeks();

    // Overlap table — computed once at startup, read-only after that.
    // Safe to share across all threads.
    private static final boolean[][] overlap =
        new boolean[MATCHES_PER_WEEK * MATCHES_PER_WEEK][MATCHES_PER_WEEK * MATCHES_PER_WEEK];

    // ── Metrics tracked across all threads ────────────────────────────────────
    private static final long          PROGRAM_START_MS = System.currentTimeMillis();
    private static final AtomicInteger solutionCount    = new AtomicInteger(0);
    private static final AtomicLong    lastSolutionMs   = new AtomicLong(System.currentTimeMillis());
    private static final AtomicLong    totalAttempts    = new AtomicLong(0);
    private static final AtomicInteger peakWeek         = new AtomicInteger(0);

    // Per-thread current week — initialised in main() once nThreads is known.
    private static volatile AtomicIntegerArray threadCurrentWeek;

    // Worker threads queue log messages here; the status thread prints them.
    private static final ConcurrentLinkedQueue<String> logQueue = new ConcurrentLinkedQueue<>();

    // Serialises all console output and System.setOut redirects.
    private static final Object outputLock = new Object();

    static {
        preComputeTable();
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    public static void main(String[] args) throws InterruptedException {

        List<List<Integer>> allCourtElements = Config.getCourtGroups();
        int numCourtGroups = allCourtElements.size();

        // Pre-compute per-group match counts once — shared read-only
        final int[] groupMatchCount = new int[numCourtGroups];
        for (int i = 0; i < numCourtGroups; i++) {
            groupMatchCount[i] = allCourtElements.get(i).size() / 2;
        }

        int nThreads      = Runtime.getRuntime().availableProcessors();
        threadCurrentWeek = new AtomicIntegerArray(nThreads);

        // Start the live progress display before launching workers.
        startStatusDisplay(nThreads);

        ExecutorService pool = Executors.newFixedThreadPool(nThreads);

        // Shutdown hook — fires on Ctrl+C.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            pool.shutdownNow();
            synchronized (outputLock) {
                System.out.println();
                System.out.println("Shutting down.");
                System.out.printf("  Total solutions : %,d%n", solutionCount.get());
                System.out.printf("  Total attempts  : %,d%n", totalAttempts.get());
                System.out.printf("  Total runtime   : %s%n",
                    formatMs(System.currentTimeMillis() - PROGRAM_START_MS));
            }
        }));

        for (int t = 0; t < nThreads; t++) {
            final int threadId = t;

            pool.submit(() -> {

                // ── Per-thread scratch arrays ─────────────────────────────────
                // Every thread has its own copies — zero shared mutable state.

                // Phase 1 scratch
                int[] elemRows = new int[MATCHES_PER_WEEK];
                int[] elemCols = new int[MATCHES_PER_WEEK];

                // Phase 2 DP scratch
                // groupTeams[0..2*courtMatches-1] : teams Phase 1 selected for this group
                // groupAdj[i]                     : bitmask of valid unplayed partners
                // dpTable[mask]                   : true iff teams in mask can be matched
                //
                // Sized to MATCHES_PER_WEEK to cover all configs without reallocation.
                // Config 1 G0 (8 teams): 2^8 = 256 states.
                // Config 3 (4 teams):    2^4 =  16 states.
                int[]     groupTeams = new int[MATCHES_PER_WEEK];
                int[]     groupAdj   = new int[MATCHES_PER_WEEK];
                boolean[] dpTable    = new boolean[1 << MATCHES_PER_WEEK];

                MatchMatrix matches;
                MatchMatrix temp_matches;

                // Per-attempt diagnostic counters
                int  break_counter_1 = 0;
                int  break_counter_2 = 0;
                long loop1Time       = 0;
                long loop2Time       = 0;
                long startTime;
                int  restartCount    = 0;
                long searchStart     = System.currentTimeMillis();
                int  maxWeekReached  = 0;
                int[] groupFailCounts = new int[numCourtGroups];
                int[] weekRetriesTemp = new int[WEEKS];

                // ── Search loop — runs until Ctrl+C ───────────────────────────
                while (!Thread.currentThread().isInterrupted()) {

                    restartCount++;
                    matches = new MatchMatrix();
                    totalAttempts.incrementAndGet();
                    int match_pairings_attempts_counter = 1;
                    java.util.Arrays.fill(weekRetriesTemp, 0);

                    int weeksReached = 0;

                    outerloop:
                    for (int weeks_counter = 0, match_count = 1;
                         weeks_counter < WEEKS;
                         weeks_counter++) {

                        weeksReached = weeks_counter;

                        int[] elements_array   = new int[MATCHES_PER_WEEK];
                        int   elements_counter = 0;
                        int   elements_total   = 0;

                        temp_matches = matches.copy();

                        for (int court_counter = 0;
                             court_counter < numCourtGroups;
                             court_counter++) {

                            int courtMatches  = groupMatchCount[court_counter];
                            int numGroupTeams = 2 * courtMatches;
                            elements_total   += courtMatches;

                            int match_attempts_counter = 1;

                            // ── Phase 1: randomly fill this group's match slots ──
                            startTime = System.nanoTime();

                            while (elements_counter < elements_total) {

                                if (match_attempts_counter++ % 50_000 == 0) {
                                    loop1Time += (System.nanoTime() - startTime);
                                    break_counter_1++;
                                    maxWeekReached = Math.max(maxWeekReached, weeks_counter + 1);
                                    break outerloop;
                                }

                                int random_match_index = temp_matches.generateRandomMatch();
                                int rRow = temp_matches.getRow(random_match_index);
                                int rCol = temp_matches.getCol(random_match_index);

                                boolean already_exists = false;
                                for (int k = 0; k < elements_counter; k++) {
                                    if (hasOverlap(rRow, rCol, elemRows[k], elemCols[k])) {
                                        already_exists = true;
                                        break;
                                    }
                                }

                                if (!already_exists
                                        && temp_matches.getMatchValueByRowAndCol(rRow, rCol) == 0) {
                                    temp_matches.setMatchValueByRowCol(rRow, rCol, match_count++);
                                    elements_array[elements_counter] = random_match_index;
                                    elemRows[elements_counter] = rRow;
                                    elemCols[elements_counter] = rCol;
                                    elements_counter++;
                                }
                            } // Phase 1 while

                            loop1Time += (System.nanoTime() - startTime);

                            // ── Phase 2: bitmask DP perfect matching + extraction ──
                            //
                            // Builds dp[mask] = "can teams in mask be perfectly matched
                            // using unplayed pairs?", then backtracks to extract it.
                            // No random search — deterministic, O(2^N × N) per call.
                            startTime = System.nanoTime();

                            // Collect Phase 1 teams for this group
                            int sliceStart = elements_counter - courtMatches;
                            int ngt = 0;
                            for (int x = 0; x < courtMatches; x++) {
                                groupTeams[ngt++] = elemRows[sliceStart + x];
                                groupTeams[ngt++] = elemCols[sliceStart + x];
                            }

                            // Build adjacency bitmasks
                            for (int i = 0; i < numGroupTeams; i++) groupAdj[i] = 0;
                            for (int i = 0; i < numGroupTeams; i++) {
                                for (int j = i + 1; j < numGroupTeams; j++) {
                                    if (temp_matches.getMatchValueByRowAndCol(
                                            groupTeams[i], groupTeams[j]) == 0) {
                                        groupAdj[i] |= (1 << j);
                                        groupAdj[j] |= (1 << i);
                                    }
                                }
                            }

                            // DP over all even-cardinality subsets
                            int fullMask = (1 << numGroupTeams) - 1;
                            java.util.Arrays.fill(dpTable, 0, fullMask + 1, false);
                            dpTable[0] = true;
                            for (int mask = 1; mask <= fullMask; mask++) {
                                if ((Integer.bitCount(mask) & 1) != 0) continue;
                                int i    = Integer.numberOfTrailingZeros(mask);
                                int rest = mask ^ (1 << i);
                                for (int j = i + 1; j < numGroupTeams; j++) {
                                    if ((rest & (1 << j)) != 0
                                            && (groupAdj[i] & (1 << j)) != 0
                                            && dpTable[rest ^ (1 << j)]) {
                                        dpTable[mask] = true;
                                        break;
                                    }
                                }
                            }

                            boolean matches_found;

                            if (!dpTable[fullMask]) {
                                matches_found = false;

                            } else {
                                // Backtrack through DP table to extract the matching
                                int mask = fullMask;
                                while (mask != 0) {
                                    int i    = Integer.numberOfTrailingZeros(mask);
                                    int rest = mask ^ (1 << i);
                                    for (int j = i + 1; j < numGroupTeams; j++) {
                                        if ((rest & (1 << j)) != 0
                                                && (groupAdj[i] & (1 << j)) != 0
                                                && dpTable[rest ^ (1 << j)]) {
                                            int ar = groupTeams[i];
                                            int ac = groupTeams[j];
                                            temp_matches.setMatchValueByRowCol(ar, ac, match_count++);
                                            int newIdx = temp_matches.getIndexByRowandCol(ar, ac);
                                            elements_array[elements_counter] = newIdx;
                                            elemRows[elements_counter] = ar;
                                            elemCols[elements_counter] = ac;
                                            elements_counter++;
                                            mask = rest ^ (1 << j);
                                            break;
                                        }
                                    }
                                }
                                matches_found = true;
                                if (court_counter < numCourtGroups - 1) {
                                    elements_total += courtMatches;
                                }
                            }

                            if (!matches_found) {

                                groupFailCounts[court_counter]++;
                                weekRetriesTemp[weeks_counter]++;

                                match_count      = (match_count / MATCHES_PER_WEEK) * MATCHES_PER_WEEK + 1;
                                elements_total   = 0;
                                elements_counter = 0;
                                court_counter    = -1;
                                temp_matches     = matches.copy();

                                if (match_pairings_attempts_counter++ % 1_000 == 0) {
                                    break_counter_2++;
                                    loop2Time += (System.nanoTime() - startTime);
                                    maxWeekReached = Math.max(maxWeekReached, weeks_counter + 1);
                                    if (weeks_counter + 1 == WEEKS) {
                                        logStats(threadId, weeks_counter + 1,
                                            break_counter_1, break_counter_2,
                                            loop1Time, loop2Time, maxWeekReached,
                                            groupFailCounts, weekRetriesTemp, restartCount,
                                            searchStart);
                                    }
                                    break outerloop;
                                }
                            }

                            loop2Time += (System.nanoTime() - startTime);

                        } // court loop

                        matches = temp_matches.copy();

                        // Log milestone at week WEEKS-1
                        if (weeks_counter + 1 == WEEKS - 1) {
                            logStats(threadId, weeks_counter + 1,
                                break_counter_1, break_counter_2,
                                loop1Time, loop2Time, maxWeekReached,
                                groupFailCounts, weekRetriesTemp, restartCount,
                                searchStart);
                            break_counter_1 = 0;
                            break_counter_2 = 0;
                            loop1Time       = 0;
                            loop2Time       = 0;
                            maxWeekReached  = 0;
                            java.util.Arrays.fill(groupFailCounts, 0);
                            java.util.Arrays.fill(weekRetriesTemp, 0);
                            restartCount    = 0;
                            searchStart     = System.currentTimeMillis();
                        }

                        // ── Full solution found — write file and keep searching ──
                        if (weeks_counter + 1 == WEEKS) {
                            int solNum = solutionCount.incrementAndGet();
                            writeSolution(matches, threadId, solNum);
                            break_counter_1 = 0;
                            break_counter_2 = 0;
                            loop1Time       = 0;
                            loop2Time       = 0;
                            maxWeekReached  = 0;
                            java.util.Arrays.fill(groupFailCounts, 0);
                            java.util.Arrays.fill(weekRetriesTemp, 0);
                            restartCount    = 0;
                            searchStart     = System.currentTimeMillis();
                        }

                    } // weeks loop

                    // Update display metrics once per attempt
                    threadCurrentWeek.set(threadId, weeksReached);
                    int pw = peakWeek.get();
                    if (weeksReached > pw) peakWeek.compareAndSet(pw, weeksReached);

                } // while not interrupted

            }); // pool.submit
        } // thread loop

        pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }

    // ── Progress display ──────────────────────────────────────────────────────

    private static void startStatusDisplay(int nThreads) {

        final int    STATUS_LINES = 5;
        final int    WIDTH        = 66;
        final String SEP          = "+" + "-".repeat(WIDTH - 2) + "+";

        Thread statusThread = new Thread(() -> {
            boolean firstDraw      = true;
            int     prevExtraLines = 0;

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    break;
                }

                long   now          = System.currentTimeMillis();
                long   elapsed      = now - PROGRAM_START_MS;
                long   sinceLastSol = now - lastSolutionMs.get();
                int    sols         = solutionCount.get();
                long   attempts     = totalAttempts.get();
                double elapsedMin   = elapsed / 60_000.0;
                double solRate      = elapsedMin > 0.001 ? sols     / elapsedMin : 0.0;
                double attRate      = elapsedMin > 0.001 ? attempts / elapsedMin : 0.0;
                int    peak         = peakWeek.get() + 1;

                StringBuilder wkStr = new StringBuilder();
                for (int i = 0; i < nThreads; i++) {
                    if (i > 0) wkStr.append(' ');
                    wkStr.append('T').append(i).append('=')
                         .append(threadCurrentWeek.get(i) + 1);
                }

                String line1 = cell(WIDTH,
                    lbl("Running",   formatMs(elapsed)),
                    lbl("Threads",   String.valueOf(nThreads)),
                    lbl("Attempts",  String.format("%,d", attempts)));
                String line2 = cell(WIDTH,
                    lbl("Solutions", String.format("%,d", sols)),
                    lbl("Sol/min",   String.format("%.2f", solRate)),
                    lbl("Last sol",  formatMs(sinceLastSol) + " ago"));
                String line3 = cell(WIDTH,
                    lbl("Att/min",   String.format("%,.0f", attRate)),
                    lbl("Peak week", peak + "/" + WEEKS),
                    lbl("Wks",       wkStr.toString()));

                synchronized (outputLock) {
                    List<String> msgs = new ArrayList<>();
                    String m;
                    while ((m = logQueue.poll()) != null) msgs.add(m);
                    int extra = msgs.size();

                    if (!firstDraw) {
                        System.out.print("\033[" + (STATUS_LINES + prevExtraLines) + "A");
                        System.out.print("\033[0J");
                    }
                    firstDraw      = false;
                    prevExtraLines = extra;

                    for (String msg : msgs) System.out.println(msg);
                    System.out.println(SEP);
                    System.out.println(line1);
                    System.out.println(line2);
                    System.out.println(line3);
                    System.out.println(SEP);
                    System.out.flush();
                }
            }
        });
        statusThread.setDaemon(true);
        statusThread.start();
    }

    private static String cell(int width, String... segments) {
        String inner = String.join("  |  ", segments);
        int target = width - 4;
        if (inner.length() > target) inner = inner.substring(0, target);
        else while (inner.length() < target) inner += ' ';
        return "| " + inner + " |";
    }

    private static String lbl(String label, String value) {
        return label + ": " + value;
    }

    private static String formatMs(long millis) {
        long s = Math.max(millis, 0) / 1000;
        return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    // ── Logging ───────────────────────────────────────────────────────────────

    /**
     * Queues a diagnostic log message (printed by the status thread above
     * the status bar so it scrolls naturally).
     */
    private static void logStats(
            int threadId, int weeksReached,
            int counter1, int counter2,
            long loop1Time, long loop2Time,
            int maxWeekReached,
            int[] groupFailCounts,
            int[] weekRetries,
            int restartCount,
            long searchStart) {

        String timeNow    = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        double elapsedSec = (System.currentTimeMillis() - searchStart) / 1000.0;
        long   totalTime  = loop1Time + loop2Time;
        int    l1pct      = totalTime > 0 ? (int)(100L * loop1Time / totalTime) : 0;

        if (weeksReached == WEEKS) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("[T%d] No solution  %s  G0:%s",
                threadId, timeNow, fmtK(groupFailCounts[0])));
            for (int i = 1; i < groupFailCounts.length; i++) {
                sb.append(String.format("  G%d:%s", i, fmtK(groupFailCounts[i])));
            }
            logQueue.offer(sb.toString());
            return;
        }

        // Week milestone line
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[T%d] Wk%d OK  %5.1fs  %d restarts  MaxWk:%d  %s",
            threadId, weeksReached, elapsedSec, restartCount, maxWeekReached, timeNow));
        sb.append(String.format("  C1:%5d  C2:%4d  L1:%2d%%  L2:%2d%%",
            counter1, counter2, l1pct, 100 - l1pct));

        // Append per-group failures
        sb.append("  |");
        for (int i = 0; i < groupFailCounts.length; i++) {
            sb.append(String.format("  G%d:%s", i, fmtK(groupFailCounts[i])));
        }

        // Append non-zero week retries
        boolean anyRetry = false;
        for (int w = 0; w < weeksReached; w++) {
            if (weekRetries[w] > 0) {
                if (!anyRetry) sb.append("  |");
                sb.append(String.format("  W%d:%d", w + 1, weekRetries[w]));
                anyRetry = true;
            }
        }

        logQueue.offer(sb.toString());
    }

    private static String fmtK(int n) {
        if (n >= 1_000_000) return String.format("%dM", Math.round(n / 1_000_000.0));
        if (n >= 1_000)     return String.format("%dK", Math.round(n / 1_000.0));
        return Integer.toString(n);
    }

    // ── File output ───────────────────────────────────────────────────────────

    private static void writeSolution(MatchMatrix matches, int threadId, int solutionNum) {

        lastSolutionMs.set(System.currentTimeMillis());

        String timestamp = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS"));
        String filename = "matches_" + timestamp + "_t" + threadId + ".txt";

        synchronized (outputLock) {
            try (PrintStream fileOut = new PrintStream(new FileOutputStream(filename))) {
                PrintStream originalOut = System.out;
                System.setOut(fileOut);
                matches.printMatrix();
                matches.printMatches();
                printCourtCounts(matches);
                System.out.flush();
                System.setOut(originalOut);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        logQueue.offer(String.format(
            "[T%d] Solution #%,d saved → %s", threadId, solutionNum, filename));
    }

    // ── Court count summary ───────────────────────────────────────────────────

    public static void printCourtCounts(MatchMatrix matches) {

        int[][] groups  = Config.getScheduleGroups();
        int mainCourts  = Config.getMainCourts();
        int bpCourts    = Config.getBpCourts();
        int gerryCourts = Config.getGerryCourts();

        int mainStart  = 0;
        int mainEnd    = mainCourts - 1;
        int bpStart    = mainCourts;
        int bpEnd      = mainCourts + bpCourts - 1;
        int gerryStart = mainCourts + bpCourts;
        int gerryEnd   = mainCourts + bpCourts + gerryCourts - 1;

        int totalListSize = MatchMatrix.TOTAL_MATCHES + 1;
        List<Integer> matchIndexes = new ArrayList<>();
        for (int k = 1; k < totalListSize; k++) {
            for (int i = 1; i < totalListSize; i++) {
                if (matches.getMatchValueByIndex(i) == k) {
                    matchIndexes.add(i);
                }
            }
        }

        int[] main  = new int[MATCHES_PER_WEEK + 1];
        int[] bp    = new int[MATCHES_PER_WEEK + 1];
        int[] gerry = new int[MATCHES_PER_WEEK + 1];

        int gamesPerWeek = MATCHES_PER_WEEK;
        int weekCount    = (matchIndexes.size() + gamesPerWeek - 1) / gamesPerWeek;

        for (int week = 0; week < weekCount; week++) {
            int base = week * gamesPerWeek;
            for (int[] group : groups) {
                for (int j = 0; j < group.length; j++) {
                    int idx = base + group[j];
                    if (idx >= matchIndexes.size()) continue;

                    int matchNum = matchIndexes.get(idx);
                    int t1 = matches.getRow(matchNum) + 1;
                    int t2 = matches.getCol(matchNum) + 1;

                    if      (j >= mainStart  && j <= mainEnd)  { main[t1]++;  main[t2]++;  }
                    else if (j >= bpStart    && j <= bpEnd)    { bp[t1]++;    bp[t2]++;    }
                    else if (j >= gerryStart && j <= gerryEnd) { gerry[t1]++; gerry[t2]++; }
                }
            }
        }

        System.out.println();
        System.out.printf("     %-6s%-4s%-6s%n", "Main", "BP", "Gerry");
        for (int t = 1; t <= MATCHES_PER_WEEK; t++) {
            System.out.printf("T%-2d  %6d%4d%6d%n", t, main[t], bp[t], gerry[t]);
        }
    }

    // ── Overlap table ─────────────────────────────────────────────────────────

    public static boolean hasOverlap(int r1, int c1, int r2, int c2) {
        int a = (r1 * MATCHES_PER_WEEK) + c1;
        int b = (r2 * MATCHES_PER_WEEK) + c2;
        return overlap[a][b];
    }

    public static void preComputeTable() {
        for (int r1 = 0; r1 < MATCHES_PER_WEEK; r1++) {
            for (int c1 = 0; c1 < MATCHES_PER_WEEK; c1++) {
                int a = (r1 * MATCHES_PER_WEEK) + c1;
                for (int r2 = 0; r2 < MATCHES_PER_WEEK; r2++) {
                    for (int c2 = 0; c2 < MATCHES_PER_WEEK; c2++) {
                        int b = (r2 * MATCHES_PER_WEEK) + c2;
                        overlap[a][b] = (r1 == r2 || c1 == c2 || r1 == c2 || c1 == r2);
                    }
                }
            }
        }
    }
}