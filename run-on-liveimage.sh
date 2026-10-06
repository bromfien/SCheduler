#!/usr/bin/env bash
set -euo pipefail

REPO_URL="https://github.com/bromfien/SCheduler.git"
WORK_DIR="$HOME/scheduler-run"

# ── 1. Dependencies ────────────────────────────────────────────────────────────
echo ">>> Installing dependencies..."
sudo apt-get update -qq
sudo apt-get install -y -qq git maven

# Try to install openjdk-22-jdk from the default repos first; fall back to the
# openjdk-r PPA for older Ubuntu releases that don't carry it.
if apt-cache show openjdk-22-jdk &>/dev/null; then
    echo ">>> openjdk-22-jdk found in default repos, installing..."
    sudo apt-get install -y -qq openjdk-22-jdk
else
    echo ">>> openjdk-22-jdk not in default repos — adding openjdk-r PPA..."
    sudo apt-get install -y -qq software-properties-common
    sudo add-apt-repository -y ppa:openjdk-r/ppa
    sudo apt-get update -qq
    sudo apt-get install -y -qq openjdk-22-jdk
fi

# Make sure the shell picks up the newly installed JDK
sudo update-alternatives --set java "$(update-alternatives --list java | grep java-22)" 2>/dev/null || true
export JAVA_HOME
JAVA_HOME=$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")

# Verify Java version
java -version
mvn -version

# ── 2. Clone repository ────────────────────────────────────────────────────────
echo ">>> Cloning $REPO_URL..."
if [ -d "$WORK_DIR" ]; then
    echo "Directory $WORK_DIR already exists — pulling latest changes instead."
    git -C "$WORK_DIR" pull
else
    git clone "$REPO_URL" "$WORK_DIR"
fi

# ── 3. Compile ─────────────────────────────────────────────────────────────────
echo ">>> Compiling..."
cd "$WORK_DIR/scheduler"
mvn compile -q

# ── 4. Run ─────────────────────────────────────────────────────────────────────
echo ">>> Running com.example.MainMultiThreaded..."
mvn exec:java -Dexec.mainClass="com.example.MainMultiThreaded" \
              -Daggregator="http://bromfien.duckdns.org:8080/push"
