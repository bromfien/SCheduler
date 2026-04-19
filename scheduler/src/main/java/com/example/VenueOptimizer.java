package com.example;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Venue Optimizer
 * ===============
 * Given a solved MatchMatrix, finds the optimal weekly venue assignment
 * for each pair-slot using exact backtracking with pruning.
 *
 * Pair-slot model (8-court layout):
 *   PS0 → columns 0-1 (Main A + Main B)
 *   PS1 → columns 2-3 (Main C + Main D)
 *   PS2 → columns 4-5 (BP East + BP West)
 *   PS3 → columns 6-7 (Gerry East + West)
 *
 * Each week: 2 pair-slots → Main, 1 → BP, 1 → Gerry.
 * Valid assignments per week: C(4,2) × 2 = 12.
 * Search space (7 weeks): 12^7 ≈ 36M — solved in milliseconds with pruning.
 */
public class VenueOptimizer {

    // ── Venue indices ──────────────────────────────────────────────────────────
    private static final int MAIN  = 0;
    private static final int BP    = 1;
    private static final int GERRY = 2;

    private static final int N_VENUES      = 3;
    private static final int N_PAIR_SLOTS  = 4;
    private static final int N_ASSIGNMENTS = 12; // C(4,2) × 2

    // ── Precomputed constants (derived from Config at class load) ──────────────
    static final int[][]  VALID_ASSIGNMENTS;
    static final int      ORIGINAL_AI;
    static final double[] TARGET;
    static final int      MAIN_MIN, MAIN_MAX, BP_MIN, BP_MAX, GERRY_MIN, GERRY_MAX;

    static {
        // Build all 12 valid weekly assignments (venue index per pair-slot)
        List<int[]> asgns = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            for (int j = i + 1; j < 4; j++) {
                int[] rem = new int[2];
                int ri = 0;
                for (int k = 0; k < 4; k++) if (k != i && k != j) rem[ri++] = k;
                for (int swap = 0; swap < 2; swap++) {
                    int[] a = new int[4];
                    a[i] = MAIN; a[j] = MAIN;
                    a[rem[0]] = (swap == 0) ? BP    : GERRY;
                    a[rem[1]] = (swap == 0) ? GERRY : BP;
                    asgns.add(a);
                }
            }
        }
        VALID_ASSIGNMENTS = asgns.toArray(new int[0][]);

        // Find original: PS0,PS1→Main; PS2→BP; PS3→Gerry
        int origIdx = 0;
        for (int ai = 0; ai < VALID_ASSIGNMENTS.length; ai++) {
            int[] a = VALID_ASSIGNMENTS[ai];
            if (a[0]==MAIN && a[1]==MAIN && a[2]==BP && a[3]==GERRY) { origIdx=ai; break; }
        }
        ORIGINAL_AI = origIdx;

        // TARGET[v] = ideal games per team at venue v over the full season
        int nW = Config.getWeeks();
        int mc = Config.getMainCourts();
        int bc = Config.getBpCourts();
        int gc = Config.getGerryCourts();
        int tc = mc + bc + gc;
        int tg = nW * 2; // total games per team (2 per week)
        TARGET = new double[]{ tg * (double)mc/tc, tg * (double)bc/tc, tg * (double)gc/tc };

