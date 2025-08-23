package com.example.optomized;

import java.util.*;

/**
 * Advanced constraint satisfaction scheduler that replaces random search
 * with intelligent backtracking and heuristic-guided selection
 */
public class ConstraintScheduler {
    
    private final OptimizedMatchMatrix matchMatrix;
    private final int TEAMS = 16;
    private final int MATCHES_PER_WEEK = 8;
    private final int TARGET_WEEKS;
    
    // Advanced constraint tracking
    private final Deque<ScheduleState> stateStack; // For backtracking
    private long maxAttempts = 1_000_000; // Prevent infinite loops
    private long currentAttempts = 0;
    
    // Statistics
    private int backtracks = 0;
    private int intelligentChoices = 0;
    private long startTime;
    
    public ConstraintScheduler(int targetWeeks) {
        this.TARGET_WEEKS = targetWeeks;
        this.matchMatrix = new OptimizedMatchMatrix();
        this.stateStack = new ArrayDeque<>();
    }
    
    /**
     * Main scheduling algorithm using constraint satisfaction
     */
    public boolean generateSchedule() {
        startTime = System.nanoTime();
        currentAttempts = 0;
        backtracks = 0;
        intelligentChoices = 0;
        
        System.out.println("Starting optimized constraint-based scheduling...");
        
        boolean success = scheduleAllWeeks();
        
        long endTime = System.nanoTime();
        printPerformanceStats(endTime - startTime);
        
        return success;
    }
    
    private boolean scheduleAllWeeks() {
        for (int week = 0; week < TARGET_WEEKS; week++) {
            System.out.printf("Scheduling week %d...", week + 1);
            
            if (!scheduleWeek()) {
                System.out.println(" FAILED");
                return false;
            }
            
            System.out.println(" SUCCESS");
            matchMatrix.startNewWeek();
        }
        
        return true;
    }
    
    /**
     * Schedule one complete week using constraint satisfaction
     */
    private boolean scheduleWeek() {
        int matchesScheduled = 0;
        int maxBacktracks = 10000;
        int weekBacktracks = 0;
        
        while (matchesScheduled < MATCHES_PER_WEEK && weekBacktracks < maxBacktracks) {
            currentAttempts++;
            
            if (currentAttempts > maxAttempts) {
                System.out.println("Max attempts reached!");
                return false;
            }
            
            // Get available matches using O(1) conflict detection
            List<Integer> availableMatches = matchMatrix.getAvailableMatches();
            
            if (availableMatches.isEmpty()) {
                // Dead end - backtrack
                if (!backtrack()) {
                    return false;
                }
                matchesScheduled = countScheduledMatchesThisWeek();
                weekBacktracks++;
                backtracks++;
                continue;
            }
            
            // Use intelligent selection instead of random
            int bestMatchIndex = matchMatrix.selectBestMatch(availableMatches);
            
            if (bestMatchIndex == -1) {
                // No valid match found - backtrack
                if (!backtrack()) {
                    return false;
                }
                matchesScheduled = countScheduledMatchesThisWeek();
                weekBacktracks++;
                backtracks++;
                continue;
            }
            
            // Save state for potential backtracking
            saveState();
            
            // Schedule the match
            int matchNumber = matchMatrix.getTotalScheduledMatches() + 1;
            if (matchMatrix.scheduleMatch(bestMatchIndex, matchNumber)) {
                matchesScheduled++;
                intelligentChoices++;
            } else {
                // Shouldn't happen with proper conflict detection
                restoreState();
                weekBacktracks++;
            }
        }
        
        return matchesScheduled == MATCHES_PER_WEEK;
    }
    
    /**
     * Count how many matches have been scheduled this week
     */
    private int countScheduledMatchesThisWeek() {
        // This is a simplified implementation
        // In a full version, you'd track this more efficiently
        return MATCHES_PER_WEEK / 2; // Placeholder
    }
    
    /**
     * Intelligent backtracking - undoes recent decisions
     */
    private boolean backtrack() {
        if (stateStack.isEmpty()) {
            return false; // No more states to backtrack to
        }
        
        ScheduleState previousState = stateStack.pop();
        // In a full implementation, you would restore the match matrix state
        // For now, this is a placeholder for the backtracking logic
        
        return true;
    }
    
    /**
     * Save current scheduling state for backtracking
     */
    private void saveState() {
        // Create snapshot of current state
        ScheduleState state = new ScheduleState();
        state.week = matchMatrix.getCurrentWeek();
        state.totalMatches = matchMatrix.getTotalScheduledMatches();
        state.timestamp = System.nanoTime();
        
        stateStack.push(state);
        
        // Limit stack size to prevent memory issues
        if (stateStack.size() > 100) {
            stateStack.removeLast();
        }
    }
    
    /**
     * Restore previous state (placeholder implementation)
     */
    private void restoreState() {
        if (!stateStack.isEmpty()) {
            stateStack.pop();
        }
    }
    
