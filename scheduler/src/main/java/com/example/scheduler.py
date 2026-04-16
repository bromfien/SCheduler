#!/usr/bin/env python3
"""
Schedule Venue Shuffler  (pair-slot model)
==========================================
Reads Java volleyball scheduler output files and finds the optimal venue
assignment for each match using an exact backtracking search.

Constraint: matches are always moved in "pair-slots" — the atomic unit is
the 2-court, 2-row block that covers exactly 4 teams across 4 matches per
week.  Every move affects exactly 4 matches and changes each involved
team's venue count by exactly ±2 (always even).

Weekly court layout
-------------------
  Pair-slot 0  →  Main A + Main B   (columns 0,1)
  Pair-slot 1  →  Main C + Main D   (columns 2,3)
  Pair-slot 2  →  BP East + BP West (columns 4,5)
  Pair-slot 3  →  Gerry East + West (columns 6,7)

Each week's venue assignment: 2 pair-slots → Main, 1 → BP, 1 → Gerry
Valid assignments per week:  C(4,2) × 2 = 12
Search space (7 weeks):     12^7 ≈ 36M — solved in ~0.03 s with pruning

Target per team (14 total matches)
-----------------------------------
  Main:  6–8  games  (average 7.0 = 3.5 weeks × 2 matches/week)
  BP:    2–4  games  (average 3.5)
  Gerry: 2–4  games  (average 3.5)

Usage
-----
  python3 shuffle_venues.py <output_dir>

Output
------
  Prints results for each file to stdout.
  Writes venue_schedule_<filename>.txt alongside each input file.
"""

import argparse
import glob
import os
import re
import sys
from collections import defaultdict
from itertools import combinations

import numpy as np


# ── Constants ────────────────────────────────────────────────────────────────

VENUE_MAIN  = "Main"
VENUE_BP    = "BP"
VENUE_GERRY = "Gerry"
VENUE_IDX   = {VENUE_MAIN: 0, VENUE_BP: 1, VENUE_GERRY: 2}
TARGET      = [7.0, 3.5, 3.5]   # ideal per-team counts [Main, BP, Gerry]

PAIR_NAMES = ["Main AB", "Main CD", "BP    ", "Gerry "]

# The 12 valid weekly assignments (each is a 4-tuple of venue strings)
def _build_valid_assignments():
    result = []
    for main_slots in combinations(range(4), 2):
        remaining = [s for s in range(4) if s not in main_slots]
        for bp_slot in remaining:
            gerry_slot = next(s for s in remaining if s != bp_slot)
            result.append(tuple(
                VENUE_MAIN  if i in main_slots else
                VENUE_BP    if i == bp_slot    else
                VENUE_GERRY
                for i in range(4)
            ))
    return result

VALID_ASSIGNMENTS = _build_valid_assignments()   # 12 tuples
ORIGINAL_ASSIGNMENT = (VENUE_MAIN, VENUE_MAIN, VENUE_BP, VENUE_GERRY)
ORIGINAL_AI = VALID_ASSIGNMENTS.index(ORIGINAL_ASSIGNMENT)


# ── Parsing ───────────────────────────────────────────────────────────────────

def parse_schedule(path):
    """
    Parse the printMatches() table from a Java scheduler output file.

    Returns
    -------
    pair_teams : list[list[frozenset]]
        pair_teams[week][pair_slot] = frozenset of 4 team indices (0-based)
    week_rows  : list[list[list[tuple|None]]]
        week_rows[week][row][col] = (team_a, team_b) or None  (0-based teams)
    n_weeks    : int
    """
    week_rows_raw = defaultdict(list)

    with open(path, encoding="utf-8", errors="replace") as fh:
        for line in fh:
            line = line.rstrip()
            if not line.startswith("Week"):
                continue
            parts = line.split('\t')
            wn = int(parts[0].split()[1]) - 1   # 0-indexed week
            cols = []
            for col in range(1, 9):
                s = parts[col].strip() if col < len(parts) else ""
                m = re.search(r'T\s*(\d+)\s*vs\s*T\s*(\d+)', s)
                cols.append(
                    (int(m.group(1)) - 1, int(m.group(2)) - 1) if m else None
                )
            week_rows_raw[wn].append(cols)

    if not week_rows_raw:
        return None, None, 0

    n_weeks = max(week_rows_raw) + 1

    pair_teams = []
    week_rows  = []
    for w in range(n_weeks):
        rows = week_rows_raw[w]      # should be exactly 2 rows
        ps_teams = []
        for ps in range(4):
            teams = set()
            for row in rows:
                for col in (ps * 2, ps * 2 + 1):
                    pair = row[col] if col < len(row) else None
                    if pair:
                        teams.add(pair[0])
                        teams.add(pair[1])
            ps_teams.append(frozenset(teams))
        pair_teams.append(ps_teams)
        week_rows.append(rows)

    return pair_teams, week_rows, n_weeks


def count_teams(pair_teams, n_weeks):
    return max(t for week in pair_teams for ps in week for t in ps) + 1


# ── Delta precomputation ──────────────────────────────────────────────────────

