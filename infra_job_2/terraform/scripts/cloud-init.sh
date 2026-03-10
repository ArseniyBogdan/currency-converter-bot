#!/bin/bash
# Cloud-init script for initial VM setup

exec > /var/log/cloud-init-output.log 2>&1

echo "=== Cloud-init started at $(date) ==="

# Update packages
apt-get update

# Install Python for Ansible
apt-get install -y python3 python3-pip

# Install Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sh get-docker.sh
usermod -aG docker ubuntu

# Install Docker Compose
apt-get install -y docker-compose-plugin

# Create app directory
mkdir -p /opt/currency-converter-bot
mkdir -p /opt/currency-converter-bot/vault/scripts
chown -R ubuntu:ubuntu /opt/currency-converter-bot

echo "=== Cloud-init completed at $(date) ==="