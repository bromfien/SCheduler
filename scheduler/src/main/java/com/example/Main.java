package com.example;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import java.io.FileWriter;
import java.io.IOException;

public class Main {

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

        // Pre-compute per-group match counts once
        int[] groupMatchCount = new int[numCourtGroups];
        for (int i = 0; i < numCourtGroups; i++) {
            groupMatchCount[i] = allCourtElements.get(i).size() / 2;
        }

        // ── DP scratch arrays (pre-allocated once, reused every Phase 2 call) ──
        //
        // Phase 2 is now a bitmask DP perfect matching that both FINDS the valid
        // pairing and EXTRACTS it in one pass, replacing the old random search.
        //
        // groupTeams[0..2*courtMatches-1] : the teams Phase 1 selected for this group
        // groupAdj[i]                     : bitmask of valid (unplayed) partners for
        //                                   groupTeams[i] among the other group teams
        // dpTable[mask]                   : true iff the teams in 'mask' can be
        //                                   perfectly matched using unplayed pairs
        //
        // Max group size: 2 * maxGroupMatches teams → 2^(2*maxGroupMatches) DP states.
        // For Config 1 (G0 = 8 teams): 2^8 = 256 states.
        // For Config 3 (each group = 4 teams): 2^4 = 16 states.
        // Sized to 2^MATCHES_PER_WEEK to cover all configs without reallocation.
        int[] groupTeams = new int[MATCHES_PER_WEEK];
        int[] groupAdj   = new int[MATCHES_PER_WEEK];
        boolean[] dpTable = new boolean[1 << MATCHES_PER_WEEK];

        // General scratch arrays for Phase 1
        int maxMatchesPerWeek = MATCHES_PER_WEEK;
        int[] elemRows        = new int[maxMatchesPerWeek];
        int[] elemCols        = new int[maxMatchesPerWeek];

        // ── search counters ───────────────────────────────────────────────
        int  break_counter_1 = 0;
        int  break_counter_2 = 0;
        long loop1Time       = 0;
        long loop2Time       = 0;
        long startTime       = 0;

        // ── diagnostic counters ───────────────────────────────────────────
        int  restartCount     = 0;
        long searchStart      = System.currentTimeMillis();
        int  maxWeekReached   = 0;
        int[] groupFailCounts = new int[numCourtGroups];
        int[] weekRetries     = new int[WEEKS];
        int[] weekRetriesTemp = new int[WEEKS];

        MatchMatrix matches     = new MatchMatrix();
        MatchMatrix temp_matches;

        boolean keepGoing = true;

        while (keepGoing) {

            restartCount++;
            matches = new MatchMatrix();
            int match_pairings_attempts_counter = 1;
            java.util.Arrays.fill(weekRetriesTemp, 0);

            outerloop:
            for (int weeks_counter = 0, match_count = 1; weeks_counter < WEEKS; weeks_counter++) {

                int[] elements_array   = new int[maxMatchesPerWeek];
                int   elements_counter = 0;
                int   elements_total   = 0;

                temp_matches = matches.copy();

                for (int court_counter = 0; court_counter < numCourtGroups; court_counter++) {

                    int courtMatches  = groupMatchCount[court_counter];
                    int numGroupTeams = 2 * courtMatches;
                    elements_total   += courtMatches;  // Phase 1 slots for this group

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

                    // ── Phase 2: DP perfect matching + direct extraction ──────
                    //
                    // Phase 1 selected 'courtMatches' matches (2*courtMatches teams).
                    // Those pairs are now marked in temp_matches as non-zero.
                    // Phase 2 must find a DIFFERENT perfect matching of the same
                    // teams using only unplayed pairs (temp_matches value == 0).
                    //
                    // The bitmask DP builds dp[mask] = "can teams in 'mask' be
                    // perfectly matched using unplayed pairs?", then backtracks
                    // directly to extract the matching — no random search needed.
                    //
                    // Cost per call: O(2^numGroupTeams × numGroupTeams).
                    //   Config 1 G0 (8 teams): 2^8 × 8 = 2048 ops.
                    //   Config 3 (4 teams):    2^4 × 4 =   64 ops.
                    startTime = System.nanoTime();

                    // Collect this group's Phase-1 teams into groupTeams[]
                    int sliceStart = elements_counter - courtMatches;
                    int ngt = 0;
                    for (int x = 0; x < courtMatches; x++) {
                        groupTeams[ngt++] = elemRows[sliceStart + x];
                        groupTeams[ngt++] = elemCols[sliceStart + x];
                    }
                    // ngt == numGroupTeams

                    // Build adjacency: groupAdj[i] bit j is set when groupTeams[i]
                    // vs groupTeams[j] is an unplayed pair (value == 0 in temp_matches,
                    // which excludes both Phase 1 selections and all prior weeks).
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

                    // Bitmask DP: dp[mask] = true iff teams indicated by mask bits
                    // can be perfectly matched using unplayed pairs.
                    int fullMask = (1 << numGroupTeams) - 1;
                    java.util.Arrays.fill(dpTable, 0, fullMask + 1, false);
                    dpTable[0] = true;
                    for (int mask = 1; mask <= fullMask; mask++) {
                        if ((Integer.bitCount(mask) & 1) != 0) continue; // odd count: skip
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
                        // No valid Phase 2 matching exists for these teams
                        matches_found = false;

                    } else {
                        // Extract the matching by backtracking through the DP table.
                        // At each step: lowest-bit team i pairs with the first valid j
                        // such that dpTable[remaining mask] is true.
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
                                printStats(
                                    weeks_counter + 1, break_counter_1, break_counter_2,
                                    loop1Time, loop2Time, maxWeekReached,
                                    groupFailCounts, weekRetriesTemp, restartCount, searchStart
                                );
                            }
                            break outerloop;
                        }
                    }

                    loop2Time += (System.nanoTime() - startTime);

                } // court loop

