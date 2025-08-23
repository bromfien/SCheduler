package com.example.optomized;

import java.util.*;

/**
 * Optimized pairing generator using primitive arrays and efficient algorithms
 * Major performance improvements over List-based approach
 */
public class OptimizedPairingGenerator {
    
    private final int[] elements;
    private final int[][][] allPairings; // [pairing_index][pair_index][team1,team2]
    private final int totalPairings;
    private final int pairsPerPairing;
    
    // Pre-computed lookup tables for O(1) access
    private final Map<String, Integer> pairingLookup;
    
    public OptimizedPairingGenerator(int[] elements) {
        if (elements.length % 2 != 0) {
            throw new IllegalArgumentException("Number of elements must be even.");
        }
        
        this.elements = elements.clone();
        this.pairsPerPairing = elements.length / 2;
        
        // Calculate total number of possible pairings
        this.totalPairings = calculateTotalPairings(elements.length);
        
        // Pre-allocate arrays for maximum performance
        this.allPairings = new int[totalPairings][pairsPerPairing][2];
        this.pairingLookup = new HashMap<>();
        
        generateAllPairings();
        createLookupTable();
    }
    
    /**
     * Calculate total number of unique pairings using mathematical formula
     * For n elements: (n-1)!! = (n-1) × (n-3) × ... × 3 × 1
     */
    private int calculateTotalPairings(int n) {
        if (n <= 0) return 0;
        if (n == 2) return 1;
        
        int result = 1;
        for (int i = n - 1; i > 0; i -= 2) {
            result *= i;
        }
        return result;
    }
    
    /**
     * Generate all possible pairings using optimized recursive algorithm
     */
    private void generateAllPairings() {
        int[] remaining = elements.clone();
        int[] currentPairing = new int[pairsPerPairing * 2]; // Flattened pairs
        generatePairingsRecursive(remaining, elements.length, currentPairing, 0, 0);
    }
    
    /**
     * Optimized recursive pairing generation using primitive arrays
     */
    private void generatePairingsRecursive(int[] remaining, int remainingCount, 
                                         int[] currentPairing, int pairIndex, int pairingCount) {
        if (remainingCount == 0) {
            // Convert flattened pairing to structured format and store
            storePairing(currentPairing, pairingCount);
            return;
        }
        
        if (remainingCount < 2) return; // Invalid state
        
        int first = remaining[0];
        
        // Try pairing first element with each subsequent element
        for (int i = 1; i < remainingCount; i++) {
            int second = remaining[i];
            
            // Store this pair in flattened format
            currentPairing[pairIndex * 2] = Math.min(first, second);
            currentPairing[pairIndex * 2 + 1] = Math.max(first, second);
            
            // Create new remaining array without the paired elements
            int[] newRemaining = new int[remainingCount - 2];
            int newIndex = 0;
            
            for (int j = 1; j < remainingCount; j++) {
                if (j != i) { // Skip the second element we're pairing
                    newRemaining[newIndex++] = remaining[j];
                }
            }
            
            // Recursive call
            generatePairingsRecursive(newRemaining, remainingCount - 2, 
                                    currentPairing, pairIndex + 1, pairingCount);
        }
        
        // After trying all pairings with first element, increment pairing count
        if (pairIndex == 0) {
            // This is only true for the root call
        }
    }
    
    private int storedPairings = 0;
    
    /**
     * Store a completed pairing in optimized format
     */
    private void storePairing(int[] flatPairing, int pairingIndex) {
        if (storedPairings >= totalPairings) return;
        
        // Convert flattened pairing to structured format
        for (int i = 0; i < pairsPerPairing; i++) {
            allPairings[storedPairings][i][0] = flatPairing[i * 2];
            allPairings[storedPairings][i][1] = flatPairing[i * 2 + 1];
        }
        
        storedPairings++;
    }
    
    /**
     * Create lookup table for fast pairing identification
     */
    private void createLookupTable() {
        for (int i = 0; i < storedPairings; i++) {
            String key = pairingToString(i);
            pairingLookup.put(key, i);
        }
    }
    
