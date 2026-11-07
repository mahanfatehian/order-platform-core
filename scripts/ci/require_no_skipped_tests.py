#!/usr/bin/env python3
"""Fail when any Surefire report records a skipped test.

CI has Docker, so nothing should skip there. The Testcontainers classes skip themselves when they cannot
reach Docker, which is how a Docker Engine upgrade once switched them off without failing a build.
"""

import glob
import sys
import xml.etree.ElementTree as ElementTree


def skipped_suites(root="."):
    found = []
    for path in sorted(glob.glob(f"{root}/**/target/surefire-reports/TEST-*.xml", recursive=True)):
        suite = ElementTree.parse(path).getroot()
        skipped = int(suite.get("skipped", "0"))
        if skipped:
            found.append((suite.get("name"), skipped))
    return found


if __name__ == "__main__":
    suites = skipped_suites()
    if suites:
        print(f"{sum(count for _, count in suites)} tests were skipped, so CI did not run them:")
        for name, count in suites:
            print(f"  {name}: {count}")
        sys.exit(1)
    print("No test was skipped.")
