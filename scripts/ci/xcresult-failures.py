#!/usr/bin/env python3
"""Print failed XCTest cases and their assertion messages from
`xcrun xcresulttool get test-results tests` JSON (read from stdin)."""
import json
import sys


def walk(node):
    if isinstance(node, dict):
        node_type = node.get("nodeType")
        if node_type == "Failure Message" or (node.get("result") == "Failed" and node_type == "Test Case"):
            print(f'{node_type}: {node.get("name")}')
        for value in node.values():
            walk(value)
    elif isinstance(node, list):
        for value in node:
            walk(value)


try:
    walk(json.load(sys.stdin))
except (json.JSONDecodeError, ValueError):
    print("no parsable xcresult JSON")