    /**
     * Convert pairing to string key for lookup
     */
    private String pairingToString(int pairingIndex) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pairsPerPairing; i++) {
            if (i > 0) sb.append("|");
            sb.append(allPairings[pairingIndex][i][0])
              .append(",")
              .append(allPairings[pairingIndex][i][1]);
        }
        return sb.toString();
    }
    
    /**
     * Get pairing at index - O(1) performance
     */
    public int[][] getPairingAtIndex(int index) {
        if (index < 0 || index >= storedPairings) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + storedPairings);
        }
        
        // Return copy to prevent modification
        int[][] result = new int[pairsPerPairing][2];
        for (int i = 0; i < pairsPerPairing; i++) {
            result[i][0] = allPairings[index][i][0];
            result[i][1] = allPairings[index][i][1];
        }
        return result;
    }
    
    /**
     * Get pairing as flattened array - most efficient format
     */
    public int[] getPairingAsArray(int index) {
        if (index < 0 || index >= storedPairings) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + storedPairings);
        }
        
        int[] result = new int[pairsPerPairing * 2];
        for (int i = 0; i < pairsPerPairing; i++) {
            result[i * 2] = allPairings[index][i][0];
            result[i * 2 + 1] = allPairings[index][i][1];
        }
        return result;
    }
    
    /**
     * Find pairing index by content - O(1) lookup
     */
    public int findPairingIndex(int[][] pairing) {
        String key = arrayPairingToString(pairing);
        return pairingLookup.getOrDefault(key, -1);
    }
    
    private String arrayPairingToString(int[][] pairing) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pairing.length; i++) {
            if (i > 0) sb.append("|");
            int first = Math.min(pairing[i][0], pairing[i][1]);
            int second = Math.max(pairing[i][0], pairing[i][1]);
            sb.append(first).append(",").append(second);
        }
        return sb.toString();
    }
    
    /**
     * Get total number of pairings
     */
    public int getTotalPairings() {
        return storedPairings;
    }
    
    /**
     * Check if a specific pair exists in a pairing
     */
    public boolean containsPair(int pairingIndex, int team1, int team2) {
        if (pairingIndex < 0 || pairingIndex >= storedPairings) {
            return false;
        }
        
        int min = Math.min(team1, team2);
        int max = Math.max(team1, team2);
        
        for (int i = 0; i < pairsPerPairing; i++) {
            if (allPairings[pairingIndex][i][0] == min && 
                allPairings[pairingIndex][i][1] == max) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Static method to rearrange elements according to a pattern - optimized with primitives
     */
    public static int[] rearrangeArray(int[] original, int[] order) {
        if (original.length != order.length) {
            throw new IllegalArgumentException("Arrays must have the same length");
        }
        
        int[] result = new int[original.length];
        
        for (int i = 0; i < original.length; i++) {
            int targetIndex = order[i];
            if (targetIndex < 0 || targetIndex >= original.length) {
                throw new IndexOutOfBoundsException("Invalid order index: " + targetIndex);
            }
            result[targetIndex] = original[i];
        }
        
        // Ensure pairs are in ascending order: (smaller, larger)
        for (int i = 0; i < result.length; i += 2) {
            if (i + 1 < result.length && result[i] > result[i + 1]) {
                int temp = result[i];
                result[i] = result[i + 1];
                result[i + 1] = temp;
            }
        }
        
        return result;
    }
    
    /**
     * Print all pairings in compact format
     */
    public void printPairings() {
        System.out.println("=== OPTIMIZED PAIRINGS ===");
        System.out.printf("Total pairings generated: %d\n", storedPairings);
        System.out.printf("Pairs per pairing: %d\n", pairsPerPairing);
        
        for (int i = 0; i < Math.min(storedPairings, 10); i++) { // Show first 10
            System.out.printf("%3d: ", i + 1);
            for (int j = 0; j < pairsPerPairing; j++) {
                if (j > 0) System.out.print(" | ");
                System.out.printf("(%d,%d)", allPairings[i][j][0] + 1, allPairings[i][j][1] + 1);
            }
            System.out.println();
        }
        
        if (storedPairings > 10) {
            System.out.printf("... and %d more pairings\n", storedPairings - 10);
        }
    }
    
    /**
     * Validate that all pairings are unique and complete
     */
    public boolean validatePairings() {
        Set<String> seen = new HashSet<>();
        
        for (int i = 0; i < storedPairings; i++) {
            String key = pairingToString(i);
            if (seen.contains(key)) {
                System.out.printf("Duplicate pairing found at index %d\n", i);
                return false;
            }
            seen.add(key);
            
            // Validate that all elements are used exactly once
            boolean[] used = new boolean[elements.length];
            for (int j = 0; j < pairsPerPairing; j++) {
                int team1 = allPairings[i][j][0];
                int team2 = allPairings[i][j][1];
                
                if (used[team1] || used[team2]) {
                    System.out.printf("Team reused in pairing %d\n", i);
                    return false;
                }
                used[team1] = used[team2] = true;
            }
        }
        
        return true;
    }
    
    /**
     * Get memory usage statistics
     */
    public void printMemoryStats() {
        long pairingArrayBytes = (long)storedPairings * pairsPerPairing * 2 * 4; // int = 4 bytes
        long lookupTableBytes = pairingLookup.size() * 50; // Rough estimate for String keys
        
        System.out.println("=== MEMORY USAGE ===");
        System.out.printf("Pairing arrays: %,d bytes (%.1f MB)\n", 
            pairingArrayBytes, pairingArrayBytes / 1024.0 / 1024.0);
        System.out.printf("Lookup table: %,d bytes (%.1f KB)\n", 
            lookupTableBytes, lookupTableBytes / 1024.0);
        System.out.printf("Total estimated: %.1f MB\n", 
            (pairingArrayBytes + lookupTableBytes) / 1024.0 / 1024.0);
    }
}