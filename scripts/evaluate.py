#!/usr/bin/env python3
"""Measures triage accuracy against eval/sample-complaints.csv using the running app.

Usage:  python scripts/evaluate.py [--runs 3] [--url http://localhost:8080]
Needs only the Python standard library. Start the app first (./mvnw spring-boot:run).
"""
import argparse, base64, csv, json, os, urllib.request
from collections import Counter


def credentials():
    """Login for the app: APP_USERNAME / APP_PASSWORD from the environment, else from a local .env file."""
    user, password = os.environ.get("APP_USERNAME"), os.environ.get("APP_PASSWORD")
    if not (user and password) and os.path.exists(".env"):
        for line in open(".env", encoding="utf-8"):
            key, _, value = line.strip().partition("=")
            if key == "APP_USERNAME" and not user:
                user = value
            elif key == "APP_PASSWORD" and not password:
                password = value
    if not (user and password):
        raise SystemExit("Set APP_USERNAME and APP_PASSWORD (environment or .env) to sign in to the app.")
    return user, password


AUTH = "Basic " + base64.b64encode(":".join(credentials()).encode("utf-8")).decode("ascii")

ap = argparse.ArgumentParser()
ap.add_argument("--runs", type=int, default=3, help="times each complaint is sent (Jev answers can vary)")
ap.add_argument("--url", default="http://localhost:8080")
ap.add_argument("--file", default="eval/sample-complaints.csv")
args = ap.parse_args()

rows = list(csv.DictReader(open(args.file, encoding="utf-8")))
total = hits = level_hits = 0
print(f"{'id':>3}  {'hits':<5} {'expected':<44} most common answer (confidence)")
for row in rows:
    answers, confs = [], []
    for _ in range(args.runs):
        req = urllib.request.Request(args.url + "/api/v1/complaints/triage",
                                     json.dumps({"feedback": row["complaint"]}).encode(),
                                     {"Content-Type": "application/json", "Authorization": AUTH})
        d = json.load(urllib.request.urlopen(req, timeout=60))
        answers.append((d["routedTo"], str(d["routedLevel"])))
        confs.append(d["confidence"])
    ok = sum(a[0] == row["expected_role"] for a in answers)
    lv = sum(a[1] == row["expected_level"] for a in answers)
    total += len(answers); hits += ok; level_hits += lv
    top = Counter(a[0] for a in answers).most_common(1)[0][0]
    print(f"{row['id']:>3}  {ok}/{args.runs}   {row['expected_role']:<44} {top} ({sum(confs)/len(confs):.0f}%)")
print(f"\nRole accuracy : {hits}/{total} = {100*hits/total:.1f}%")
print(f"Level accuracy: {level_hits}/{total} = {100*level_hits/total:.1f}%")
