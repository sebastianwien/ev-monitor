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

# Remove images not used by any container and older than 7 days
# (also tagged ones, e.g. superseded postgres versions or one-off tools)
echo "Removing unused images older than 7 days..."
docker image prune --all --force --filter until=168h

# Show disk usage after cleanup
echo "Disk usage after cleanup:"
df -h / | grep -v Filesystem
docker system df

echo "[$(date)] Docker cleanup completed."
