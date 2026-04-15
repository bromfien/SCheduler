package com.example;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

public class MainDPExtraction {

    private static final int MATCHES_PER_WEEK = MatchMatrix.MATCHES_PER_WEEK;
    private static final int WEEKS            = Config.getWeeks();

    private static final boolean[][] overlap =
        new boolean[MATCHES_PER_WEEK * MATCHES_PER_WEEK][MATCHES_PER_WEEK * MATCHES_PER_WEEK];

    static {
        preComputeTable();
    }

    public static void main(String[] args) {

        List<List<Integer>> allCourtElements = Config.getCourtGroups();
        int numCourtGroups = allCourtElements.size();

        int[] groupMatchCount = new int[numCourtGroups];
        for (int i = 0; i < numCourtGroups; i++) {
            groupMatchCount[i] = allCourtElements.get(i).size() / 2;
        }

        // ── DP scratch arrays ─────────────────────────────────────────────────
        int[]     groupTeams = new int[MATCHES_PER_WEEK];
        int[]     groupAdj   = new int[MATCHES_PER_WEEK];
        boolean[] dpTable    = new boolean[1 << MATCHES_PER_WEEK];

        // Phase 1 scratch
        int[] elemRows = new int[MATCHES_PER_WEEK];
        int[] elemCols = new int[MATCHES_PER_WEEK];

        // ── Counters reset each solution attempt ──────────────────────────────
        int  break_counter_1  = 0;
        int  break_counter_2  = 0;
        long loop1Time        = 0;
        long loop2Time        = 0;
        long startTime        = 0;
        int  restartCount     = 0;
        int  maxWeekReached   = 0;
        int[] groupFailCounts = new int[numCourtGroups];
        int[] weekRetries     = new int[WEEKS];
        int[] weekRetriesTemp = new int[WEEKS];

        // ── Run-level metrics (never reset) ───────────────────────────────────
        long programStartMs  = System.currentTimeMillis();
        long lastSolutionMs  = programStartMs;
        int  solutionCount   = 0;
        long totalAttempts   = 0;

        long searchStart = programStartMs;

        MatchMatrix matches;
        MatchMatrix temp_matches;

        System.out.println("Starting search (single-threaded, DP extraction). "
            + "Press Ctrl+C to stop.\n");

        // ── Main loop — runs until Ctrl+C ─────────────────────────────────────
        while (!Thread.currentThread().isInterrupted()) {

            restartCount++;
            totalAttempts++;
            matches = new MatchMatrix();
            int match_pairings_attempts_counter = 1;
            java.util.Arrays.fill(weekRetriesTemp, 0);

            outerloop:
            for (int weeks_counter = 0, match_count = 1;
                 weeks_counter < WEEKS;
                 weeks_counter++) {

                int[] elements_array   = new int[MATCHES_PER_WEEK];
                int   elements_counter = 0;
                int   elements_total   = 0;

                temp_matches = matches.copy();

                for (int court_counter = 0; court_counter < numCourtGroups; court_counter++) {

                    int courtMatches  = groupMatchCount[court_counter];
                    int numGroupTeams = 2 * courtMatches;
                    elements_total   += courtMatches;

                    int match_attempts_counter = 1;

                    // ── Phase 1: randomly fill this group's match slots ───────
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

                    // ── Phase 2: bitmask DP perfect matching + extraction ─────
                    startTime = System.nanoTime();

                    int sliceStart = elements_counter - courtMatches;
                    int ngt = 0;
                    for (int x = 0; x < courtMatches; x++) {
                        groupTeams[ngt++] = elemRows[sliceStart + x];
                        groupTeams[ngt++] = elemCols[sliceStart + x];
                    }

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
                                printStats(weeks_counter + 1,
                                    break_counter_1, break_counter_2,
                                    loop1Time, loop2Time, maxWeekReached,
                                    groupFailCounts, weekRetriesTemp,
                                    restartCount, searchStart,
                                    totalAttempts, programStartMs,
                                    solutionCount, lastSolutionMs);
                            }
                            break outerloop;
                        }
                    }

                    loop2Time += (System.nanoTime() - startTime);

                } // court loop

                matches = temp_matches.copy();

