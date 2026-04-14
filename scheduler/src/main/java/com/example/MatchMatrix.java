package com.example;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.time.Duration;
import java.util.ArrayList;

public class MatchMatrix {

    private int[][] matrix;
    private int[]   list;
    private final int[][] to_matrix_map;
    private final int[][] to_list_map;

    // ---------- available-match pool (change #1) ----------
    // Packed array of match indexes that have not yet been scheduled.
    // Swap-remove keeps it dense so random selection is O(1) with zero wasted draws.
    private int[] availableMatches;
    private int   availableCount;
    // Reverse map: availablePos[index] = position of 'index' in availableMatches[].
    // Allows O(1) removal when a match is scheduled.
    private int[] availablePos;
    // ------------------------------------------------------

    public static final int MATCHES_PER_WEEK = Config.getTeams();
    public static final int TOTAL_MATCHES    = MATCHES_PER_WEEK * (MATCHES_PER_WEEK - 1) / 2;
    public static final int ROW = 0;
    public static final int COL = 1;

    private int totalMatches = 0;
    private int matchCount   = 0;

    public MatchMatrix() {
        matrix        = new int[MATCHES_PER_WEEK][MATCHES_PER_WEEK];
        list          = new int[TOTAL_MATCHES + 1];
        to_matrix_map = new int[TOTAL_MATCHES + 1][2];
        to_list_map   = new int[MATCHES_PER_WEEK][MATCHES_PER_WEEK];
        availableMatches = new int[TOTAL_MATCHES];
        availablePos     = new int[TOTAL_MATCHES + 1];
        initializeMatrix();
    }

    private void initializeMatrix() {
        list[0]              = -9;
        to_matrix_map[0][ROW] = -9;
        to_matrix_map[0][COL] = -9;

        availableCount = 0;

        for (int row = 0; row < MATCHES_PER_WEEK; row++) {
            for (int col = 0; col < MATCHES_PER_WEEK; col++) {
                if (row == col) {
                    matrix[row][col] = -1;
                } else if (col > row) {
                    matrix[row][col] = -2;
                } else {
                    matrix[row][col] = 0;
                    int idx = totalMatches + 1;
                    list[idx]              = 0;
                    to_matrix_map[idx][ROW] = row;
                    to_matrix_map[idx][COL] = col;
                    to_list_map[row][col]   = idx;

                    // Add to available pool
                    availablePos[idx]              = availableCount;
                    availableMatches[availableCount] = idx;
                    availableCount++;

                    totalMatches++;
                }
            }
        }
    }

    public MatchMatrix copy() {
        MatchMatrix copy = new MatchMatrix();
        for (int row = 0; row < MATCHES_PER_WEEK; row++) {
            for (int col = 0; col < MATCHES_PER_WEEK; col++) {
                copy.matrix[row][col]     = this.matrix[row][col];
                copy.to_list_map[row][col] = this.to_list_map[row][col];
            }
        }
        for (int i = 0; i < TOTAL_MATCHES + 1; i++) {
            copy.list[i]              = this.list[i];
            copy.to_matrix_map[i][ROW] = this.to_matrix_map[i][ROW];
            copy.to_matrix_map[i][COL] = this.to_matrix_map[i][COL];
        }
        // Copy available pool
        copy.availableCount = this.availableCount;
        System.arraycopy(this.availableMatches, 0, copy.availableMatches, 0, this.availableCount);
        System.arraycopy(this.availablePos,     0, copy.availablePos,     0, TOTAL_MATCHES + 1);

        copy.totalMatches = this.totalMatches;
        copy.matchCount   = this.matchCount;
        return copy;
    }

    public void printMatrix() {
        System.out.print("     ");
        for (int i = 0; i < MATCHES_PER_WEEK; i++) {
            System.out.printf("C%-4d", i);
        }
        System.out.println();
        for (int row = 0; row < MATCHES_PER_WEEK; row++) {
            System.out.printf("R%-3d", row);
            for (int col = 0; col < MATCHES_PER_WEEK; col++) {
                if (matrix[row][col] > 0 || matrix[row][col] == -1)
                    System.out.printf("%-5s", matrix[row][col]);
                else if (matrix[row][col] == -2)
                    System.out.printf("%-5s", " |   ");
                else
                    System.out.printf("%-5s", ".....");
            }
            System.out.println();
        }
    }

    public void printMatches() {
        List<Integer> matchIndexes = new ArrayList<>();

        // Header built from Config
        System.out.print("\t\t");
        String[] courtNames = Config.getCourtNames();
        for (int n = 0; n < courtNames.length; n++) {
            if (n > 0) System.out.print("\t");
            System.out.print(courtNames[n]);
        }
        System.out.println();

        for (int k = 1; k < list.length; k++) {
            for (int i = 1; i < list.length; i++) {
                if (list[i] == k) {
                    matchIndexes.add(i);
                }
            }
        }

        int gamesPerWeek = MATCHES_PER_WEEK;
        int weekCount    = (matchIndexes.size() + gamesPerWeek - 1) / gamesPerWeek;
        int[][] groups   = Config.getScheduleGroups();

        for (int week = 0; week < weekCount; week++) {
            int base = week * gamesPerWeek;
            for (int g = 0; g < groups.length; g++) {
                System.out.print("Week " + (week + 1) + "\t");
                for (int j = 0; j < groups[g].length; j++) {
                    int idx = base + groups[g][j];
                    if (j > 0) System.out.print("\t");
                    if (idx < matchIndexes.size()) {
                        int matchNum = matchIndexes.get(idx);
                        int r = to_matrix_map[matchNum][ROW];
                        int c = to_matrix_map[matchNum][COL];
                        if (r < c)
                            System.out.printf("T%2d vs T%2d", r + 1, c + 1);
                        else
                            System.out.printf("T%2d vs T%2d", c + 1, r + 1);
                    } else {
                        System.out.print("  -   ");
                    }
                }
                System.out.println();
            }
        }
    }

