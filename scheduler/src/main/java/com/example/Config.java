package com.example;

import java.util.List;

/**
 * =====================================================================
 *  SCHEDULE CONFIGURATION  —  edit only this file to change the setup
 * =====================================================================
 *
 *  ACTIVE_CONFIG  selects which preset to use.
 *
 *  Each preset defines:
 *    - TEAMS            : total number of teams (= matches per week)
 *    - WEEKS            : number of weeks to schedule
 *    - COURT_GROUPS     : pairing-generator input; one inner list per
 *                         court group, each element is a team slot index.
 *                         The number of matches on that group = list.size()/2.
 *    - SCHEDULE_GROUPS  : how the TEAMS matches in a week are laid out
 *                         into two printed rows.  Each row lists the
 *                         within-week slot indices (0-based) that belong
 *                         to that row, in left-to-right column order.
 *    - COURT_NAMES      : column headers printed above the schedule.
 *    - MAIN_COURTS      : how many of the leftmost columns are "Main".
 *    - BP_COURTS        : how many of the next columns are "BP".
 *    - GERRY_COURTS     : how many of the next columns are "Gerry".
 */
public class Config {

    // ----------------------------------------------------------------
    //  >>>  CHANGE THIS ONE LINE TO SWITCH CONFIGURATIONS  <<<
    // ----------------------------------------------------------------
    public static final int ACTIVE_CONFIG = 3;
    // ----------------------------------------------------------------

    // Config 1 — 16 teams, 4 Main + 2 BP + 2 Gerry  (current default)
    private static final int     C1_TEAMS  = 16;
    private static final int     C1_WEEKS  = 7;
    private static final List<List<Integer>> C1_COURT_GROUPS = List.of(
        List.of(0, 1, 2, 3, 4, 5, 6, 7),
        List.of(0, 1, 2, 3),
        List.of(0, 1, 2, 3)
    );
    private static final int[][] C1_SCHEDULE_GROUPS = {
        {0, 1, 2, 3, 8,  9,  12, 13},
        {4, 5, 6, 7, 10, 11, 14, 15}
    };
    private static final String[] C1_COURT_NAMES = {
        "Main A", "Main B", "Main C", "Main D", "BP East", "BP West", "Gerry East", "Gerry West"
    };
    private static final int C1_MAIN_COURTS  = 4;
    private static final int C1_BP_COURTS    = 2;
    private static final int C1_GERRY_COURTS = 2;

    // Config 2 — 14 teams, 3 Main + 2 BP + 2 Gerry
    private static final int     C2_TEAMS  = 14;
    private static final int     C2_WEEKS  = 6;
    private static final List<List<Integer>> C2_COURT_GROUPS = List.of(
        List.of(0, 1, 2, 3, 4, 5),
        List.of(0, 1, 2, 3),
        List.of(0, 1, 2, 3)
    );
    private static final int[][] C2_SCHEDULE_GROUPS = {
        {0, 1, 2, 6,  7,  10, 11},
        {3, 4, 5, 8,  9,  12, 13}
    };
    private static final String[] C2_COURT_NAMES = {
        "Main A", "Main B", "Main C", "BP East", "BP West", "Gerry East", "Gerry West"
    };
    private static final int C2_MAIN_COURTS  = 3;
    private static final int C2_BP_COURTS    = 2;
    private static final int C2_GERRY_COURTS = 2;

    // Config 3 — 16 teams, 4 x 2-court groups (all equal)
    private static final int     C3_TEAMS  = 16;
    private static final int     C3_WEEKS  = 7;
    private static final List<List<Integer>> C3_COURT_GROUPS = List.of(
        List.of(0, 1, 2, 3),
        List.of(0, 1, 2, 3),
        List.of(0, 1, 2, 3),
        List.of(0, 1, 2, 3)
    );
    private static final int[][] C3_SCHEDULE_GROUPS = {
        {0, 1, 4,  5,  8,  9,  12, 13},
        {2, 3, 6,  7,  10, 11, 14, 15}
    };
    private static final String[] C3_COURT_NAMES = {
        "Main A", "Main B", "Main C", "Main D", "BP East", "BP West", "Gerry East", "Gerry West"
    };
    private static final int C3_MAIN_COURTS  = 4;
    private static final int C3_BP_COURTS    = 2;
    private static final int C3_GERRY_COURTS = 2;

    // ----------------------------------------------------------------
    //  Accessors — the rest of the code reads only from here
    // ----------------------------------------------------------------

    public static int getTeams() {
        switch (ACTIVE_CONFIG) {
            case 2:  return C2_TEAMS;
            case 3:  return C3_TEAMS;
            default: return C1_TEAMS;
        }
    }

    public static int getWeeks() {
        switch (ACTIVE_CONFIG) {
            case 2:  return C2_WEEKS;
            case 3:  return C3_WEEKS;
            default: return C1_WEEKS;
        }
    }

    public static List<List<Integer>> getCourtGroups() {
        switch (ACTIVE_CONFIG) {
            case 2:  return C2_COURT_GROUPS;
            case 3:  return C3_COURT_GROUPS;
            default: return C1_COURT_GROUPS;
        }
    }

    public static int[][] getScheduleGroups() {
        switch (ACTIVE_CONFIG) {
            case 2:  return C2_SCHEDULE_GROUPS;
            case 3:  return C3_SCHEDULE_GROUPS;
            default: return C1_SCHEDULE_GROUPS;
        }
    }

    public static String[] getCourtNames() {
        switch (ACTIVE_CONFIG) {
            case 2:  return C2_COURT_NAMES;
            case 3:  return C3_COURT_NAMES;
            default: return C1_COURT_NAMES;
        }
    }

    public static int getMainCourts() {
        switch (ACTIVE_CONFIG) {
            case 2:  return C2_MAIN_COURTS;
            case 3:  return C3_MAIN_COURTS;
            default: return C1_MAIN_COURTS;
        }
    }

    public static int getBpCourts() {
        switch (ACTIVE_CONFIG) {
            case 2:  return C2_BP_COURTS;
            case 3:  return C3_BP_COURTS;
            default: return C1_BP_COURTS;
        }
    }

    public static int getGerryCourts() {
        switch (ACTIVE_CONFIG) {
            case 2:  return C2_GERRY_COURTS;
            case 3:  return C3_GERRY_COURTS;
            default: return C1_GERRY_COURTS;
        }
    }
}
