#!/bin/bash

# Path to the monitor script
MONITOR_SCRIPT="/Users/laszlo/dev/code/ib/scripts/cron-kafka-status.sh"

# Create a temporary file for the crontab
TEMP_CRONTAB=$(mktemp)

# Export the current crontab
crontab -l > $TEMP_CRONTAB 2>/dev/null || echo "# New crontab" > $TEMP_CRONTAB

# Check if the monitor job is already in the crontab
if ! grep -q "$MONITOR_SCRIPT" $TEMP_CRONTAB; then
    # Add the monitor job to run daily at 8 AM
    echo "# Kafka status check - runs daily at 8 AM" >> $TEMP_CRONTAB
    echo "0 8 * * * $MONITOR_SCRIPT" >> $TEMP_CRONTAB
    
    # Install the new crontab
    crontab $TEMP_CRONTAB
    echo "✅ Kafka status check cron job added successfully"
else
    echo "Kafka status check cron job already exists"
fi

# Clean up the temporary file
rm $TEMP_CRONTAB

echo "Cron setup complete. The status check will run daily at 8 AM."
echo "You can view the cron jobs with: crontab -l"