                matches = temp_matches.copy();

                if (weeks_counter + 1 == WEEKS - 1) {

                    try (FileWriter writer = new FileWriter("matches_output.txt", true)) {
                        java.io.PrintStream originalOut = System.out;
                        java.io.PrintStream fileOut = new java.io.PrintStream(new java.io.OutputStream() {
                            @Override
                            public void write(int b) throws IOException { writer.write(b); }
                        });
                        System.setOut(fileOut);
                        matches.printMatrix();
                        matches.printMatches();
                        System.out.flush();
                        System.setOut(originalOut);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    System.out.print("Total Weeks: " + (weeks_counter + 1) + "\t");
                    printStats(
                        weeks_counter + 1, break_counter_1, break_counter_2,
                        loop1Time, loop2Time, maxWeekReached,
                        groupFailCounts, weekRetriesTemp, restartCount, searchStart
                    );

                    System.arraycopy(weekRetriesTemp, 0, weekRetries, 0, WEEKS);

                    // Reset counters for next attempt
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

                if (weeks_counter + 1 == WEEKS) {
                    keepGoing = false;
                    break;
                }

            } // weeks loop

        } // while keepGoing

        // Write final results to a timestamped file
        String timestamp = java.time.LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String filename = "matches_" + timestamp + ".txt";

        try (java.io.PrintStream fileOut =
                new java.io.PrintStream(new java.io.FileOutputStream(filename))) {
            java.io.PrintStream originalOut = System.out;
            System.setOut(fileOut);
            matches.printMatrix();
            matches.printMatches();
            printCourtCounts(matches);
            System.out.flush();
            System.setOut(originalOut);
        } catch (java.io.FileNotFoundException e) {
            e.printStackTrace();
        }

        System.out.println("Results saved to: " + filename);
    }

    /**
     * Prints a two-line diagnostic block per solution attempt.
     *
     * Example:
     *   [Wk6 OK]   22.6s   5088 restarts  MaxWk:6  22:57:20.964
     *          C1:  4931  C2:  156  L1:73% 00:16.212  L2:27% 00:05.833
     *          G0:408K  G1: 77K  G2: 22K  G3:  7K  |  W3:3  W4:19  W5:22  W6:242
     */
    private static void printStats(
            int weeksReached,
            int counter1, int counter2,
            long loop1Time, long loop2Time,
            int maxWeekReached,
            int[] groupFailCounts,
            int[] weekRetries,
            int restartCount,
            long searchStart) {

        String timeNow    = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
        double elapsedSec = (System.currentTimeMillis() - searchStart) / 1000.0;

        long totalTime = loop1Time + loop2Time;
        int  l1pct     = totalTime > 0 ? (int)(100L * loop1Time / totalTime) : 0;
        int  l2pct     = 100 - l1pct;

        if (weeksReached == WEEKS) {
            System.out.printf(
                "  >> No solution (wk7)  %s  G0: %s%n",
                timeNow, fmtK(groupFailCounts[0])
            );
            return;
        }

        // Line 1: headline
        System.out.printf(
            "[Wk%d OK]  %5.1fs  %5d restarts  MaxWk:%d  %s%n",
            weeksReached, elapsedSec, restartCount, maxWeekReached, timeNow
        );

        // Line 2: counters and time split
        System.out.printf(
            "         C1:%5d  C2:%4d  L1:%2d%% %s  L2:%2d%% %s%n",
            counter1, counter2,
            l1pct, MatchMatrix.formatDuration(loop1Time),
            l2pct, MatchMatrix.formatDuration(loop2Time)
        );

        // Line 3: per-group Phase 2 failures + non-zero week retries
        StringBuilder sb = new StringBuilder("         ");
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
    }

    /** Format a large integer compactly: 407573 -> "408K", 1200000 -> "1M" */
    private static String fmtK(int n) {
        if (n >= 1_000_000) return String.format("%dM", Math.round(n / 1_000_000.0));
        if (n >= 1_000)     return String.format("%dK", Math.round(n / 1_000.0));
        return Integer.toString(n);
    }

    /**
     * Tallies how many times each team appears on each court group (Main, BP, Gerry).
     */
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

                    if (j >= mainStart && j <= mainEnd) {
                        main[t1]++;  main[t2]++;
                    } else if (j >= bpStart && j <= bpEnd) {
                        bp[t1]++;    bp[t2]++;
                    } else if (j >= gerryStart && j <= gerryEnd) {
                        gerry[t1]++; gerry[t2]++;
                    }
                }
            }
        }

        System.out.println();
        System.out.printf("     %-6s%-4s%-6s%n", "Main", "BP", "Gerry");
        for (int t = 1; t <= MATCHES_PER_WEEK; t++) {
            System.out.printf("T%-2d  %6d%4d%6d%n", t, main[t], bp[t], gerry[t]);
        }
    }

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
