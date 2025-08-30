package com.example;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.time.Duration;
import java.util.ArrayList;
//import java.util.Collections;

public class MatchMatrix {

    private int[][] matrix;
    private int[] list;
    private final int[][] to_matrix_map;
    private final int[][] to_list_map;
    public static final int MATCHES_PER_WEEK = 16;
    public static final int TOTAL_MATCHES = MATCHES_PER_WEEK * (MATCHES_PER_WEEK - 1) / 2; // 120 for 16 teams
    public static final int ROW = 0;
    public static final int COL = 1;
    private int totalMatches = 0;
    private int matchCount = 0;

    // Constructor to initialize the match matrix
    public MatchMatrix() {
        matrix = new int[MATCHES_PER_WEEK][MATCHES_PER_WEEK];
        list = new int[TOTAL_MATCHES+1];
        to_matrix_map = new int[TOTAL_MATCHES+1][2];
        to_list_map = new int[MATCHES_PER_WEEK][MATCHES_PER_WEEK];
        
        initializeMatrix();
    }

    // Initialize the match matrix
    // This method will set the diagonal to -1 (same team)
    // and the upper triangle to -2 (unused)
    // The lower triangle will be initialized to 0 (no match)
    // It will also count the total number of matches
    // The total matches will be the number of unique matches (lower triangle)
    private void initializeMatrix() {
        list[0] = -9; // Index 0 is unused
        to_matrix_map[0][ROW] = -9; // Unused
        to_matrix_map[0][COL] = -9; // Unused
        for (int row = 0; row < MATCHES_PER_WEEK; row++) {
            for (int col = 0; col < MATCHES_PER_WEEK; col++) {
                if (row == col) {
                    matrix[row][col] = -1; // Same team
                } else if (col > row) {
                    matrix[row][col] = -2; // Unused upper triangle
                } else {
                    matrix[row][col] = 0;
                    list[totalMatches + 1] = 0;
                    to_matrix_map[totalMatches + 1][ROW] = row;
                    to_matrix_map[totalMatches + 1][COL] = col;
                    to_list_map[row][col] = totalMatches + 1;
                    totalMatches++;
                }
            }
        }
    }
    
    public MatchMatrix copy() {
        MatchMatrix copy = new MatchMatrix();
        for (int row = 0; row < MATCHES_PER_WEEK; row++) {
            for (int col = 0; col < MATCHES_PER_WEEK; col++) {
                copy.matrix[row][col] = this.matrix[row][col];
                copy.to_list_map[row][col] = this.to_list_map[row][col];
            }
        }
        for (int i = 0; i < TOTAL_MATCHES+1; i++) {
            copy.list[i] = this.list[i];
            copy.to_matrix_map[i][ROW] = this.to_matrix_map[i][ROW];
            copy.to_matrix_map[i][COL] = this.to_matrix_map[i][COL];
        }
        copy.totalMatches = this.totalMatches;
        copy.matchCount = this.matchCount;
        return copy;
    }
    
    // Create a method to print the match matrix
    // This method will print the matrix in a readable format
    // It will show the team numbers along the top and left side
    // and the match values in the cells
    // It will also handle the unused upper triangle and diagonal
    // highlight values greater than 0 to green
    
    
    public void printMatrix() {

        System.out.print("     ");
        for (int i = 0; i < MATCHES_PER_WEEK; i++) {
            System.out.printf("C%-4d", i + 0);
        }
        System.out.println();
        for (int row = 0; row < MATCHES_PER_WEEK; row++) {
            System.out.printf("R%-3d", row + 0);
            for (int col = 0; col < MATCHES_PER_WEEK; col++) {
                if (matrix[row][col] > 0 || matrix[row][col] == -1) // Highlight values greater than 0 or -1 (same team)
                    System.out.printf("%-5s", matrix[row][col]);
                else if (matrix[row][col] == -2)
                    System.out.printf("%-5s"," |   ");
                else
                    System.out.printf("%-5s",".....");
            }
            System.out.println();
        }
    }
    
    // print matrix data structure but for each entry starting at 1 printing the two teams
    // that are playing against each othe
    // ignore values that are less than 1
    // print the teams in the format "T1 vs T2"
    // print 8 matches per line
    // print week number at the start of each line. there are two matches per week
    