def build_deltas(pair_teams, n_weeks, n_teams):
    """
    deltas[w][ai] = int8 array shape (n_teams, 3)
    The change in [Main, BP, Gerry] counts for each team if week w uses
    assignment VALID_ASSIGNMENTS[ai].
    """
    deltas = np.zeros((n_weeks, 12, n_teams, 3), dtype=np.int8)
    for w in range(n_weeks):
        for ai, assign in enumerate(VALID_ASSIGNMENTS):
            for ps in range(4):
                vi = VENUE_IDX[assign[ps]]
                for t in pair_teams[w][ps]:
                    deltas[w][ai][t][vi] += 2
    return deltas


# ── Exact search ──────────────────────────────────────────────────────────────

def exact_search(deltas, n_weeks, n_teams):
    """
    Backtracking search over all 12^n_weeks assignments with pruning.
    Returns (best_choices, best_score).
    """
    ideal  = np.array(TARGET, dtype=np.float64)
    counts = np.zeros((n_teams, 3), dtype=np.int16)
    best   = {"score": float("inf"), "choices": [ORIGINAL_AI] * n_weeks}

    def backtrack(week, choices):
        if week == n_weeks:
            sc = float(np.sum((counts.astype(np.float64) - ideal) ** 2))
            if sc < best["score"]:
                best["score"] = sc
                best["choices"] = choices[:]
            return

        for ai in range(12):
            counts[:] += deltas[week][ai]
            choices.append(ai)

            # Pruning: scale partial counts to a full-season estimate
            if best["score"] < float("inf"):
                wt = (week + 1) / n_weeks
                partial = float(
                    np.sum((counts.astype(np.float64) / wt - ideal) ** 2)
                ) * wt * wt
                if partial >= best["score"]:
                    counts[:] -= deltas[week][ai]
                    choices.pop()
                    continue

            backtrack(week + 1, choices)
            counts[:] -= deltas[week][ai]
            choices.pop()

    backtrack(0, [])
    return best["choices"], best["score"]


# ── Team counts helper ────────────────────────────────────────────────────────

def compute_counts(deltas, choices, n_weeks, n_teams):
    counts = np.zeros((n_teams, 3), dtype=np.int16)
    for w in range(n_weeks):
        counts += deltas[w][choices[w]]
    return counts


def in_range(m, b, g):
    return 6 <= m <= 8 and 2 <= b <= 4 and 2 <= g <= 4


# ── Output formatting ─────────────────────────────────────────────────────────

def format_counts_table(counts, n_teams):
    lines = []
    header = f"  {'Team':>4}   {'Main':>5}  {'BP':>5}  {'Gerry':>5}  {'Total':>5}  Status"
    sep    = "  " + "-" * (len(header) - 2)
    lines.append(header)
    lines.append(sep)
    ok = 0
    for t in range(n_teams):
        m, b, g = int(counts[t, 0]), int(counts[t, 1]), int(counts[t, 2])
        status = "✓" if in_range(m, b, g) else "✗"
        if status == "✓":
            ok += 1
        issues = []
        if not (6 <= m <= 8): issues.append(f"Main={m}")
        if not (2 <= b <= 4): issues.append(f"BP={b}")
        if not (2 <= g <= 4): issues.append(f"Gerry={g}")
        note = f"  ({', '.join(issues)})" if issues else ""
        lines.append(
            f"  T{t+1:>2}   {m:>5}  {b:>5}  {g:>5}  {m+b+g:>5}  {status}{note}"
        )
    lines.append(sep)
    lines.append(f"  Teams within target range:  {ok}/{n_teams}")
    return lines, ok


def format_schedule(week_rows, choices, n_weeks):
    """
    Build per-week schedule listing matches grouped by new venue.
    """
    lines  = []
    venues = [VENUE_MAIN, VENUE_BP, VENUE_GERRY]
    for w in range(n_weeks):
        assign = VALID_ASSIGNMENTS[choices[w]]
        lines.append(f"Week {w + 1}")

        grouped = {v: [] for v in venues}
        for ps in range(4):
            venue = assign[ps]
            for row in week_rows[w]:
                for col in (ps * 2, ps * 2 + 1):
                    pair = row[col] if col < len(row) else None
                    if pair:
                        grouped[venue].append(f"T{pair[0]+1:2d} vs T{pair[1]+1:2d}")

        for venue in venues:
            matches = "   ".join(grouped[venue])
            lines.append(f"  {venue:<5}: {matches}")
        lines.append("")
    return lines


