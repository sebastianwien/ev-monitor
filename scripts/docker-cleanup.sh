#!/bin/bash
#
# Docker Cleanup Script
# Runs weekly via cron to prevent disk space issues from buildx cache buildup
#

set -e

echo "[$(date)] Starting Docker cleanup..."

# Remove buildx cache older than 7 days
echo "Pruning buildx cache (older than 7 days)..."
docker buildx prune --force --filter until=168h

# Remove dangling images (not tagged, not used by any container)
echo "Removing dangling images..."
docker image prune --force

# Show disk usage after cleanup
echo "Disk usage after cleanup:"
df -h / | grep -v Filesystem
docker system df

echo "[$(date)] Docker cleanup completed."