    public void printMatches() {
        // Gather all valid match indexes (starting at 1) in order
        List<Integer> matchIndexes = new ArrayList<>();
        
        System.out.println("\t\tMain A \t\tMain B \t\tMain C \t\tMain D\t\tBP East\t\tBP West\t\tGerry East\tGerry West");
        
        for (int k = 1; k < list.length; k++) {
            for (int i = 1; i < list.length; i++) {
                if (list[i] == k) {
                    matchIndexes.add(i);
                }
            }
        }

        int gamesPerWeek = MATCHES_PER_WEEK;
        int weekCount = (matchIndexes.size() + gamesPerWeek - 1) / gamesPerWeek;
        int[][] groups = {
            //{0,1,2,3,8,9,12,13},  // 4, 2, 2    
            //{4,5,6,7,10,11,14,15} // 4, 2, 2
            {0,1,4,5,8,9,12,13},    // 4 x 2
            {2,3,6,7,10,11,14,15}   // 4 x 2
            //{0,1,2,6,7,10,11},    // 3, 2, 2
            //{3,4,5,8,9,12,13}     // 3, 2, 2
        };

        for (int week = 0; week < weekCount; week++) {
            int base = week * gamesPerWeek;
            for (int g = 0; g < groups.length; g++) {
                System.out.print("Week " + (week + 1) + "\t");
                for (int j = 0; j < groups[g].length; j++) {
                    int idx = base + groups[g][j];
                    if (j > 0) System.out.print("\t");
                    if (idx < matchIndexes.size()) {
                        int matchNum = matchIndexes.get(idx);
                        int[] teams = getRowandColByIndex(matchNum);
                        if (teams[0] < teams[1]) 
                            System.out.printf("T%d vs T%d", teams[0] + 1, teams[1] + 1);
                        else
                            System.out.printf("T%d vs T%d", teams[1] + 1, teams[0] + 1);
                    } else {
                        System.out.print("-");
                    }
                }
                System.out.println();
            }
        }
    }

    // Create a method to get a match between two teams
    // teamA and teamB are 1-based indices (1 to 16)
    // This method will return the match value (e.g., score, result, etc.)
    // It will ensure that the indices are valid (1 to 16)
    // If the match does not exist, it will return -2 (unused upper triangle)
    // If the match is invalid (same team or out of bounds), it will throw an IllegalArgumentException
    // If the match exists, it will return the match value
    
    public int getMatchValueByRowCol(int row, int col) {
        validateTeamIndex(row);
        validateTeamIndex(col);
        return matrix[row][col];
    }

    // Create a method to set a match between two teams
    // teamA and teamB are 1-based indices (1 to 16)
    // value is the match value (e.g., score, result, etc.)
    // This method will update the match value in the matrix
    // and ensure that the match is only set once (i.e., no duplicates)
    // It will also ensure that the match is not set for the same team (diagonal)
    // and that the indices are valid (1 to 16)
    // If the match already exists, it will update the value
    // If the match is invalid (same team or out of bounds), it will throw an IllegalArgumentException
    public void setMatchValueByRowCol(int row, int col, int value) {
        validateTeamIndex(row);
        validateTeamIndex(col);
        if (row == col || row < 0 || col < 0 || row >= MATCHES_PER_WEEK || col >= MATCHES_PER_WEEK) {
            throw new IllegalArgumentException("Cannot update diagonal or invalid team indices.");
        }

        if (row > col) {
            matrix[row][col] = value;
            int index = to_list_map[row][col];
            list[index] = value;
        } else {
            matrix[col][row] = value;
            int index = to_list_map[col][row];
            list[index] = value;
        }
        matchCount++;
        
    }
    // Create a method to get the total number of matches
    public int getTotalMatches() {
        return totalMatches;
    }
    
    // Create a method to get a row and column by index betweeo 0 and totalMatches - 1 and return an array of two integers
    public int[] getRowandColByIndex(int index) {
        if (index < 0 || index >= TOTAL_MATCHES + 1) {                   
            throw new IndexOutOfBoundsException("Index must be between 0 and " + ((MATCHES_PER_WEEK * (MATCHES_PER_WEEK + 1) / 2) - 1) + ".");
        }
        return new int[]{to_matrix_map[index][ROW], to_matrix_map[index][COL]};
    }
    
    // get the index by row and col
    
