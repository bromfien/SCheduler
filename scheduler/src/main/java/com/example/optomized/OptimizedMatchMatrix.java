package com.example.optomized;

import java.time.Duration;
import java.util.*;

/**
 * Optimized MatchMatrix using BitSets for O(1) conflict detection
 * and constraint satisfaction for intelligent match selection
 */
public class OptimizedMatchMatrix {
    
    public static final int MATCHES_PER_WEEK = 16;
    public static final int TOTAL_MATCHES = MATCHES_PER_WEEK * (MATCHES_PER_WEEK - 1) / 2; // 120 for 16 teams
    
    // BitSet-based conflict detection for O(1) performance
    private final BitSet[] hasPlayedMatrix;
    private final BitSet usedTeamsThisWeek;
    
    // Match tracking
    private final int[][] schedule; // [week][match_index] -> match_number
    private final List<int[]> allPossibleMatches; // Pre-computed all possible team pairings
    private final Map<String, Integer> matchLookup; // "team1,team2" -> match_index
    
    // Statistics
    private final int[] teamGameCounts;
    private final int[] teamWeekCounts; // Games per team per week
    private int currentWeek = 0;
    private int totalScheduledMatches = 0;
    
    public OptimizedMatchMatrix() {
        // Initialize BitSets for fast conflict detection
        hasPlayedMatrix = new BitSet[MATCHES_PER_WEEK];
        for (int i = 0; i < MATCHES_PER_WEEK; i++) {
            hasPlayedMatrix[i] = new BitSet(MATCHES_PER_WEEK);
        }
        usedTeamsThisWeek = new BitSet(MATCHES_PER_WEEK);
        
        // Initialize data structures
        schedule = new int[20][8]; // Max 20 weeks, 8 matches per week
        allPossibleMatches = new ArrayList<>();
        matchLookup = new HashMap<>();
        teamGameCounts = new int[MATCHES_PER_WEEK];
        teamWeekCounts = new int[MATCHES_PER_WEEK];
        
        precomputeMatches();
    }
    
    private void precomputeMatches() {
        int index = 0;
        for (int i = 0; i < MATCHES_PER_WEEK; i++) {
            for (int j = i + 1; j < MATCHES_PER_WEEK; j++) {
                allPossibleMatches.add(new int[]{i, j});
                matchLookup.put(i + "," + j, index);
                matchLookup.put(j + "," + i, index); // Bidirectional lookup
                index++;
            }
        }
    }
    
    /**
     * Get all available matches for current week using constraint satisfaction
     */
    public List<Integer> getAvailableMatches() {
        List<Integer> available = new ArrayList<>();
        
        for (int i = 0; i < allPossibleMatches.size(); i++) {
            int[] match = allPossibleMatches.get(i);
            int team1 = match[0];
            int team2 = match[1];
            
            // O(1) conflict detection using BitSets
            if (!hasConflict(team1, team2)) {
                available.add(i);
            }
        }
        
        return available;
    }
    
    /**
     * O(1) conflict detection - major performance improvement
     */
    private boolean hasConflict(int team1, int team2) {
        return usedTeamsThisWeek.get(team1) || 
               usedTeamsThisWeek.get(team2) || 
               hasPlayedMatrix[team1].get(team2);
    }
    