                // ── Week WEEKS-1 milestone ────────────────────────────────────
                if (weeks_counter + 1 == WEEKS - 1) {

                    System.arraycopy(weekRetriesTemp, 0, weekRetries, 0, WEEKS);
                    printStats(weeks_counter + 1,
                        break_counter_1, break_counter_2,
                        loop1Time, loop2Time, maxWeekReached,
                        groupFailCounts, weekRetries,
                        restartCount, searchStart,
                        totalAttempts, programStartMs,
                        solutionCount, lastSolutionMs);

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

                // ── Full solution found ───────────────────────────────────────
                if (weeks_counter + 1 == WEEKS) {

                    solutionCount++;
                    long now       = System.currentTimeMillis();
                    long sinceLast = now - lastSolutionMs;
                    lastSolutionMs = now;

                    // Write timestamped file
                    String timestamp = LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS"));
                    String filename = "matches_" + timestamp + ".txt";

                    try (PrintStream fileOut =
                            new PrintStream(new FileOutputStream(filename))) {
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

                    double elapsedMin = (now - programStartMs) / 60_000.0;
                    double attPerMin  = elapsedMin > 0.001 ? totalAttempts / elapsedMin : 0;
                    double solPerMin  = elapsedMin > 0.001 ? solutionCount / elapsedMin : 0;

                    System.out.printf(
                        "Solution #%,d  %s  Runtime: %s  Since last: %s  "
                        + "Attempts: %,d  Att/min: %,.0f  Sol/min: %.3f  "
                        + "C1: %5d  C2: %4d  L1: %s  L2: %s  → %s%n",
                        solutionCount,
                        LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                        formatMs(now - programStartMs),
                        formatMs(sinceLast),
                        totalAttempts, attPerMin, solPerMin,
                        break_counter_1, break_counter_2,
                        MatchMatrix.formatDuration(loop1Time),
                        MatchMatrix.formatDuration(loop2Time),
                        filename);

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

        } // while not interrupted

        // ── Final summary on Ctrl+C ───────────────────────────────────────────
        long elapsed = System.currentTimeMillis() - programStartMs;
        System.out.println();
        System.out.println("Shutting down.");
        System.out.printf("  Total solutions : %,d%n", solutionCount);
        System.out.printf("  Total attempts  : %,d%n", totalAttempts);
        System.out.printf("  Total runtime   : %s%n", formatMs(elapsed));
        double elapsedMin = elapsed / 60_000.0;
        if (elapsedMin > 0.001) {
            System.out.printf("  Avg att/min     : %,.0f%n", totalAttempts / elapsedMin);
            System.out.printf("  Avg sol/min     : %.3f%n",  solutionCount / elapsedMin);
        }
    }

    // ── Stat printing ─────────────────────────────────────────────────────────

    /**
     * Prints a diagnostic block after each week-6 milestone and each
     * failed week-7 attempt.
     *
     * Example output:
     *   [Wk6 OK]   22.6s  5088 restarts  MaxWk:6  22:57:20
     *              C1: 4931  C2:  156  L1:73% 00:16.212  L2:27% 00:05.833
     *              G0: 408K  G1:  77K  G2:  22K  G3:   7K  |  W3:3  W4:19
     *              Runtime: 00:14:22  Attempts: 82,341  Att/min: 5,732
     *              Solutions: 3  Sol/min: 0.209  Last sol: 00:04:51 ago
     */
    private static void printStats(
            int weeksReached,
            int counter1, int counter2,
            long loop1Time, long loop2Time,
            int maxWeekReached,
            int[] groupFailCounts,
            int[] weekRetries,
            int restartCount,
            long searchStart,
            long totalAttempts,
            long programStartMs,
            int solutionCount,
            long lastSolutionMs) {

        String timeNow    = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        double elapsedSec = (System.currentTimeMillis() - searchStart) / 1000.0;

        long totalTime = loop1Time + loop2Time;
        int  l1pct     = totalTime > 0 ? (int)(100L * loop1Time / totalTime) : 0;
        int  l2pct     = 100 - l1pct;

        long   now        = System.currentTimeMillis();
        long   elapsed    = now - programStartMs;
        double elapsedMin = elapsed / 60_000.0;
        double attPerMin  = elapsedMin > 0.001 ? totalAttempts / elapsedMin : 0;
        double solPerMin  = elapsedMin > 0.001 ? solutionCount / elapsedMin : 0;
        long   sinceLast  = now - lastSolutionMs;

        if (weeksReached == WEEKS) {
            // Compact no-solution line
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("  >> No sol (wk7)  %s", timeNow));
            for (int i = 0; i < groupFailCounts.length; i++) {
                sb.append(String.format("  G%d:%s", i, fmtK(groupFailCounts[i])));
            }
            sb.append(String.format("  Att/min: %,.0f", attPerMin));
            System.out.println(sb);
            return;
        }

        // Line 1: headline
        System.out.printf("[Wk%d OK]  %5.1fs  %5d restarts  MaxWk:%d  %s%n",
            weeksReached, elapsedSec, restartCount, maxWeekReached, timeNow);

        // Line 2: loop counters and time split
        System.out.printf("          C1:%5d  C2:%4d  L1:%2d%% %s  L2:%2d%% %s%n",
            counter1, counter2,
            l1pct, MatchMatrix.formatDuration(loop1Time),
            l2pct, MatchMatrix.formatDuration(loop2Time));

        // Line 3: per-group Phase 2 failures + week retries
        StringBuilder sb = new StringBuilder("          ");
        for (int i = 0; i < groupFailCounts.length; i++) {
            sb.append(String.format("G%d:%4s  ", i, fmtK(groupFailCounts[i])));
        }
        sb.append("|");
        boolean anyRetry = false;
        for (int w = 0; w < weeksReached; w++) {
            if (weekRetries[w] > 0) {
                sb.append(String.format("  W%d:%d", w + 1, weekRetries[w]));
                anyRetry = true;
            }
        }
        if (!anyRetry) sb.append("  (no retries)");
        System.out.println(sb);

        // Line 4: run-level throughput
        System.out.printf("          Runtime: %s  Attempts: %,d  Att/min: %,.0f%n",
            formatMs(elapsed), totalAttempts, attPerMin);

        // Line 5: solution rate
        System.out.printf("          Solutions: %,d  Sol/min: %.3f  Last sol: %s ago%n",
            solutionCount, solPerMin, formatMs(sinceLast));
    }

    private static String fmtK(int n) {
        if (n >= 1_000_000) return String.format("%dM", Math.round(n / 1_000_000.0));
        if (n >= 1_000)     return String.format("%dK", Math.round(n / 1_000.0));
        return Integer.toString(n);
    }

    private static String formatMs(long millis) {
        long s = Math.max(millis, 0) / 1000;
        return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
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

        int weekCount = (matchIndexes.size() + MATCHES_PER_WEEK - 1) / MATCHES_PER_WEEK;

        for (int week = 0; week < weekCount; week++) {
            int base = week * MATCHES_PER_WEEK;
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