    public int getIndexByRowandCol(int rowValue, int colValue) {
        validateTeamIndex(rowValue);
        validateTeamIndex(colValue);
        if (rowValue == colValue || rowValue < 0 || colValue < 0 || rowValue >= MATCHES_PER_WEEK || colValue >= MATCHES_PER_WEEK) {
            throw new IllegalArgumentException("Cannot update diagonal or invalid team indices.");
        }
        return to_list_map[rowValue][colValue];
    }
    
                    
    // Create a method to get a match by index betweeo 0 and totalMatches - 1
    public int getMatchValueByIndex(int index) {
        if (index <= 0 || index > TOTAL_MATCHES + 1) {
            throw new IndexOutOfBoundsException("Index must be between 0 and " + (TOTAL_MATCHES) + ".");
        }
        return list[index];
    }           
    
    // Create a method to set a match by index betweeo 0 and totalMatches - 1
    
    public void setMatchValueByIndex(int index, int value) {
        if (index <= 0 || index > TOTAL_MATCHES + 1) {
            throw new IndexOutOfBoundsException("Index must be between 0 and " + (TOTAL_MATCHES) + ".");
        }
        int row = to_matrix_map[index][ROW];
        int col = to_matrix_map[index][COL];
        setMatchValueByRowCol(row, col, value);
    }
    
    // Get the match value by row and column
    // This method will return the match value for a given row and column
    // It will ensure that the row and column are valid (1 to 16)
    public int getMatchValueByRowAndCol(int row, int col) {
        validateTeamIndex(row);
        validateTeamIndex(col);
        if (row > col) {
            return matrix[row][col]; // Return the match value for row > col
        } else {        
            return matrix[col][row]; // Return the match value for row < col
        }
    }    
     
    // function provides a List of matches by an array of indexes and return a list of teams (1-16).
    // Each match has two teams, so the list will have 2 * totalMatches elements.
    
    public List<Integer> getRowColListByIndexes(int[] indexes) {
        List<Integer> matchList = new ArrayList<>();
        for (int index : indexes) {
            if (index <= 0 || index > TOTAL_MATCHES + 1) {
                throw new IndexOutOfBoundsException("Index must be between 0 and " + (TOTAL_MATCHES) + ".");
            }
            int [] rowCol = getRowandColByIndex(index);
            matchList.add(rowCol[ROW]);
            matchList.add(rowCol[COL]);
        }
        return matchList;
    }
    
    public int [] getRowColArrayByIndexes(int[] indexes) {
        //List<Integer> matchList = new ArrayList<>();
        int [] matchList = new int[indexes.length * 2];
        int pos = 0;
        for (int index : indexes) {
            if (index <= 0 || index > TOTAL_MATCHES + 1) {
                throw new IndexOutOfBoundsException("Index must be between 0 and " + (TOTAL_MATCHES) + ".");
            }
            int [] rowCol = getRowandColByIndex(index);
            matchList[pos++] = rowCol[ROW];
            matchList[pos++] = rowCol[COL];
        }
        return matchList;
    }
    
    
    public List<Integer> getMatchListByIndex(int index) {
        if (index <= 0 || index > TOTAL_MATCHES + 1) {
            throw new IndexOutOfBoundsException("Index must be between 0 and " + (TOTAL_MATCHES) + ".");
        }
        List<Integer> matchList = new ArrayList<>();
        int match = list[index];
        matchList.add(to_matrix_map[match][ROW]);
        matchList.add(to_matrix_map[match][COL]);
        return matchList;
    } 
    
    // create a method that randomly generates a value a value between 0 and totalMatches - 1
    // that value in the matrix must be set to 0 to be valid
    // if the value is already set, generate a new value until a valid one is found
    public int generateRandomMatch() {
        int index;
        do {
            index = ThreadLocalRandom.current().nextInt(1, TOTAL_MATCHES + 1);
        } while (getMatchValueByIndex(index) != 0);
        return index;
    }
                                 
    // Validate team index to ensure it is between 1 and 16
    // This method will throw an IllegalArgumentException if the index is out of bounds
    private void validateTeamIndex(int team) {
        if (team < 0 || team >= MATCHES_PER_WEEK) {
            throw new IllegalArgumentException("Team index must be between 0 and 15.");
        }
    }
    
    public static String formatDuration(long nanos) {
        Duration d = Duration.ofNanos(nanos);
        long hours   = d.toHours();
        long minutes = d.toMinutesPart();
        long seconds = d.toSecondsPart();
        long millis  = d.toMillisPart();
        return String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, millis);
    }
}