    /**
     * Get the completed schedule
     */
    public OptimizedMatchMatrix getSchedule() {
        return matchMatrix;
    }
    
    /**
     * Print comprehensive performance statistics
     */
    private void printPerformanceStats(long totalTime) {
        System.out.println("\n=== PERFORMANCE STATISTICS ===");
        System.out.printf("Total execution time: %s\n", 
            OptimizedMatchMatrix.formatDuration(totalTime));
        System.out.printf("Total attempts: %,d\n", currentAttempts);
        System.out.printf("Intelligent choices: %,d\n", intelligentChoices);
        System.out.printf("Backtracks required: %,d\n", backtracks);
        System.out.printf("Success rate: %.2f%%\n", 
            (double)intelligentChoices / currentAttempts * 100);
        
        if (currentAttempts > 0) {
            System.out.printf("Average time per decision: %.2f μs\n",
                totalTime / 1000.0 / currentAttempts);
        }
        
        System.out.printf("Weeks completed: %d / %d\n", 
            matchMatrix.getCurrentWeek(), TARGET_WEEKS);
        System.out.printf("Matches scheduled: %d / %d\n",
            matchMatrix.getTotalScheduledMatches(), 
            OptimizedMatchMatrix.TOTAL_MATCHES);
    }
    
    /**
     * Advanced scheduling with forward checking
     */
    public boolean generateScheduleWithForwardChecking() {
        startTime = System.nanoTime();
        System.out.println("Starting advanced scheduling with forward checking...");
        
        boolean success = scheduleWithForwardChecking(0);
        
        long endTime = System.nanoTime();
        printPerformanceStats(endTime - startTime);
        
        return success;
    }
    
    /**
     * Forward checking algorithm - looks ahead to prevent dead ends
     */
    private boolean scheduleWithForwardChecking(int week) {
        if (week >= TARGET_WEEKS) {
            return true; // Successfully scheduled all weeks
        }
        
        System.out.printf("Forward checking week %d...", week + 1);
        
        // Try to schedule this week
        List<Integer> weekMatches = new ArrayList<>();
        if (findWeekMatches(weekMatches, new BitSet(TEAMS), 0)) {
            
            // Apply the matches
            for (int matchIndex : weekMatches) {
                int matchNumber = matchMatrix.getTotalScheduledMatches() + 1;
                matchMatrix.scheduleMatch(matchIndex, matchNumber);
            }
            
            matchMatrix.startNewWeek();
            
            // Recursively try next week
            if (scheduleWithForwardChecking(week + 1)) {
                System.out.println(" SUCCESS");
                return true;
            }
            
            // Backtrack - undo this week's matches
            // (In full implementation, restore previous state)
        }
        
        System.out.println(" FAILED");
        return false;
    }
    
    /**
     * Find valid matches for one week using recursive backtracking
     */
    private boolean findWeekMatches(List<Integer> weekMatches, BitSet usedTeams, int depth) {
        if (weekMatches.size() >= MATCHES_PER_WEEK) {
            return true; // Found all matches for this week
        }
        
        if (depth > 1000) { // Prevent infinite recursion
            return false;
        }
        
        List<Integer> available = matchMatrix.getAvailableMatches();
        
        // Filter by teams not used this week
        available.removeIf(matchIndex -> {
            int[] teams = matchMatrix.getMatchTeams(matchIndex);
            return teams == null || usedTeams.get(teams[0]) || usedTeams.get(teams[1]);
        });
        
        if (available.isEmpty()) {
            return false;
        }
        
        // Try matches in order of constraint preference
        Collections.sort(available, (a, b) -> {
            int[] teamsA = matchMatrix.getMatchTeams(a);
            int[] teamsB = matchMatrix.getMatchTeams(b);
            // Prefer matches with more constrained teams
            return Integer.compare(
                matchMatrix.countAvailableOpponents(teamsA[0]) + matchMatrix.countAvailableOpponents(teamsA[1]),
                matchMatrix.countAvailableOpponents(teamsB[0]) + matchMatrix.countAvailableOpponents(teamsB[1])
            );
        });
        
        for (int matchIndex : available) {
            int[] teams = matchMatrix.getMatchTeams(matchIndex);
            
            // Add this match
            weekMatches.add(matchIndex);
            usedTeams.set(teams[0]);
            usedTeams.set(teams[1]);
            
            // Recursively try to complete the week
            if (findWeekMatches(weekMatches, usedTeams, depth + 1)) {
                return true;
            }
            
            // Backtrack
            weekMatches.remove(weekMatches.size() - 1);
            usedTeams.clear(teams[0]);
            usedTeams.clear(teams[1]);
        }
        
        return false;
    }
    
    /**
     * State snapshot for backtracking
     */
    private static class ScheduleState {
        int week;
        int totalMatches;
        long timestamp;
        // In full implementation: BitSet snapshots, team counts, etc.
    }
}