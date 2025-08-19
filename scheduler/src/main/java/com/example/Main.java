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
    private static final int SIZE = MatchMatrix.SIZE;
    
    public static void main(String[] args) {
        
        /*
        List<List<Integer>> allElements = List.of(
            List.of(0, 1, 2, 3, 4, 5, 6, 7),
            List.of(0, 1, 2, 3),
            List.of(0, 1, 2, 3)
        );
        */
        
        List<List<Integer>> allCourtElements = List.of(
            List.of(0, 1, 2, 3),
            List.of(0, 1, 2, 3),
            List.of(0, 1, 2, 3),
            List.of(0, 1, 2, 3)
        );
        
        PairingGenerator [] courts = new PairingGenerator[allCourtElements.size()];
        
        for (int i = 0; i < courts.length; i++) {
            courts[i] = new PairingGenerator(allCourtElements.get(i));
        }
        
        
        /*courts[0].printPairings();
        courts[1].printPairings();
        courts[2].printPairings();*/
        
        
        MatchMatrix matches = new MatchMatrix();
        MatchMatrix temp_matches;
        
        int maxWeeksFound = 0;
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

                int[] elements_array = new int[SIZE];
                int elements_counter = 0;
                int elements_total = 0;
                
                temp_matches = matches.copy();
                
                // The outer loop will only run 3 times
                for (int court_counter = 0; court_counter < allCourtElements.size(); court_counter++){

                    elements_total += allCourtElements.get(court_counter).size() / 2;
                    
                    int match_attempts_counter = 1;

                    while (elements_counter < elements_total) {
                        
                        startTime = System.nanoTime();
                        
                        if (match_attempts_counter++ % 5E2 == 0) {
                            break_counter_1++;
                            break outerloop;
                        }
                        
                        boolean already_exists = false;
                        // temp_matches.randomizeList();
                        int random_match_index = temp_matches.generateRandomMatch();
                        int [] random_row_col_array = matches.getRowandColByIndex(random_match_index);
                        
                        for (int k = 0; k < elements_counter; k++){
                            
                            int [] current_row_col_array = matches.getRowandColByIndex(elements_array[k]);
                            
                            if (random_row_col_array[ROW] == current_row_col_array[ROW] || 
                                random_row_col_array[COL] == current_row_col_array[COL] ||
                                random_row_col_array[ROW] == current_row_col_array[COL] ||
                                random_row_col_array[COL] == current_row_col_array[ROW]) {
                                
                                already_exists = true;
                                break;
                            }
                        }
                    
                        if (temp_matches.getMatchValueByIndex(random_match_index) == 0 && !already_exists) {
                            
                            temp_matches.setMatchValueByIndex(random_match_index, match_count++);
                            elements_array[elements_counter++] = random_match_index;
                            already_exists = true;
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
                            
                            int tempMatchValue = temp_matches.getMatchValueByRowAndCol((int)rearrangedList.get(2*j).intValue(), (int)rearrangedList.get(2*j+1).intValue());
                            if (tempMatchValue != 0) {
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
                            
                            if (elements_counter < SIZE){
                                elements_total += allCourtElements.get(court_counter).size() / 2;
                            }
                            
                            break;
                        }
                        
                    } // for loop to find a pairing

                    if (!matches_found) {
                        
                        match_count = match_count - (match_count % SIZE) + 1;
                        elements_total = 0;
                        elements_counter = 0;
                        court_counter = -1;
                        
                        for (int k = 0; k < SIZE; k++){
                            elements_array [k] = 0; 
                        }
                        
                        temp_matches = matches.copy();
                        
                        if (match_pairings_attempts_counter++ % 1E7 == 0) {
                            break_counter_2++;
                            break outerloop;
                        }
                    }
                    
                    loop2Time += (System.nanoTime() - startTime);
                    
                } // for loop to iterate through all courts
                
                matches = temp_matches.copy();
                
                if (weeks_counter > maxWeeksFound || weeks_counter + 1 == 6) {
                    maxWeeksFound = weeks_counter;
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
                    

                    // usage in your print
                    System.out.println(
                        "Current system time: " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")) +
                        "\tBreak Counter 1: " + break_counter_1 +
                        "\tBreak Counter 2: " + break_counter_2 +
                        "\tLoop 1 Time: " + MatchMatrix.formatDuration(loop1Time) +
                        "\tLoop 2 Time: " + MatchMatrix.formatDuration(loop2Time)
                    );

                }

                if (weeks_counter + 1 == 7) {
                    keepGoing = false;
                    break;
                }
            } // for loop to iterate through all weeks
            
        } // while (keepGoing == true)
           
        matches.printMatrix();
        matches.printMatches();
    }

}
