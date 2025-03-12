#!/bin/bash

echo "Checking which process is using port 8080..."

# Find the process using port 8080
PID=$(lsof -i :8080 -t)

if [ -z "$PID" ]; then
    echo "No process is currently using port 8080."
    exit 0
fi

# Get process details
PROCESS_INFO=$(ps -p $PID -o pid,user,command | tail -n +2)
PROCESS_COUNT=$(echo "$PID" | wc -l | xargs)

echo "Found $PROCESS_COUNT process(es) using port 8080:"
echo "-------------------------------------------"
ps -p $PID -o pid,user,%cpu,%mem,start,command
echo "-------------------------------------------"

# Ask for confirmation before killing
read -p "Do you want to kill the process(es)? (y/n): " CONFIRM

if [[ "$CONFIRM" =~ ^[Yy]$ ]]; then
    echo "Killing process(es) with PID: $PID"
    kill $PID
    
    # Check if process was killed
    sleep 1
    if ps -p $PID > /dev/null 2>&1; then
        echo "Process(es) still running. Attempting force kill..."
        kill -9 $PID
        
        sleep 1
        if ps -p $PID > /dev/null 2>&1; then
            echo "Failed to kill process(es). You may need to run this script with sudo."
        else
            echo "Process(es) successfully force killed."
        fi
    else
        echo "Process(es) successfully killed."
    fi
    
    # Verify port is free
    if lsof -i :8080 > /dev/null 2>&1; then
        echo "Warning: Port 8080 is still in use. You may need to wait a moment or run this script with sudo."
    else
        echo "Port 8080 is now free."
    fi
else
    echo "Operation cancelled. No processes were killed."
fi 