def write_output(out_path, week_rows, choices, counts, n_weeks, n_teams,
                 source_name, score_val):
    with open(out_path, "w") as fh:
        fh.write(f"Venue Schedule — derived from: {source_name}\n")
        fh.write("=" * 60 + "\n\n")

        fh.write("TARGETS  Main 6–8   BP 2–4   Gerry 2–4   (per team, 14 games)\n")
        fh.write(f"Optimality score (sum of squared deviations): {score_val:.1f}\n\n")

        fh.write("TEAM VENUE COUNTS\n")
        table_lines, ok = format_counts_table(counts, n_teams)
        fh.write("\n".join(table_lines) + "\n\n")

        fh.write("PAIR-SLOT ASSIGNMENTS PER WEEK\n")
        fh.write("  (* = changed from original assignment)\n\n")
        header = f"  {'Wk':>2}      {'PS0 MainAB':>12}  {'PS1 MainCD':>12}"
        header += f"  {'PS2 BP':>12}  {'PS3 Gerry':>12}"
        fh.write(header + "\n")
        fh.write("  " + "-" * (len(header) - 2) + "\n")
        for w in range(n_weeks):
            assign  = VALID_ASSIGNMENTS[choices[w]]
            changed = assign != ORIGINAL_ASSIGNMENT
            flag    = " *" if changed else "  "
            cols    = [f"{assign[ps]:>12}" for ps in range(4)]
            fh.write(f"  W{w+1}{flag}  {'  '.join(cols)}\n")
        fh.write("\n")

        fh.write("WEEKLY SCHEDULE BY VENUE\n\n")
        for line in format_schedule(week_rows, choices, n_weeks):
            fh.write(line + "\n")


# ── Per-file processing ───────────────────────────────────────────────────────

def process_file(path):
    try:
        pair_teams, week_rows, n_weeks = parse_schedule(path)
    except Exception as e:
        print(f"  [SKIP] Could not parse {os.path.basename(path)}: {e}")
        return None

    if not pair_teams:
        print(f"  [SKIP] No schedule found in {os.path.basename(path)}")
        return None

    n_teams = count_teams(pair_teams, n_weeks)
    deltas  = build_deltas(pair_teams, n_weeks, n_teams)

    # Show original counts for reference
    orig_counts = compute_counts(deltas, [ORIGINAL_AI] * n_weeks, n_weeks, n_teams)
    orig_score  = float(np.sum((orig_counts.astype(float) - np.array(TARGET)) ** 2))
    _, orig_ok  = format_counts_table(orig_counts, n_teams)
    print(f"  Teams: {n_teams}   Weeks: {n_weeks}")
    print(f"  Original:  score={orig_score:.0f}   {orig_ok}/{n_teams} teams in range")

    # Exact search
    import time
    t0 = time.time()
    choices, best_score = exact_search(deltas, n_weeks, n_teams)
    elapsed = time.time() - t0

    best_counts = compute_counts(deltas, choices, n_weeks, n_teams)
    table_lines, ok = format_counts_table(best_counts, n_teams)

    print(f"  Optimal:   score={best_score:.0f}   {ok}/{n_teams} teams in range"
          f"   ({elapsed:.2f}s)")
    print()
    for line in table_lines:
        print("    " + line)
    print()

    # Show which weeks changed and what swapped
    changed_weeks = [w for w in range(n_weeks) if choices[w] != ORIGINAL_AI]
    if changed_weeks:
        print(f"  Venue reassignments:")
        for w in changed_weeks:
            orig_a = ORIGINAL_ASSIGNMENT
            new_a  = VALID_ASSIGNMENTS[choices[w]]
            diffs  = [(PAIR_NAMES[ps], orig_a[ps], new_a[ps])
                      for ps in range(4) if orig_a[ps] != new_a[ps]]
            swaps  = "   ".join(f"{nm}: {o} → {n}" for nm, o, n in diffs)
            print(f"    Week {w+1}:  {swaps}")
    else:
        print("  No changes from original — original is already optimal.")
    print()

    # Write output file
    base     = os.path.splitext(os.path.basename(path))[0]
    out_path = os.path.join(os.path.dirname(path),
                            f"venue_schedule_{base}.txt")
    write_output(out_path, week_rows, choices, best_counts,
                 n_weeks, n_teams, os.path.basename(path), best_score)
    print(f"  → Saved: {out_path}\n")
    return ok, n_teams


# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="Optimally assign venues to a Java volleyball scheduler output.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__)
    parser.add_argument("directory",
        help="Directory containing Java scheduler output .txt files")
    parser.add_argument("--pattern", default="matches_*.txt",
        help="Glob pattern for input files (default: matches_*.txt)")
    args = parser.parse_args()

    if not os.path.isdir(args.directory):
        print(f"Error: {args.directory!r} is not a directory.", file=sys.stderr)
        sys.exit(1)

    files = sorted(glob.glob(os.path.join(args.directory, args.pattern)))
    files = [f for f in files
             if not os.path.basename(f).startswith("venue_schedule_")]

    if not files:
        print(f"No files matching {args.pattern!r} in {args.directory!r}",
              file=sys.stderr)
        sys.exit(1)

    print(f"Found {len(files)} file(s).\n")
    print(f"Target per team:  Main 6–8   BP 2–4   Gerry 2–4\n")

    summary = []
    for path in files:
        print("─" * 55)
        print(f"File: {os.path.basename(path)}")
        result = process_file(path)
        if result:
            ok, n = result
            summary.append((os.path.basename(path), ok, n))

    if len(summary) > 1:
        print("═" * 55)
        print("SUMMARY")
        print("═" * 55)
        for fname, ok, n in summary:
            bar = "█" * ok + "░" * (n - ok)
            print(f"  {fname:<40} {ok:2d}/{n}  {bar}")


if __name__ == "__main__":
    main()