        // Valid range: nearest even-number interval spanning TARGET[v]
        // (counts are always even since each pair-slot adds 2 games)
        MAIN_MIN  = (int)(Math.floor(TARGET[MAIN]  / 2.0)) * 2;
        MAIN_MAX  = (int)(Math.ceil( TARGET[MAIN]  / 2.0)) * 2;
        BP_MIN    = (int)(Math.floor(TARGET[BP]    / 2.0)) * 2;
        BP_MAX    = (int)(Math.ceil( TARGET[BP]    / 2.0)) * 2;
        GERRY_MIN = (int)(Math.floor(TARGET[GERRY] / 2.0)) * 2;
        GERRY_MAX = (int)(Math.ceil( TARGET[GERRY] / 2.0)) * 2;
    }

    // ── Support check ──────────────────────────────────────────────────────────

    /** Returns true for pair-slot (8-court, 4-pair-slot) layouts. */
    private static boolean isPairSlotLayout() {
        int tc = Config.getMainCourts() + Config.getBpCourts() + Config.getGerryCourts();
        return MatchMatrix.MATCHES_PER_WEEK % N_PAIR_SLOTS == 0
            && tc == N_PAIR_SLOTS * 2;
    }

    /**
     * Returns true for any layout where each column maps to a fixed venue
     * (pair-slot rotation OR fixed assignment).
     */
    public static boolean isSupported() {
        int tc = Config.getMainCourts() + Config.getBpCourts() + Config.getGerryCourts();
        int[][] sg = Config.getScheduleGroups();
        return sg.length == 2 && tc == sg[0].length;
    }

    // ── Result holder ──────────────────────────────────────────────────────────

    static final String SUMMARY_FILE = "venue_summary.txt";

    static class OptResult {
        final int[][][][] weekMatchPairs; // [nWeeks][nRows][nCols][2] — {teamA, teamB}
        final int[]       choices;        // best assignment index per week
        final double      score;
        final int[][]     finalCounts;    // [nTeams][3]
        final int         teamsInRange;   // count of teams meeting all venue targets
        final int         nTeams;
        final int         nWeeks;
        final boolean     pairSlot;       // true = 4-pair-slot layout; false = fixed-main layout

        OptResult(int[][][][] weekMatchPairs, int[] choices, double score,
                  int[][] finalCounts, int teamsInRange, int nTeams, int nWeeks,
                  boolean pairSlot) {
            this.weekMatchPairs = weekMatchPairs;
            this.choices        = choices;
            this.score          = score;
            this.finalCounts    = finalCounts;
            this.teamsInRange   = teamsInRange;
            this.nTeams         = nTeams;
            this.nWeeks         = nWeeks;
            this.pairSlot       = pairSlot;
        }
    }

    // ── Entry point ────────────────────────────────────────────────────────────

    /**
     * Runs the venue optimization on a solved MatchMatrix.
     * Returns null if the current Config is not a supported layout.
     */
    public static OptResult optimize(MatchMatrix matches) {
        if (!isSupported()) return null;
        if (!isPairSlotLayout()) return optimizeFixed(matches);

        int      nWeeks        = Config.getWeeks();
        int      nTeams        = MatchMatrix.MATCHES_PER_WEEK;
        int      gamesPerWeek  = MatchMatrix.MATCHES_PER_WEEK;
        int[][]  schedGroups   = Config.getScheduleGroups();
        int      nRows         = schedGroups.length;    // 2
        int      nCols         = schedGroups[0].length; // 8

        // ── Build sorted match index list ──────────────────────────────────────
        int totalListSize = MatchMatrix.TOTAL_MATCHES + 1;
        List<Integer> matchIndexes = new ArrayList<>();
        for (int k = 1; k < totalListSize; k++) {
            for (int i = 1; i < totalListSize; i++) {
                if (matches.getMatchValueByIndex(i) == k) {
                    matchIndexes.add(i);
                    break;
                }
            }
        }

        // ── Extract weekMatchPairs and pairTeams bitmasks ──────────────────────
        // weekMatchPairs[w][g][j] = {teamA, teamB} (teamA ≤ teamB)
        // pairTeams[w][ps]        = bitmask of teams in that pair-slot
        int[][][][] weekMatchPairs = new int[nWeeks][nRows][nCols][2];
        int[][]     pairTeams      = new int[nWeeks][N_PAIR_SLOTS];

        for (int w = 0; w < nWeeks; w++) {
            int base = w * gamesPerWeek;
            for (int g = 0; g < nRows; g++) {
                for (int j = 0; j < nCols; j++) {
                    int listPos  = base + schedGroups[g][j];
                    int matchNum = matchIndexes.get(listPos);
                    int tA = matches.getRow(matchNum);
                    int tB = matches.getCol(matchNum);
                    if (tA > tB) { int tmp = tA; tA = tB; tB = tmp; }
                    weekMatchPairs[w][g][j][0] = tA;
                    weekMatchPairs[w][g][j][1] = tB;
                    pairTeams[w][j / 2] |= (1 << tA) | (1 << tB);
                }
            }
        }

        // ── Build deltas[w][ai][t][v] ──────────────────────────────────────────
        // delta = +2 for each team in the pair-slot assigned to venue v
        int[][][][] deltas = new int[nWeeks][N_ASSIGNMENTS][nTeams][N_VENUES];
        for (int w = 0; w < nWeeks; w++) {
            for (int ai = 0; ai < N_ASSIGNMENTS; ai++) {
                for (int ps = 0; ps < N_PAIR_SLOTS; ps++) {
                    int v    = VALID_ASSIGNMENTS[ai][ps];
                    int mask = pairTeams[w][ps];
                    while (mask != 0) {
                        int t = Integer.numberOfTrailingZeros(mask);
                        deltas[w][ai][t][v] += 2;
                        mask &= mask - 1;
                    }
                }
            }
        }

        // ── Exact backtracking search ──────────────────────────────────────────
        int[]    choices     = new int[nWeeks];
        int[]    bestChoices = new int[nWeeks];
        double[] bestScore   = { Double.MAX_VALUE };
        int[][]  counts      = new int[nTeams][N_VENUES];
        Arrays.fill(choices, ORIGINAL_AI);
        Arrays.fill(bestChoices, ORIGINAL_AI);

        backtrack(0, choices, bestChoices, counts, deltas, nWeeks, nTeams, bestScore);

        // ── Accumulate final counts ────────────────────────────────────────────
        int[][] finalCounts = new int[nTeams][N_VENUES];
        for (int w = 0; w < nWeeks; w++)
            for (int t = 0; t < nTeams; t++)
                for (int v = 0; v < N_VENUES; v++)
                    finalCounts[t][v] += deltas[w][bestChoices[w]][t][v];

        int teamsInRange = 0;
        for (int t = 0; t < nTeams; t++) {
            int m = finalCounts[t][MAIN], b = finalCounts[t][BP], g = finalCounts[t][GERRY];
            if (m >= MAIN_MIN && m <= MAIN_MAX
             && b >= BP_MIN   && b <= BP_MAX
             && g >= GERRY_MIN && g <= GERRY_MAX) teamsInRange++;
        }

        return new OptResult(weekMatchPairs, bestChoices, bestScore[0],
                             finalCounts, teamsInRange, nTeams, nWeeks, true);
    }

    // ── Fixed-main layout optimizer (e.g. Config 2) ───────────────────────────
    //
    // Main courts (columns 0..mainCourts-1) are always Main — no choice needed.
    // The BP group (columns mainCourts..mainCourts+bpCourts-1) and Gerry group
    // (columns mainCourts+bpCourts..totalCourts-1) can swap each week.
    // choices[w] = 0 → BP group stays BP; 1 → BP group becomes Gerry (swap).

    private static OptResult optimizeFixed(MatchMatrix matches) {
        int nWeeks      = Config.getWeeks();
        int nTeams      = MatchMatrix.MATCHES_PER_WEEK;
        int gpw         = MatchMatrix.MATCHES_PER_WEEK;
        int[][] sg      = Config.getScheduleGroups();
        int nRows       = sg.length;
        int nCols       = sg[0].length;
        int mainCourts  = Config.getMainCourts();
        int bpCourts    = Config.getBpCourts();

        // Build sorted match index list
        int totalListSize = MatchMatrix.TOTAL_MATCHES + 1;
        List<Integer> matchIndexes = new ArrayList<>();
        for (int k = 1; k < totalListSize; k++) {
            for (int i = 1; i < totalListSize; i++) {
                if (matches.getMatchValueByIndex(i) == k) {
                    matchIndexes.add(i);
                    break;
                }
            }
        }

        // Extract weekMatchPairs[w][g][j] = {teamA, teamB}
        int[][][][] weekMatchPairs = new int[nWeeks][nRows][nCols][2];
        for (int w = 0; w < nWeeks; w++) {
            int base = w * gpw;
            for (int g = 0; g < nRows; g++) {
                for (int j = 0; j < nCols; j++) {
                    int matchNum = matchIndexes.get(base + sg[g][j]);
                    int tA = matches.getRow(matchNum);
                    int tB = matches.getCol(matchNum);
                    if (tA > tB) { int tmp = tA; tA = tB; tB = tmp; }
                    weekMatchPairs[w][g][j][0] = tA;
                    weekMatchPairs[w][g][j][1] = tB;
                }
            }
        }

        // deltas[w][swap][t][v]: swap=0 → original, swap=1 → BP/Gerry swapped
        int[][][][] deltas = new int[nWeeks][2][nTeams][N_VENUES];
        for (int w = 0; w < nWeeks; w++) {
            for (int swap = 0; swap < 2; swap++) {
                for (int g = 0; g < nRows; g++) {
                    for (int j = 0; j < nCols; j++) {
                        int v;
                        if (j < mainCourts) {
                            v = MAIN;
                        } else if (j < mainCourts + bpCourts) {
                            v = (swap == 0) ? BP : GERRY;
                        } else {
                            v = (swap == 0) ? GERRY : BP;
                        }
                        int tA = weekMatchPairs[w][g][j][0];
                        int tB = weekMatchPairs[w][g][j][1];
                        deltas[w][swap][tA][v]++;
                        deltas[w][swap][tB][v]++;
                    }
                }
            }
        }

        // Backtrack over 2^nWeeks choices
        int[]    choices     = new int[nWeeks];
        int[]    bestChoices = new int[nWeeks];
        double[] bestScore   = { Double.MAX_VALUE };
        int[][]  counts      = new int[nTeams][N_VENUES];

        backtrackFixed(0, choices, bestChoices, counts, deltas, nWeeks, nTeams, bestScore);

        int[][] finalCounts = new int[nTeams][N_VENUES];
        for (int w = 0; w < nWeeks; w++)
            for (int t = 0; t < nTeams; t++)
                for (int v = 0; v < N_VENUES; v++)
                    finalCounts[t][v] += deltas[w][bestChoices[w]][t][v];

        int teamsInRange = 0;
        for (int t = 0; t < nTeams; t++) {
            int m = finalCounts[t][MAIN], b = finalCounts[t][BP], gv = finalCounts[t][GERRY];
            if (m >= MAIN_MIN && m <= MAIN_MAX
             && b >= BP_MIN   && b <= BP_MAX
             && gv >= GERRY_MIN && gv <= GERRY_MAX) teamsInRange++;
        }

        return new OptResult(weekMatchPairs, bestChoices, bestScore[0],
                             finalCounts, teamsInRange, nTeams, nWeeks, false);
    }

    private static void backtrackFixed(
            int week, int[] choices, int[] bestChoices,
            int[][] counts, int[][][][] deltas,
            int nWeeks, int nTeams, double[] bestScore) {

        if (week == nWeeks) {
            double sc = 0.0;
            for (int t = 0; t < nTeams; t++)
                for (int v = 0; v < N_VENUES; v++) {
                    double d = counts[t][v] - TARGET[v];
                    sc += d * d;
                }
            if (sc < bestScore[0]) {
                bestScore[0] = sc;
                System.arraycopy(choices, 0, bestChoices, 0, nWeeks);
            }
            return;
        }

        for (int swap = 0; swap < 2; swap++) {
            for (int t = 0; t < nTeams; t++)
                for (int v = 0; v < N_VENUES; v++)
                    counts[t][v] += deltas[week][swap][t][v];
            choices[week] = swap;

            boolean prune = false;
            if (bestScore[0] < Double.MAX_VALUE) {
                double wt = (double)(week + 1) / nWeeks;
                double partial = 0.0;
                for (int t = 0; t < nTeams; t++)
                    for (int v = 0; v < N_VENUES; v++) {
                        double d = counts[t][v] - TARGET[v] * wt;
                        partial += d * d;
                    }
                if (partial >= bestScore[0]) prune = true;
            }

            if (!prune)
                backtrackFixed(week + 1, choices, bestChoices, counts, deltas,
                               nWeeks, nTeams, bestScore);

            for (int t = 0; t < nTeams; t++)
                for (int v = 0; v < N_VENUES; v++)
                    counts[t][v] -= deltas[week][swap][t][v];
        }
    }

    // ── Backtracking search ────────────────────────────────────────────────────

    private static void backtrack(
            int week, int[] choices, int[] bestChoices,
            int[][] counts, int[][][][] deltas,
            int nWeeks, int nTeams, double[] bestScore) {

        if (week == nWeeks) {
            double sc = 0.0;
            for (int t = 0; t < nTeams; t++)
                for (int v = 0; v < N_VENUES; v++) {
                    double d = counts[t][v] - TARGET[v];
                    sc += d * d;
                }
            if (sc < bestScore[0]) {
                bestScore[0] = sc;
                System.arraycopy(choices, 0, bestChoices, 0, nWeeks);
            }
            return;
        }

        for (int ai = 0; ai < N_ASSIGNMENTS; ai++) {
            // Apply delta
            for (int t = 0; t < nTeams; t++) {
                counts[t][MAIN]  += deltas[week][ai][t][MAIN];
                counts[t][BP]    += deltas[week][ai][t][BP];
                counts[t][GERRY] += deltas[week][ai][t][GERRY];
            }
            choices[week] = ai;

            // Pruning: scale partial counts to full-season estimate and compare
            boolean prune = false;
            if (bestScore[0] < Double.MAX_VALUE) {
                double wt      = (double)(week + 1) / nWeeks;
                double partial = 0.0;
                for (int t = 0; t < nTeams; t++)
                    for (int v = 0; v < N_VENUES; v++) {
                        double d = counts[t][v] - TARGET[v] * wt;
                        partial += d * d;
                    }
                if (partial >= bestScore[0]) prune = true;
            }

            if (!prune)
                backtrack(week + 1, choices, bestChoices, counts, deltas,
                          nWeeks, nTeams, bestScore);

            // Undo delta
            for (int t = 0; t < nTeams; t++) {
                counts[t][MAIN]  -= deltas[week][ai][t][MAIN];
                counts[t][BP]    -= deltas[week][ai][t][BP];
                counts[t][GERRY] -= deltas[week][ai][t][GERRY];
            }
        }
    }

    // ── Output ─────────────────────────────────────────────────────────────────

    public static void appendSummary(OptResult result, String matchesFile) {
        int    ok    = result.teamsInRange;
        int    n     = result.nTeams;
        String bar   = "\u2588".repeat(ok) + "\u2591".repeat(n - ok);
        String flag  = (ok >= n - 1) ? "  \u2605" : "";  // ★ for 15/16 or 16/16
        String ts    = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        try (PrintWriter pw = new PrintWriter(new FileWriter(SUMMARY_FILE, true))) {
            pw.printf("%-52s  %2d/%d  %s%s  [%s]%n", matchesFile, ok, n, bar, flag, ts);
        } catch (IOException e) {
            System.err.println("VenueOptimizer: could not write " + SUMMARY_FILE + ": " + e.getMessage());
        }
    }

    /**
     * Returns the ordered column indices (j into weekMatchPairs[w][g][j]) for week w,
     * arranged as: all Main courts, then all BP courts, then all Gerry courts.
     */
    private static int[] buildColOrder(OptResult result, int w) {
        int mc = Config.getMainCourts();
        int bc = Config.getBpCourts();
        int gc = Config.getGerryCourts();
        int tc = mc + bc + gc;
        int[] colOrder = new int[tc];
        int pos = 0;

        if (result.pairSlot) {
            int[] assign = VALID_ASSIGNMENTS[result.choices[w]];
            for (int v = 0; v < N_VENUES; v++)
                for (int ps = 0; ps < N_PAIR_SLOTS; ps++)
                    if (assign[ps] == v) {
                        colOrder[pos++] = ps * 2;
                        colOrder[pos++] = ps * 2 + 1;
                    }
        } else {
            // Main courts are always cols 0..mc-1
            for (int j = 0; j < mc; j++) colOrder[pos++] = j;
            // BP and Gerry groups may be swapped
            boolean swapped = result.choices[w] == 1;
            int[] bpCols    = swapped ? seq(mc + bc, mc + bc + gc) : seq(mc, mc + bc);
            int[] gerryCols = swapped ? seq(mc, mc + bc)           : seq(mc + bc, mc + bc + gc);
            for (int j : bpCols)    colOrder[pos++] = j;
            for (int j : gerryCols) colOrder[pos++] = j;
        }
        return colOrder;
    }

    private static int[] seq(int from, int to) {
        int[] a = new int[to - from];
        for (int i = 0; i < a.length; i++) a[i] = from + i;
        return a;
    }

    public static void writeOutput(OptResult result, String sourceFile, String outPath) {
        int mc = Config.getMainCourts();
        int bc = Config.getBpCourts();
        int gc = Config.getGerryCourts();
        int tc = mc + bc + gc;
        int nRows = result.weekMatchPairs[0].length; // 2 for all configs

        try (PrintWriter pw = new PrintWriter(new FileWriter(outPath))) {

            // ── Header row (tab-separated venue column names) ──────────────────
            pw.print("\t\t");
            for (int i = 1; i <= mc; i++) { if (i > 1) pw.print("\t"); pw.print("Main " + i); }
            for (int i = 1; i <= bc; i++) { pw.print("\t"); pw.print("BP " + i); }
            for (int i = 1; i <= gc; i++) { pw.print("\t"); pw.print("Gerry " + i); }
            pw.println();

            // ── Weekly schedule grid ───────────────────────────────────────────
            for (int w = 0; w < result.nWeeks; w++) {
                int[] colOrder = buildColOrder(result, w);
                for (int g = 0; g < nRows; g++) {
                    pw.print("Week " + (w + 1) + "\t");
                    for (int ci = 0; ci < tc; ci++) {
                        if (ci > 0) pw.print("\t");
                        int j  = colOrder[ci];
                        int tA = result.weekMatchPairs[w][g][j][0];
                        int tB = result.weekMatchPairs[w][g][j][1];
                        pw.printf("T%2d vs T%2d", tA + 1, tB + 1);
                    }
                    pw.println();
                }
            }

            // ── Team venue counts table ────────────────────────────────────────
            int totalGames = result.nWeeks * 2;
            pw.println();
            pw.printf("TARGETS  Main %d\u2013%d   BP %d\u2013%d   Gerry %d\u2013%d   (per team, %d games)%n",
                MAIN_MIN, MAIN_MAX, BP_MIN, BP_MAX, GERRY_MIN, GERRY_MAX, totalGames);
            pw.printf("Optimality score (sum of squared deviations): %.1f%n", result.score);
            pw.println();
            pw.println("TEAM VENUE COUNTS");
            pw.printf("  %-6s %6s %6s %6s %6s  %s%n", "Team", "Main", "BP", "Gerry", "Total", "Status");
            pw.println("  -----------------------------------------");
            for (int t = 0; t < result.nTeams; t++) {
                int m  = result.finalCounts[t][MAIN];
                int b  = result.finalCounts[t][BP];
                int g  = result.finalCounts[t][GERRY];
                int tot = m + b + g;
                boolean ok = m >= MAIN_MIN && m <= MAIN_MAX
                          && b >= BP_MIN   && b <= BP_MAX
                          && g >= GERRY_MIN && g <= GERRY_MAX;
                String status;
                if (ok) {
                    status = "\u2713";
                } else {
                    List<String> reasons = new ArrayList<>();
                    if (m < MAIN_MIN || m > MAIN_MAX)   reasons.add("Main="  + m);
                    if (b < BP_MIN   || b > BP_MAX)     reasons.add("BP="    + b);
                    if (g < GERRY_MIN || g > GERRY_MAX) reasons.add("Gerry=" + g);
                    status = "\u2717  (" + String.join(", ", reasons) + ")";
                }
                pw.printf("  T%2d  %6d %6d %6d %6d  %s%n", t + 1, m, b, g, tot, status);
            }
            pw.println("  -----------------------------------------");
            pw.printf("  Teams within target range:  %d/%d%n", result.teamsInRange, result.nTeams);

        } catch (IOException e) {
            System.err.println("VenueOptimizer: could not write " + outPath + ": " + e.getMessage());
        }
    }
}
