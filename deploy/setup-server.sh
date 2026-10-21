#!/usr/bin/env bash
# One-time setup of a fresh Ubuntu 24.04 server, run as root over SSH with your key:
#   ssh root@<ip> 'bash -s' < deploy/setup-server.sh
# Installs Docker, turns on automatic security updates and a firewall (SSH, HTTP, HTTPS only),
# and creates a "deploy" user that owns /opt/whh. Afterwards SSH accepts keys only; root keeps
# key login for admin work (reboots after kernel updates). Check `ssh deploy@<ip>` works before
# closing this session.
set -euo pipefail

if [[ ! -s /root/.ssh/authorized_keys ]]; then
    echo "No /root/.ssh/authorized_keys: add your public key first, or SSH would lock you out." >&2
    exit 1
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get -y upgrade
apt-get -y install docker.io docker-compose-v2 unattended-upgrades ufw
dpkg-reconfigure -f noninteractive unattended-upgrades

# Container logs would otherwise grow until the disk is full.
cat > /etc/docker/daemon.json <<'JSON'
{"log-driver": "json-file", "log-opts": {"max-size": "10m", "max-file": "3"}}
JSON
systemctl enable docker
systemctl restart docker

# Docker publishes Caddy's ports itself (around ufw); everything else stays closed.
ufw default deny incoming
ufw default allow outgoing
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 443/udp
ufw --force enable

if ! id deploy >/dev/null 2>&1; then
    adduser --disabled-password --gecos "" deploy
fi
usermod -aG docker deploy
install -d -m 700 -o deploy -g deploy /home/deploy/.ssh
install -m 600 -o deploy -g deploy /root/.ssh/authorized_keys /home/deploy/.ssh/authorized_keys
install -d -o deploy -g deploy /opt/whh

# Sorts before cloud-init's 50-cloud-init.conf, and sshd keeps the first value it reads.
cat > /etc/ssh/sshd_config.d/10-hardening.conf <<'CONF'
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitRootLogin prohibit-password
CONF
systemctl reload ssh

echo "Done. Next: ssh deploy@<ip>, and follow deploy/README.md."
