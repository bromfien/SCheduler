package com.example;

import java.util.*;
import java.io.*;

public class CourtCounter {

    static final int MAIN_START  = 0, MAIN_END  = 3;
    static final int BP_START    = 4, BP_END    = 5;
    static final int GERRY_START = 6, GERRY_END = 7;

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java CourtCounter <filename>");
            System.exit(1);
        }

        List<String> scheduleLines = new ArrayList<>();
        boolean foundWeek1 = false;

        try (BufferedReader br = new BufferedReader(new FileReader(args[0]))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!foundWeek1) {
                    if (line.startsWith("Week 1")) foundWeek1 = true;
                    else continue;
                }
                if (line.startsWith("Week")) scheduleLines.add(line);
            }
        }

        // Find highest team number dynamically
        int maxTeam = 0;
        for (String line : scheduleLines) {
            String[] cols = line.split("\t");
            for (int c = 1; c < cols.length; c++) {
                int[] teams = parseMatchup(cols[c]);
                if (teams != null) {
                    maxTeam = Math.max(maxTeam, Math.max(teams[0], teams[1]));
                }
            }
        }

        int[] main  = new int[maxTeam + 1];
        int[] bp    = new int[maxTeam + 1];
        int[] gerry = new int[maxTeam + 1];

        for (String line : scheduleLines) {
            String[] cols = line.split("\t");
            for (int c = 1; c < cols.length; c++) {
                int courtIndex = c - 1;
                int[] teams = parseMatchup(cols[c]);
                if (teams == null) continue;

                if (courtIndex >= MAIN_START && courtIndex <= MAIN_END) {
                    main[teams[0]]++;
                    main[teams[1]]++;
                } else if (courtIndex >= BP_START && courtIndex <= BP_END) {
                    bp[teams[0]]++;
                    bp[teams[1]]++;
                } else if (courtIndex >= GERRY_START && courtIndex <= GERRY_END) {
                    gerry[teams[0]]++;
                    gerry[teams[1]]++;
                }
            }
        }

        System.out.printf("     %-6s%-4s%-6s%n", "Main", "BP", "Gerry");
        for (int t = 1; t <= maxTeam; t++) {
            System.out.printf("T%-2d  %6d%4d%6d%n", t, main[t], bp[t], gerry[t]);
        }
    }

    static int[] parseMatchup(String s) {
        s = s.trim();
        String[] parts = s.split("\\s+vs\\s+");
        if (parts.length != 2) return null;
        int t1 = parseTeam(parts[0]);
        int t2 = parseTeam(parts[1]);
        if (t1 < 0 || t2 < 0) return null;
        return new int[]{t1, t2};
    }

    static int parseTeam(String s) {
        s = s.trim().replaceAll("\\s+", "");
        if (!s.startsWith("T")) return -1;
        try {
            return Integer.parseInt(s.substring(1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}