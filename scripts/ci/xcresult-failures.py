#!/usr/bin/env python3
"""Print failed XCTest cases and their assertion messages from
`xcrun xcresulttool get test-results tests` JSON (read from stdin)."""
import json
import sys


def walk(node):
    if isinstance(node, dict):
        if node.get("result") == "Failed" and node.get("nodeType") in ("Test Case", "Failure Message"):
            print(f'{node.get("nodeType")}: {node.get("name")}')
        for value in node.values():
            walk(value)
    elif isinstance(node, list):
        for value in node:
            walk(value)


try:
    walk(json.load(sys.stdin))
except (json.JSONDecodeError, ValueError):
    print("no parsable xcresult JSON")
