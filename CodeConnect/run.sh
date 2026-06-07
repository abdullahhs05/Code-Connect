#!/bin/bash
# -------------------------------------------------------------
# CodeConnect run script for Linux and macOS.
# This script clean compiles the Maven project and launches 
# the JavaFX application.
# -------------------------------------------------------------

# Ensure the script stops on errors
set -e

# Change directory to the script's directory
cd "$(dirname "$0")"

echo "--------------------------------------------------------"
echo "Compiling and Launching CodeConnect..."
echo "--------------------------------------------------------"

# Run Maven clean, compile and run the JavaFX main class
mvn clean compile javafx:run
