package com.example;

import java.util.Arrays;
import java.util.List;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

//import java.io.FileWriter;
//import java.io.IOException;

public class Main {
    
    private static final int ROW = MatchMatrix.ROW;
    private static final int COL = MatchMatrix.COL;
    private static final int MATCHES_PER_WEEK = MatchMatrix.MATCHES_PER_WEEK;
    private static final boolean[][] overlap = new boolean[MATCHES_PER_WEEK*MATCHES_PER_WEEK][MATCHES_PER_WEEK*MATCHES_PER_WEEK]; // because 16*16 = 256 possible pairs
    
    /*final static List<List<Integer>> allCourtElements = List.of(
        List.of(0, 1, 2, 3, 4, 5, 6, 7),
        List.of(0, 1, 2, 3),
        List.of(0, 1, 2, 3)
    );*/
    
    /*final static List<List<Integer>> allCourtElements = List.of(
        List.of(0, 1, 2, 3, 4, 5),
        List.of(0, 1, 2, 3),
        List.of(0, 1, 2, 3)
    );*/
    
    final static List<List<Integer>> allCourtElements = List.of(
        List.of(0, 1, 2, 3),
        List.of(0, 1, 2, 3),
        List.of(0, 1, 2, 3),
        List.of(0, 1, 2, 3)
    );
    
    static {
        preComputeTable();
    }
    
