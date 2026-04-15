package com.example;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class MainSingleThreaded {

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

        PairingGenerator[] courts = new PairingGenerator[numCourtGroups];
        for (int i = 0; i < numCourtGroups; i++) {
            courts[i] = new PairingGenerator(allCourtElements.get(i));
        }

        int[] groupMatchCount = new int[numCourtGroups];
        int[] groupSize       = new int[numCourtGroups];
        for (int i = 0; i < numCourtGroups; i++) {
            groupSize[i]       = allCourtElements.get(i).size();
            groupMatchCount[i] = groupSize[i] / 2;
        }

        int maxMatchesPerWeek = MATCHES_PER_WEEK;
        int[] elemRows        = new int[maxMatchesPerWeek];
        int[] elemCols        = new int[maxMatchesPerWeek];

        int maxGroupMatches = 0;
        for (int mc : groupMatchCount) maxGroupMatches = Math.max(maxGroupMatches, mc);
        int[] current_elements = new int[maxGroupMatches];

        int[][] pairingOrders = new int[numCourtGroups][];
        for (int i = 0; i < numCourtGroups; i++) {
            int count = courts[i].countPairings();
            pairingOrders[i] = new int[count];
            for (int k = 0; k < count; k++) pairingOrders[i][k] = k;
        }

        MatchMatrix matches;
        MatchMatrix temp_matches;

        // ── Run-level metrics ─────────────────────────────────────────────────
        long programStartMs = System.currentTimeMillis();
        long lastSolutionMs = programStartMs;
        int  solutionCount  = 0;
        long totalAttempts  = 0;

        int  break_counter_1 = 0;
        int  break_counter_2 = 0;
        long startTime  = 0;
        long loop1Time  = 0;
        long loop2Time  = 0;

        System.out.println("Starting single-threaded search. Press Ctrl+C to stop.");
        System.out.println();

        // ── Main loop — runs until Ctrl+C ─────────────────────────────────────
        while (!Thread.currentThread().isInterrupted()) {

            matches = new MatchMatrix();
            totalAttempts++;
            int match_pairings_attempts_counter = 1;

            outerloop:
            for (int weeks_counter = 0, match_count = 1; weeks_counter < WEEKS; weeks_counter++) {

                int[] elements_array   = new int[maxMatchesPerWeek];
                int   elements_counter = 0;
                int   elements_total   = 0;

                temp_matches = matches.copy();

                for (int court_counter = 0; court_counter < numCourtGroups; court_counter++) {

                    int courtMatches = groupMatchCount[court_counter];
                    elements_total  += courtMatches;

                    int match_attempts_counter = 1;
                    startTime = System.nanoTime();

                    while (elements_counter < elements_total) {

                        if (match_attempts_counter++ % 10_000 == 0) {
                            loop1Time += (System.nanoTime() - startTime);
                            break_counter_1++;
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
                    } // while elements

                    loop1Time += (System.nanoTime() - startTime);

                    boolean matches_found = false;
                    startTime = System.nanoTime();

                    int[] order       = pairingOrders[court_counter];
                    int   pairingCount = order.length;
                    for (int k = pairingCount - 1; k > 0; k--) {
                        int j   = ThreadLocalRandom.current().nextInt(k + 1);
                        int tmp = order[k]; order[k] = order[j]; order[j] = tmp;
                    }

                    int sliceStart = elements_counter - courtMatches;
                    for (int x = 0; x < courtMatches; x++) {
                        current_elements[x] = elements_array[sliceStart + x];
                    }

                    for (int ki = 0; ki < pairingCount; ki++) {
                        int k = order[ki];

                        int[] currentElementArray = temp_matches.getRowColArrayByIndexes(
                            current_elements, courtMatches);
                        int[] rearrangedArray = PairingGenerator.rearrangeArray(
                            currentElementArray, courts[court_counter].getPairingAsArray(k));

                        boolean already_exists = false;
                        for (int j = 0; j < courtMatches; j++) {
                            if (temp_matches.getMatchValueByRowAndCol(
                                    rearrangedArray[2*j], rearrangedArray[2*j + 1]) != 0) {
                                already_exists = true;
                                break;
                            }
                        }

                        if (!already_exists) {
                            for (int j = 0; j < courtMatches; j++) {
                                int ar = rearrangedArray[2*j];
                                int ac = rearrangedArray[2*j + 1];
                                temp_matches.setMatchValueByRowCol(ar, ac, match_count++);
                                int newIdx = temp_matches.getIndexByRowandCol(ar, ac);
                                elements_array[elements_counter] = newIdx;
                                elemRows[elements_counter] = ar;
                                elemCols[elements_counter] = ac;
                                elements_counter++;
                            }
                            matches_found = true;
                            if (court_counter < numCourtGroups - 1) {
                                elements_total += courtMatches;
                            }
                            break;
                        }
                    } // pairing loop

                    if (!matches_found) {

                        loop2Time += (System.nanoTime() - startTime);

                        match_count      = (match_count / MATCHES_PER_WEEK) * MATCHES_PER_WEEK + 1;
                        elements_total   = 0;
                        elements_counter = 0;
                        court_counter    = -1;

                        temp_matches = matches.copy();

                        if (match_pairings_attempts_counter++ % 100_000 == 0 && weeks_counter + 1 == WEEKS ||
                            match_pairings_attempts_counter     % 50_000  == 0 && weeks_counter + 1 < WEEKS) {

                            break_counter_2++;
                            loop2Time += (System.nanoTime() - startTime);

                            if (weeks_counter + 1 == WEEKS) {
                                long elapsed = System.currentTimeMillis() - programStartMs;
                                double attPerMin = totalAttempts / Math.max(elapsed / 60_000.0, 0.001);
                                System.out.printf(
                                    "No solution  %s  Attempts: %,d  Att/min: %,.0f  " +
                                    "C1: %5d  C2: %5d  L1: %s  L2: %s  Wk: %d%n",
                                    LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                                    totalAttempts, attPerMin,
                                    break_counter_1, break_counter_2,
                                    MatchMatrix.formatDuration(loop1Time),
                                    MatchMatrix.formatDuration(loop2Time),
                                    weeks_counter + 1);
                            }

                            break outerloop;
                        }
                    }

                    loop2Time += (System.nanoTime() - startTime);

                } // court loop

                matches = temp_matches.copy();

                // ── Solution found ────────────────────────────────────────────
                if (weeks_counter + 1 == WEEKS) {

                    solutionCount++;
                    long now        = System.currentTimeMillis();
                    long elapsed    = now - programStartMs;
                    long sinceLast  = now - lastSolutionMs;
                    lastSolutionMs  = now;
                    double attPerMin = totalAttempts / Math.max(elapsed / 60_000.0, 0.001);

                    // Write timestamped file
                    String timestamp = LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS"));
                    String filename = "matches_" + timestamp + ".txt";

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

                    System.out.printf(
                        "Solution #%,d  %s  Runtime: %s  Since last: %s  " +
                        "Attempts: %,d  Att/min: %,.0f  C1: %5d  C2: %5d  " +
                        "L1: %s  L2: %s  → %s%n",
                        solutionCount,
                        LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                        formatMs(elapsed),
                        formatMs(sinceLast),
                        totalAttempts, attPerMin,
                        break_counter_1, break_counter_2,
                        MatchMatrix.formatDuration(loop1Time),
                        MatchMatrix.formatDuration(loop2Time),
                        filename);

                    // Reset per-solution counters
                    break_counter_1 = 0;
                    break_counter_2 = 0;
                    loop1Time = 0;
                    loop2Time = 0;
                }

            } // weeks loop

        } // while not interrupted

        // ── Final summary on Ctrl+C ───────────────────────────────────────────
        long elapsed = System.currentTimeMillis() - programStartMs;
        System.out.println();
        System.out.println("Shutting down.");
        System.out.printf("  Total solutions : %,d%n", solutionCount);
        System.out.printf("  Total attempts  : %,d%n", totalAttempts);
        System.out.printf("  Total runtime   : %s%n",  formatMs(elapsed));
        System.out.printf("  Avg att/min     : %,.0f%n",
            totalAttempts / Math.max(elapsed / 60_000.0, 0.001));
    }

    /** Converts milliseconds to HH:MM:SS. */
    private static String formatMs(long millis) {
        long s = Math.max(millis, 0) / 1000;
        return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

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