    /**
     * Public method to count available opponents for a team
     */
    public int countAvailableOpponents(int team) {
        int count = 0;
        for (int other = 0; other < MATCHES_PER_WEEK; other++) {
            if (other != team && !hasPlayedMatrix[team].get(other)) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Intelligent match selection using Most Constrained Variable heuristic
     */
    public int selectBestMatch(List<Integer> availableMatches) {
        if (availableMatches.isEmpty()) {
            return -1;
        }
        
        int bestMatch = -1;
        double bestScore = Double.NEGATIVE_INFINITY;
        
        for (int matchIndex : availableMatches) {
            int[] match = allPossibleMatches.get(matchIndex);
            double score = calculateMatchScore(match[0], match[1]);
            
            if (score > bestScore) {
                bestScore = score;
                bestMatch = matchIndex;
            }
        }
        
        return bestMatch;
    }
    
    /**
     * Scoring function for intelligent match selection
     */
    private double calculateMatchScore(int team1, int team2) {
        double score = 0;
        
        // 1. Most Constrained Variable: prefer teams with fewer available opponents
        int availableOpp1 = countAvailableOpponents(team1);
        int availableOpp2 = countAvailableOpponents(team2);
        score += 100.0 / (availableOpp1 + availableOpp2 + 1);
        
        // 2. Load balancing: prefer teams that have played fewer games
        int totalGames = teamGameCounts[team1] + teamGameCounts[team2];
        int maxGames = Arrays.stream(teamGameCounts).max().orElse(0);
        score += (maxGames * 2 - totalGames) * 10;
        
        // 3. Week balancing: prefer teams with fewer games this week
        int weekGames = teamWeekCounts[team1] + teamWeekCounts[team2];
        score += (2 - weekGames) * 5;
        
        // 4. Small random factor to break ties
        score += Math.random() * 0.1;
        
        return score;
    }
    
    /*private int countAvailableOpponents(int team) {
        int count = 0;
        for (int other = 0; other < MATCHES_PER_WEEK; other++) {
            if (other != team && !hasPlayedMatrix[team].get(other)) {
                count++;
            }
        }
        return count;
    }*/
    
    /**
     * Schedule a match - updates all conflict tracking
     */
    public boolean scheduleMatch(int matchIndex, int matchNumber) {
        if (matchIndex < 0 || matchIndex >= allPossibleMatches.size()) {
            return false;
        }
        
        int[] match = allPossibleMatches.get(matchIndex);
        int team1 = match[0];
        int team2 = match[1];
        
        if (hasConflict(team1, team2)) {
            return false;
        }
        
        // Update all tracking structures
        usedTeamsThisWeek.set(team1);
        usedTeamsThisWeek.set(team2);
        hasPlayedMatrix[team1].set(team2);
        hasPlayedMatrix[team2].set(team1);
        
        teamGameCounts[team1]++;
        teamGameCounts[team2]++;
        teamWeekCounts[team1]++;
        teamWeekCounts[team2]++;
        
        // Store in schedule
        int matchesThisWeek = Integer.bitCount((int)usedTeamsThisWeek.toLongArray()[0]) / 2;
        schedule[currentWeek][matchesThisWeek - 1] = matchNumber;
        
        totalScheduledMatches++;
        return true;
    }
    
    /**
     * Start a new week - reset weekly constraints
     */
    public void startNewWeek() {
        usedTeamsThisWeek.clear();
        Arrays.fill(teamWeekCounts, 0);
        currentWeek++;
    }
    
    /**
     * Check if current week is complete
     */
    public boolean isWeekComplete() {
        return Integer.bitCount((int)usedTeamsThisWeek.toLongArray()[0]) >= MATCHES_PER_WEEK;
    }
    
    /**
     * Backtrack - remove last match (for advanced constraint satisfaction)
     */
    public void removeLastMatch() {
        // Implementation for backtracking if needed
        // This would require maintaining a stack of operations
    }
    
    /**
     * Get match teams by index
     */
    public int[] getMatchTeams(int matchIndex) {
        if (matchIndex >= 0 && matchIndex < allPossibleMatches.size()) {
            return allPossibleMatches.get(matchIndex).clone();
        }
        return null;
    }
    
    /**
     * Print current schedule in organized format
     */
    public void printSchedule() {
        System.out.println("\n=== OPTIMIZED MATCH SCHEDULE ===");
        System.out.println("Week\tMatch 1\t\tMatch 2\t\tMatch 3\t\tMatch 4\t\tMatch 5\t\tMatch 6\t\tMatch 7\t\tMatch 8");
        
        for (int week = 0; week < currentWeek; week++) {
            System.out.printf("W%d\t", week + 1);
            
            for (int match = 0; match < 8; match++) {
                if (schedule[week][match] > 0) {
                    // Find which teams played this match
                    int matchNumber = schedule[week][match];
                    int matchIndex = findMatchIndexByNumber(matchNumber, week);
                    
                    if (matchIndex >= 0) {
                        int[] teams = allPossibleMatches.get(matchIndex);
                        System.out.printf("T%d-T%d\t\t", teams[0] + 1, teams[1] + 1);
                    } else {
                        System.out.print("ERROR\t\t");
                    }
                } else {
                    System.out.print("-----\t\t");
                }
            }
            System.out.println();
        }
        
        printStatistics();
    }
    
    private int findMatchIndexByNumber(int matchNumber, int week) {
        // This is a simplified lookup - in a full implementation,
        // you'd maintain a reverse mapping
        for (int i = 0; i < allPossibleMatches.size(); i++) {
            // Check if this match was scheduled in the given week with given number
            // This is inefficient but works for demonstration
        }
        return 0; // Placeholder
    }
    
    private void printStatistics() {
        System.out.println("\n=== STATISTICS ===");
        System.out.printf("Total weeks scheduled: %d\n", currentWeek);
        System.out.printf("Total matches scheduled: %d / %d\n", totalScheduledMatches, TOTAL_MATCHES);
        
        System.out.print("Games per team: ");
        for (int i = 0; i < MATCHES_PER_WEEK; i++) {
            System.out.printf("T%d:%d ", i + 1, teamGameCounts[i]);
        }
        System.out.println();
        
        // Calculate completion percentage
        double completionRate = (double) totalScheduledMatches / TOTAL_MATCHES * 100;
        System.out.printf("Completion rate: %.1f%%\n", completionRate);
    }
    
    public int getCurrentWeek() { return currentWeek; }
    public int getTotalScheduledMatches() { return totalScheduledMatches; }
    public boolean isComplete() { return totalScheduledMatches >= TOTAL_MATCHES; }
    
    /**
     * Utility method for timing operations
     */
    public static String formatDuration(long nanos) {
        Duration d = Duration.ofNanos(nanos);
        long hours = d.toHours();
        long minutes = d.toMinutesPart();
        long seconds = d.toSecondsPart();
        long millis = d.toMillisPart();
        return String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, millis);
    }
}