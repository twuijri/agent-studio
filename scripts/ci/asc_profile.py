#!/usr/bin/env python3
"""Make sure an App Store provisioning profile exists for a bundle id.

Xcode's cloud signing needs an App Store Connect key role that a fresh
account may not have, and it fails with an opaque "Cloud signing permission
error". Talking to the App Store Connect API directly gives a readable error
and lets the build sign manually with a profile we control.

Environment:
  ASC_API_KEY_PATH    path to the AuthKey_<id>.p8 file
  ASC_API_KEY_ID      key id
  ASC_API_ISSUER_ID   issuer id
  BUNDLE_ID           e.g. com.twuijri.corehub
  PROFILE_NAME        name to create when the profile is missing

Prints the profile name and UUID, installs it for Xcode, and writes both to
GITHUB_ENV when running on a runner. No secret is ever printed.
"""

import base64
import json
import os
import plistlib
import subprocess
import sys
import time
import urllib.error
import urllib.request

import jwt

API = "https://api.appstoreconnect.apple.com"
PROFILE_DIR = os.path.expanduser("~/Library/MobileDevice/Provisioning Profiles")


def bearer() -> str:
    now = int(time.time())
    with open(os.environ["ASC_API_KEY_PATH"], "r", encoding="utf-8") as handle:
        private_key = handle.read()
    return jwt.encode(
        {
            "iss": os.environ["ASC_API_ISSUER_ID"],
            "iat": now,
            "exp": now + 900,
            "aud": "appstoreconnect-v1",
        },
        private_key,
        algorithm="ES256",
        headers={"kid": os.environ["ASC_API_KEY_ID"], "typ": "JWT"},
    )


def call(token: str, method: str, path: str, body=None):
    request = urllib.request.Request(API + path, method=method)
    request.add_header("Authorization", "Bearer " + token)
    payload = None
    if body is not None:
        payload = json.dumps(body).encode()
        request.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(request, payload) as response:
            return response.status, json.loads(response.read() or b"{}")
    except urllib.error.HTTPError as error:
        raw = error.read().decode("utf-8", "replace")
        try:
            return error.code, json.loads(raw)
        except ValueError:
            return error.code, {"raw": raw[:800]}


def fail(message: str, status=None, payload=None):
    print("ERROR: " + message)
    if status is not None:
        print("HTTP status: %s" % status)
    if payload:
        for item in payload.get("errors", [])[:5]:
            print("  %s: %s" % (item.get("title"), item.get("detail")))
        if "errors" not in payload:
            print("  %s" % json.dumps(payload)[:500])
    sys.exit(1)


def main() -> None:
    bundle_identifier = os.environ["BUNDLE_ID"]
    wanted_name = os.environ.get("PROFILE_NAME", "Core Hub Mobile App Store")
    token = bearer()

    status, data = call(token, "GET", "/v1/bundleIds?limit=200")
    if status != 200:
        fail("the API key cannot read the bundle identifiers", status, data)
    bundle = next(
        (
            item
            for item in data.get("data", [])
            if item["attributes"].get("identifier") == bundle_identifier
        ),
        None,
    )
    if bundle is None:
        known = ", ".join(
            sorted(item["attributes"].get("identifier", "?") for item in data.get("data", []))
        )
        fail(
            "'%s' is not registered in Certificates, Identifiers & Profiles. Known: %s"
            % (bundle_identifier, known or "none")
        )
    print("bundle id: %s" % bundle_identifier)

    status, data = call(token, "GET", "/v1/certificates?limit=200")
    if status != 200:
        fail("the API key cannot read the signing certificates", status, data)
    distribution = [
        item
        for item in data.get("data", [])
        if item["attributes"].get("certificateType") in ("DISTRIBUTION", "IOS_DISTRIBUTION")
    ]
    if not distribution:
        fail("the team has no Apple Distribution certificate")
    print(
        "distribution certificates: %s"
        % ", ".join(item["attributes"].get("certificateType", "?") for item in distribution)
    )

    status, data = call(token, "GET", "/v1/profiles?limit=200&include=bundleId")
    if status != 200:
        fail("the API key cannot read the provisioning profiles", status, data)
    included = {item["id"]: item for item in data.get("included", [])}
    profile = None
    for item in data.get("data", []):
        attributes = item["attributes"]
        if attributes.get("profileType") != "IOS_APP_STORE":
            continue
        if attributes.get("profileState") != "ACTIVE":
            continue
        related = item.get("relationships", {}).get("bundleId", {}).get("data") or {}
        owner = included.get(related.get("id"), {}).get("attributes", {})
        if owner.get("identifier") == bundle_identifier:
            profile = item
            break

    if profile is None:
        print("no App Store profile for this bundle id yet, creating '%s'" % wanted_name)
        status, data = call(
            token,
            "POST",
            "/v1/profiles",
            {
                "data": {
                    "type": "profiles",
                    "attributes": {"name": wanted_name, "profileType": "IOS_APP_STORE"},
                    "relationships": {
                        "bundleId": {"data": {"type": "bundleIds", "id": bundle["id"]}},
                        "certificates": {
                            "data": [
                                {"type": "certificates", "id": item["id"]}
                                for item in distribution
                            ]
                        },
                    },
                }
            },
        )
        if status not in (200, 201):
            fail("could not create the provisioning profile", status, data)
        profile = data["data"]

    attributes = profile["attributes"]
    content = base64.b64decode(attributes["profileContent"])
    os.makedirs(PROFILE_DIR, exist_ok=True)
    raw_path = os.path.join(PROFILE_DIR, "candidate.mobileprovision")
    with open(raw_path, "wb") as handle:
        handle.write(content)
    decoded = subprocess.run(
        ["security", "cms", "-D", "-i", raw_path],
        check=True,
        capture_output=True,
    ).stdout
    parsed = plistlib.loads(decoded)
    uuid = parsed["UUID"]
    name = parsed["Name"]
    os.replace(raw_path, os.path.join(PROFILE_DIR, uuid + ".mobileprovision"))
    print("profile: %s (%s), expires %s" % (name, uuid, attributes.get("expirationDate")))

    env_file = os.environ.get("GITHUB_ENV")
    if env_file:
        with open(env_file, "a", encoding="utf-8") as handle:
            handle.write("PROVISIONING_PROFILE_NAME=%s\n" % name)
            handle.write("PROVISIONING_PROFILE_UUID=%s\n" % uuid)


if __name__ == "__main__":
    main()