    public int getMatchValueByRowCol(int row, int col) {
        validateTeamIndex(row);
        validateTeamIndex(col);
        return matrix[row][col];
    }

    public void setMatchValueByRowCol(int row, int col, int value) {
        validateTeamIndex(row);
        validateTeamIndex(col);
        if (row == col || row < 0 || col < 0 || row >= MATCHES_PER_WEEK || col >= MATCHES_PER_WEEK) {
            throw new IllegalArgumentException("Cannot update diagonal or invalid team indices.");
        }

        int r = (row > col) ? row : col;
        int c = (row > col) ? col : row;

        matrix[r][c]         = value;
        int index            = to_list_map[r][c];
        list[index]          = value;

        // Remove from available pool (swap with last element)
        if (value != 0) {
            int pos  = availablePos[index];
            int last = availableMatches[--availableCount];
            availableMatches[pos] = last;
            availablePos[last]    = pos;
            // index is no longer in the pool; its availablePos entry is now stale but never read
        }

        matchCount++;
    }

    public int getTotalMatches() {
        return totalMatches;
    }

    // Returns a new int[2] — kept for compatibility with non-hot-path callers.
    public int[] getRowandColByIndex(int index) {
        if (index < 0 || index >= TOTAL_MATCHES + 1) {
            throw new IndexOutOfBoundsException("Index out of bounds: " + index);
        }
        return new int[]{to_matrix_map[index][ROW], to_matrix_map[index][COL]};
    }

    // change #3 — allocation-free row/col accessors for use in the hot path
    public int getRow(int index) { return to_matrix_map[index][ROW]; }
    public int getCol(int index) { return to_matrix_map[index][COL]; }

    public int getIndexByRowandCol(int rowValue, int colValue) {
        validateTeamIndex(rowValue);
        validateTeamIndex(colValue);
        if (rowValue == colValue) {
            throw new IllegalArgumentException("Cannot use diagonal.");
        }
        int r = (rowValue > colValue) ? rowValue : colValue;
        int c = (rowValue > colValue) ? colValue : rowValue;
        return to_list_map[r][c];
    }

    public int getMatchValueByIndex(int index) {
        if (index <= 0 || index > TOTAL_MATCHES + 1) {
            throw new IndexOutOfBoundsException("Index out of bounds: " + index);
        }
        return list[index];
    }

    public void setMatchValueByIndex(int index, int value) {
        if (index <= 0 || index > TOTAL_MATCHES + 1) {
            throw new IndexOutOfBoundsException("Index out of bounds: " + index);
        }
        setMatchValueByRowCol(to_matrix_map[index][ROW], to_matrix_map[index][COL], value);
    }

    public int getMatchValueByRowAndCol(int row, int col) {
        validateTeamIndex(row);
        validateTeamIndex(col);
        int r = (row > col) ? row : col;
        int c = (row > col) ? col : row;
        return matrix[r][c];
    }

    public int[] getRowColArrayByIndexes(int[] indexes) {
        return getRowColArrayByIndexes(indexes, indexes.length);
    }

    // change #7: length-limited overload so callers can pass a pre-allocated scratch
    // array with only the first 'length' entries used — avoids Arrays.copyOfRange
    public int[] getRowColArrayByIndexes(int[] indexes, int length) {
        int[] matchList = new int[length * 2];
        int pos = 0;
        for (int i = 0; i < length; i++) {
            int index = indexes[i];
            if (index <= 0 || index > TOTAL_MATCHES + 1) {
                throw new IndexOutOfBoundsException("Index out of bounds: " + index);
            }
            matchList[pos++] = to_matrix_map[index][ROW];
            matchList[pos++] = to_matrix_map[index][COL];
        }
        return matchList;
    }

    /**
     * Counts how many matches in the available pool have both teams free
     * (not marked in teamUsed[]). Used by Phase 1 forward checking to
     * detect dead ends before they burn 50,000 draws.
     * teamUsed is indexed by 0-based team number.
     */
    public int countFreeMatches(boolean[] teamUsed) {
        int count = 0;
        for (int i = 0; i < availableCount; i++) {
            int idx = availableMatches[i];
            if (!teamUsed[to_matrix_map[idx][ROW]] && !teamUsed[to_matrix_map[idx][COL]]) {
                count++;
            }
        }
        return count;
    }

    // change #1 — O(1) random pick from the available pool, zero wasted draws
    public int generateRandomMatch() {
        int pos = ThreadLocalRandom.current().nextInt(availableCount);
        return availableMatches[pos];
    }

    private void validateTeamIndex(int team) {
        if (team < 0 || team >= MATCHES_PER_WEEK) {
            throw new IllegalArgumentException("Team index out of bounds: " + team);
        }
    }

    public static String formatDuration(long nanos) {
        Duration d       = Duration.ofNanos(nanos);
        long     minutes = d.toMinutesPart();
        long     seconds = d.toSecondsPart();
        long     millis  = d.toMillisPart();
        return String.format("%02d:%02d.%03d", minutes, seconds, millis);
    }
}
