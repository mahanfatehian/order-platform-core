#!/usr/bin/env python3
"""Turn JaCoCo's aggregate CSV into a Markdown table for the GitHub Actions job summary."""

import csv
import sys
from collections import defaultdict


def percent(covered, missed):
    total = covered + missed
    return "n/a" if total == 0 else f"{covered / total:.1%}"


def summarise(path):
    # Per module: covered lines, missed lines, covered branches, missed branches.
    totals = defaultdict(lambda: [0, 0, 0, 0])
    with open(path, encoding="utf-8", newline="") as report:
        for row in csv.DictReader(report):
            counts = totals[row["GROUP"].rsplit("/", 1)[-1]]
            counts[0] += int(row["LINE_COVERED"])
            counts[1] += int(row["LINE_MISSED"])
            counts[2] += int(row["BRANCH_COVERED"])
            counts[3] += int(row["BRANCH_MISSED"])

    lines = ["### Test coverage", "", "| Module | Lines | Branches |", "| --- | ---: | ---: |"]
    for module, (lc, lm, bc, bm) in sorted(totals.items()):
        lines.append(f"| {module} | {percent(lc, lm)} | {percent(bc, bm)} |")
    lc, lm, bc, bm = (sum(column) for column in zip(*totals.values())) if totals else (0, 0, 0, 0)
    lines.append(f"| **All modules** | **{percent(lc, lm)}** | **{percent(bc, bm)}** |")
    return "\n".join(lines)


if __name__ == "__main__":
    print(summarise(sys.argv[1]))