    public static void main(String[] args) {

        PairingGenerator [] courts = new PairingGenerator[allCourtElements.size()];
        
        for (int i = 0; i < courts.length; i++) {
            courts[i] = new PairingGenerator(allCourtElements.get(i));
        }
        
        /*courts[0].printPairings();
        courts[1].printPairings();
        courts[2].printPairings();*/
            
        MatchMatrix matches = new MatchMatrix();
        MatchMatrix temp_matches;
        
        //int maxWeeksFound = 0;
        int break_counter_1 = 0;
        int break_counter_2 = 0;
        
        boolean keepGoing = true;
        
        long startTime = 0;
        long loop1Time = 0;
        long loop2Time = 0;
        
        while (keepGoing) {
            
            matches = new MatchMatrix();
            int match_pairings_attempts_counter = 1;
            
            outerloop:
            for (int weeks_counter = 0, match_count = 1; weeks_counter < 7; weeks_counter++) {

                int[] elements_array = new int[MATCHES_PER_WEEK]; // contains the index of the matches in a week
                int elements_counter = 0; // counter for the elements in the elements_array`
                int elements_total = 0; // total number of elements in the current week at the start of the loop
                
                temp_matches = matches.copy();
                
                for (int court_counter = 0; court_counter < allCourtElements.size(); court_counter++){

                    elements_total += allCourtElements.get(court_counter).size() / 2;
                    
                    int match_attempts_counter = 1;

                    while (elements_counter < elements_total) {
                        
                        startTime = System.nanoTime();
                        
                        if (match_attempts_counter++ % 5E4 == 0) {
                            
                            break_counter_1++;
                            loop1Time += (System.nanoTime() - startTime);
                            break outerloop;
                        }
                        
                        int random_match_index = temp_matches.generateRandomMatch();
                        int [] random_row_col_array = matches.getRowandColByIndex(random_match_index);
                        
                        boolean already_exists = false;
                        
                        for (int k = 0; k < elements_counter; k++) {
                            
                            int [] current_row_col_array = matches.getRowandColByIndex(elements_array[k]);
                            
                            if (hasOverlap(random_row_col_array[ROW], random_row_col_array[COL], current_row_col_array[ROW], current_row_col_array[COL])) {
                                
                                already_exists = true;
                                break;
                            }
                        }
                            
                        if (temp_matches.getMatchValueByRowAndCol(random_row_col_array[ROW], random_row_col_array[COL]) == 0 && !already_exists) {                
                                                                                                                                                  
                            temp_matches.setMatchValueByRowCol(random_row_col_array[ROW], random_row_col_array[COL], match_count++);
                            elements_array[elements_counter++] = random_match_index;
                        }             
                    } // while loop
                    
                    loop1Time += (System.nanoTime() - startTime);
                    
                    boolean matches_found = false;
                    
                    startTime = System.nanoTime();
                    
                    for (int k = 0; k < courts[court_counter].countPairings(); k++) {

                        int[] current_elements = Arrays.copyOfRange(
                            elements_array, 
                            elements_counter - allCourtElements.get(court_counter).size() / 2, 
                            elements_counter
                        );
                        
                        List<Integer> currentElementList = temp_matches.getRowColListByIndexes(current_elements);               
                        List<Integer> rearrangedList = PairingGenerator.rearrangeList(currentElementList,courts[court_counter].getPairingAsList(k));                        
                        
                        boolean already_exists = false;
                        
                        for (int j = 0; j < allCourtElements.get(court_counter).size() / 2; j++) {
                            
                            if (temp_matches.getMatchValueByRowAndCol((int)rearrangedList.get(2*j).intValue(), (int)rearrangedList.get(2*j+1).intValue()) != 0) {
                                
                                already_exists = true;
                                break;
                            }
                        } // for loop to check if the newList values are already used in the matches matrix
                        
                        if (!already_exists) {
                            
                            for (int j = 0; j < allCourtElements.get(court_counter).size() / 2; j++) {
                                
                                temp_matches.setMatchValueByRowCol (rearrangedList.get(2*j),rearrangedList.get(2*j+1), match_count++);
                                elements_array[elements_counter++] = temp_matches.getIndexByRowandCol (rearrangedList.get(2*j),rearrangedList.get(2*j+1));
                            }
                            
                            matches_found = true;
                            
                            if (court_counter < allCourtElements.size() - 1) {
                                elements_total += allCourtElements.get(court_counter).size() / 2;
                            }
                            
                            break;
                        }
                        
                    } // for k loop to find a pairing

                    if (!matches_found) {
                        
                        match_count = (match_count / MATCHES_PER_WEEK) * MATCHES_PER_WEEK + 1;
                        elements_total = 0;
                        elements_counter = 0;
                        court_counter = -1;

                        temp_matches = matches.copy();
                        
                        if (match_pairings_attempts_counter++ % 1E7 == 0) {
                            
                            break_counter_2++;
                            loop2Time += (System.nanoTime() - startTime);
                            break outerloop;
                        }
  
                    }
                    
                    loop2Time += (System.nanoTime() - startTime);
                    
                } // for loop to iterate through all courts
                
                matches = temp_matches.copy();
                
                if (weeks_counter + 1 == 6) { //}) || weeks_counter > maxWeeksFound) {
                    
                    //maxWeeksFound = weeks_counter;
                    
                    //matches.printMatches();
                                     
                    // Write matches.printMatches() output to a file
                    /*try (FileWriter writer = new FileWriter("matches_output.txt", true)) { // true enables append mode
                        java.io.PrintStream originalOut = System.out;
                        java.io.PrintStream fileOut = new java.io.PrintStream(new java.io.OutputStream() {
                            @Override
                            public void write(int b) throws IOException {
                                writer.write(b);
                            }
                        });
                        System.setOut(fileOut);
                        matches.printMatches();
                        System.out.flush();
                        System.setOut(originalOut);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }*/
                    
                    System.out.print("Total Weeks:" + (weeks_counter + 1) + "\t");
                    
                    System.out.println(
                        "Current system time: " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")) +
                        "\tBreak Counter 1: " + break_counter_1 +
                        "\tBreak Counter 2: " + break_counter_2 +
                        "\tLoop 1 Time: " + MatchMatrix.formatDuration(loop1Time) +
                        "\tLoop 2 Time: " + MatchMatrix.formatDuration(loop2Time)
                    );
                    break_counter_1 = 0;
                    break_counter_2 = 0;
                    loop1Time = 0;
                    loop2Time = 0;
                }

                if (weeks_counter + 1 == 7) {
                    keepGoing = false;
                    break;
                }
            } // for loop to iterate through all weeks (outerloop)
            
        } // while (keepGoing == true)
           
        matches.printMatrix();
        matches.printMatches();
    }
    
    public static boolean hasOverlap(int r1, int c1, int r2, int c2) {
        int a = (r1 << 4) | c1;
        int b = (r2 << 4) | c2;
        return overlap[a][b];
    }
                                           
    public static void preComputeTable ()
    {
        // This method is not used in the current implementation.
        // It can be used for pre-computing or initializing data if needed in the future.

        for (int r1 = 0; r1 < 16; r1++) {
            for (int c1 = 0; c1 < 16; c1++) {
                int a = (r1 << 4) | c1;
                for (int r2 = 0; r2 < 16; r2++) {
                    for (int c2 = 0; c2 < 16; c2++) {
                        int b = (r2 << 4) | c2;
                        overlap[a][b] = (r1 == r2 || c1 == c2 || r1 == c2 || c1 == r2);
                    }
                }
            }
        }
    }                                       

}
