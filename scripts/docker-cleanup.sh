#!/bin/bash
#
# Docker Cleanup Script
# Runs nightly at 3 AM via cron to prevent disk space issues from buildx cache buildup
#

set -e

echo "[$(date)] Starting Docker cleanup..."

# Remove buildx cache older than 48 hours (keeps recent builds cached)
echo "Pruning buildx cache (older than 48 hours)..."
docker buildx prune --force --filter until=48h

# Remove dangling images (not tagged, not used by any container)
echo "Removing dangling images..."
docker image prune --force

# Show disk usage after cleanup
echo "Disk usage after cleanup:"
df -h / | grep -v Filesystem
docker system df

echo "[$(date)] Docker cleanup completed."
