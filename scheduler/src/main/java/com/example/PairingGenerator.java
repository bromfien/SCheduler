package com.example;

import java.util.*;

public class PairingGenerator {

    private final List<Integer> elements;
    private final List<List<Pair<Integer, Integer>>> allPairings = new ArrayList<>();

    public PairingGenerator(List<Integer> elements) {
        if (elements.size() % 2 != 0) {
            throw new IllegalArgumentException("Number of elements must be even.");
        }
        this.elements = new ArrayList<>(elements);
        generatePairings(this.elements, new ArrayList<>());
        sortAllPairings();
    }

    public List<List<Pair<Integer, Integer>>> getAllPairings() {
        return allPairings;
    }
    
    // Return a list a List of Pair<Integer, Integer> from allPairings at a given index
    // Each Pair<Integer, Integer> represents a match between two elements

    public List<Pair<Integer, Integer>> getPairingAtIndex(int index) {
        if (index < 0 || index >= allPairings.size()) {
            throw new IndexOutOfBoundsException("Index out of bounds: " + index);
        }
        return allPairings.get(index);
    }
    
    // Convert the List of Pair<Integer, Integer> at a given index to a List of Integer
    public List<Integer> getPairingAsList(int index) {
        List<Pair<Integer, Integer>> pairing = getPairingAtIndex(index);
        List<Integer> result = new ArrayList<>();
        for (Pair<Integer, Integer> pair : pairing) {
            result.add(pair.getFirst());
            result.add(pair.getSecond());
        }
        return result;
    }
    
    // Count the total number of pairings

    public int countPairings() {
        return allPairings.size();
    }
    
    // Print all pairings in a readable format
    // Each pairing is a list of Pair<Integer, Integer>

    public void printPairings() {
        int idx = 1;
        for (List<Pair<Integer, Integer>> pairing : allPairings) {
            System.out.printf("%d: %s%n", idx++, pairing);
        }
    }

    // === Core Logic ===
    private void generatePairings(List<Integer> remaining, List<Pair<Integer, Integer>> current) {
        if (remaining.isEmpty()) {
            // Deep copy of current and add sorted
            List<Pair<Integer, Integer>> normalized = new ArrayList<>(current);
            normalized.sort(Comparator.comparing((Pair<Integer, Integer> p) -> p.getFirst())
                                   .thenComparing(p -> p.getSecond()));
            allPairings.add(normalized);
            return;
        }

        int first = remaining.get(0);
        for (int i = 1; i < remaining.size(); i++) {
            int second = remaining.get(i);
            Pair<Integer, Integer> pair = new Pair<>(Math.min(first, second), Math.max(first, second));

            List<Integer> next = new ArrayList<>(remaining);
            next.remove(Integer.valueOf(first));
            next.remove(Integer.valueOf(second));

            current.add(pair);
            generatePairings(next, current);
            current.remove(current.size() - 1);
        }
        
        // I want to remove any pairings that have the same elements as the first pairing (0,1), (2,3), (4,5), (6,7)
        List<Pair<Integer, Integer>> forbidden = List.of(
            new Pair<>(0, 1), new Pair<>(2, 3), new Pair<>(4, 5), new Pair<>(6, 7));

        allPairings.removeIf(pairing -> {
            for (Pair<Integer, Integer> forbiddenPair : forbidden) {
                if (pairing.contains(forbiddenPair)) {
                    return true; // Remove this pairing
                }
            }
            return false;
        });
                                                              
    }
    
    // Static method to that converts the results from getPairingAtIndex(int index) to a an array of integers
    /*public static int[] convertPairingToArray(List<Pair<Integer, Integer>> pairing) {
        int[] result = new int[pairing.size() * 2];
        for (int i = 0; i < pairing.size(); i++) {
            Pair<Integer, Integer> pair = pairing.get(i);
            result[i * 2] = pair.getFirst();
            result[i * 2 + 1] = pair.getSecond();
        }
        return result;
    }*/
    
    // Static method that takes a list of N elements long and rearranges them using the order of a second list of N elements long
    // the returned list will be the same length as the first list, but the elements will be rearranged according to the order of the second list
    public static List<Integer> rearrangeList(List<Integer> original, List<Integer> order) {
        
        List<Integer> originalCopy = new ArrayList<>(original);
        
        if (originalCopy.size() != order.size()) {
            throw new IllegalArgumentException("Original and order lists must be of the same size.");
        }
        List<Integer> rearranged = new ArrayList<>(Collections.nCopies(original.size(), 0));
        for (int i = 0; i < originalCopy.size(); i++) {
            int index = order.get(i);
            if (index < 0 || index >= originalCopy.size()) {
                throw new IndexOutOfBoundsException("Index out of bounds: " + index);
            }
            rearranged.set(index, originalCopy.get(i));
        }
        // even elements should always be greater than odd elements, e.g. (0, 1), (2, 3), (4, 5)
        for (int i = 0; i < rearranged.size(); i += 2) {
            if (i + 1 < rearranged.size() && rearranged.get(i) < rearranged.get(i + 1)) {
                // Swap if the even element is greater than the odd element
                int temp = rearranged.get(i);
                rearranged.set(i, rearranged.get(i + 1));
                rearranged.set(i + 1, temp);
            }
        }
        return rearranged;
    }
    
    // Sort all pairings lexicographically and remove duplicates
    // This method sorts the entire list of pairings and removes duplicates
    // It uses a LinkedHashSet to maintain insertion order while removing duplicates
    // It sorts the pairings based on their string representation to ensure a consistent order
    
    private void sortAllPairings() {
        // Sort entire list lexicographically
        allPairings.sort(Comparator.comparing((List<Pair<Integer, Integer>> p) -> 
            p.stream().map(Pair::toString).toList().toString()));
        // Remove duplicates by using a LinkedHashSet
        Set<String> seen = new LinkedHashSet<>();
        List<List<Pair<Integer, Integer>>> unique = new ArrayList<>();
        for (List<Pair<Integer, Integer>> pairing : allPairings) {
            String key = pairing.toString();
            if (seen.add(key)) {
                unique.add(pairing);
            }
        }
        allPairings.clear();
        allPairings.addAll(unique);
    }

    // === Helper Pair class ===
    public static class Pair<F, S> {
        private final F first;
        private final S second;

        public Pair(F first, S second) {
            this.first = first;
            this.second = second;
        }

        public F getFirst() { return first; }
        public S getSecond() { return second; }

        @Override
        public String toString() {
            return "(" + first + ", " + second + ")";
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Pair)) return false;
            Pair<?, ?> p = (Pair<?, ?>) o;
            return Objects.equals(first, p.first) && Objects.equals(second, p.second);
        }

        @Override
        public int hashCode() {
            return Objects.hash(first, second);
        }
    }
}
