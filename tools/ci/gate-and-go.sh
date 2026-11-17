#!/usr/bin/env bash
# Pushes the project to GitHub and runs the build loop, but only after checking that the GitHub token is nowhere in the tree.
#   tools/ci/gate-and-go.sh "commit message" [seconds to wait for the run]
# Needs the token in /tmp/.gh_env (export GH_TOKEN='...'). It checks 24 characters from the middle of the REAL token, not just its prefix.
cd "$(dirname "$0")/../.." || exit 1
. /tmp/.gh_env
M="${GH_TOKEN:40:24}"
[ ${#M} -eq 24 ] || { echo "gate string has the wrong length, abort"; exit 1; }
if grep -rIl -F "$M" . /mnt/user-data/outputs 2>/dev/null; then echo "TOKEN FOUND IN THE TREE OR THE OUTPUTS, ABORT"; exit 1; fi
echo "gate: the token is not in the tree or the outputs"
exec bash tools/ci/cycle.sh "${1:-update}" "${2:-700}"
