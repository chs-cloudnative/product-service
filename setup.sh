#!/bin/bash
# ===================================================================
# Application Deployment Automation Script
# ===================================================================
# 用途: 在 Ubuntu 24.04 LTS 系統上自動設定應用環境
# 執行方式: sudo bash setup.sh
set -e

# ===================================================================
# Color Output Functions
# ===================================================================
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

echo_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

echo_error() {
    echo -e "${RED}✗ $1${NC}"
}

# ===================================================================
# Root Privilege Check
# ===================================================================
if [ "$EUID" -ne 0 ]; then
    echo_error "This script must be run with sudo"
    exit 1
fi

echo "====================================================================="
echo "Starting Application Environment Setup"
echo "====================================================================="


# ===================================================================
# Step 1: Update Package Lists
# ===================================================================
echo "Step 1: Updating package lists..."
apt update -y
echo_success "Package lists updated"


# ===================================================================
# Step 2: Upgrade System Packages
# ===================================================================
echo "Step 2: Upgrading system packages..."
DEBIAN_FRONTEND=noninteractive apt upgrade -y
echo_success "System packages upgraded"


# ===================================================================
# Step 3: Install Required Software
# ===================================================================
echo "Step 3: Installing Java, Maven, Unzip and PostgreSQL..."
apt install -y openjdk-17-jdk maven unzip postgresql postgresql-contrib

# Verify installations
java -version
mvn -version

echo_success "Java, Maven, Unzip and PostgreSQL installed"

# Start and enable PostgreSQL
systemctl start postgresql
systemctl enable postgresql
echo_success "PostgreSQL service started and enabled"


# ===================================================================
# Step 4: Create Database and User
# ===================================================================
echo "Step 4: Creating application database..."
DB_NAME="csye6225_db"
DB_USER="csye6225"
DB_PASSWORD="csye6225_password"

# Create database (skip if exists)
sudo -u postgres psql -c "CREATE DATABASE ${DB_NAME};" 2>/dev/null || echo "Database already exists, skipping"

# Create user (skip if exists)
sudo -u postgres psql -c "CREATE USER ${DB_USER} WITH PASSWORD '${DB_PASSWORD}';" 2>/dev/null || echo "User already exists, skipping"

# Grant privileges
sudo -u postgres psql -c "GRANT ALL PRIVILEGES ON DATABASE ${DB_NAME} TO ${DB_USER};"
sudo -u postgres psql -c "ALTER DATABASE ${DB_NAME} OWNER TO ${DB_USER};"

echo_success "Database ${DB_NAME} configured"
echo_success "Database user ${DB_USER} configured"


# ===================================================================
# Step 5: Create Application Group
# ===================================================================
echo "Step 5: Creating application group..."
GROUP_NAME="csye6225"

if getent group ${GROUP_NAME} > /dev/null 2>&1; then
    echo "Group ${GROUP_NAME} already exists, skipping"
else
    groupadd ${GROUP_NAME}
    echo_success "Group ${GROUP_NAME} created"
fi


# ===================================================================
# Step 6: Create Application User
# ===================================================================
echo "Step 6: Creating application user..."
USER_NAME="csye6225"

if id "${USER_NAME}" > /dev/null 2>&1; then
    echo "User ${USER_NAME} already exists, skipping"
else
    # Create system user with no login shell
    useradd -r -g ${GROUP_NAME} -s /usr/sbin/nologin -m ${USER_NAME}
    echo_success "User ${USER_NAME} created"
fi


# ===================================================================
# Step 7: Deploy and Build Application
# ===================================================================
echo "Step 7: Deploying application files..."
APP_DIR="/opt/csye6225"
mkdir -p ${APP_DIR}

# Stop existing service if running
SERVICE_NAME="${USER_NAME}.service"
if systemctl is-active --quiet ${SERVICE_NAME} 2>/dev/null; then
    systemctl stop ${SERVICE_NAME}
    echo_success "Existing service stopped"
fi

# Check if webapp.zip exists
if [ ! -f "/tmp/webapp.zip" ]; then
    echo_error "ERROR: /tmp/webapp.zip not found!"
    echo "Please upload: scp webapp.zip ubuntu@server-ip:/tmp/webapp.zip"
    exit 1
fi

# Extract application files
unzip -oq /tmp/webapp.zip -d ${APP_DIR}/
echo_success "Source code extracted"

# Copy environment file if exists
if [ -f "/tmp/.env" ]; then
    cp /tmp/.env ${APP_DIR}/
    echo_success "Environment file copied"
fi

# Set ownership
chown -R ${USER_NAME}:${GROUP_NAME} ${APP_DIR}

# Build application
cd ${APP_DIR}
if [ -f ./mvnw ]; then
    chmod +x mvnw
    su -s /bin/bash -c "./mvnw clean package -DskipTests" ${USER_NAME}
    echo_success "Build completed with Maven Wrapper"
else
    su -s /bin/bash -c "mvn clean package -DskipTests" ${USER_NAME}
    echo_success "Build completed with system Maven"
fi

# Verify build output
JAR_FILE=$(find ${APP_DIR}/target -name "*.jar" -type f ! -name "*original*" 2>/dev/null | head -1)
if [ -z "$JAR_FILE" ]; then
    echo_error "ERROR: Build failed - no JAR file found in target/"
    exit 1
fi
echo_success "Build successful: $(basename $JAR_FILE)"


# ===================================================================
# Step 8: Set File Permissions
# ===================================================================
echo "Step 8: Setting file permissions..."
chown -R ${USER_NAME}:${GROUP_NAME} ${APP_DIR}
chmod -R 755 ${APP_DIR}
echo_success "File permissions set (Owner: ${USER_NAME}:${GROUP_NAME}, Mode: 755)"


# ===================================================================
# Step 9: Install CloudWatch Agent
# ===================================================================
echo "Step 9: Installing CloudWatch Agent..."

# Download CloudWatch Agent
wget -q https://s3.amazonaws.com/amazoncloudwatch-agent/ubuntu/amd64/latest/amazon-cloudwatch-agent.deb -O /tmp/amazon-cloudwatch-agent.deb

# Install CloudWatch Agent
DEBIAN_FRONTEND=noninteractive dpkg -i -E /tmp/amazon-cloudwatch-agent.deb

# Clean up installer
rm -f /tmp/amazon-cloudwatch-agent.deb

# Create configuration directory
mkdir -p /opt/aws/amazon-cloudwatch-agent/etc/

# Copy configuration file (uploaded by Packer)
if [ -f "/tmp/cloudwatch-config.json" ]; then
    cp /tmp/cloudwatch-config.json /opt/aws/amazon-cloudwatch-agent/etc/
    echo_success "CloudWatch config file copied"
fi

# Create logs directory
mkdir -p /opt/csye6225/logs
chown -R ${USER_NAME}:${GROUP_NAME} /opt/csye6225/logs
chmod -R 755 /opt/csye6225/logs

echo_success "CloudWatch Agent installed"


# ===================================================================
# Setup Complete
# ===================================================================
echo "====================================================================="
echo_success "Environment setup completed!"
echo "====================================================================="
echo "Database Information:"
echo "  - Database Name: ${DB_NAME}"
echo "  - Database User: ${DB_USER}"
echo "  - Database Password: ${DB_PASSWORD}"
echo ""
echo "Application Information:"
echo "  - Application Directory: ${APP_DIR}"
echo "  - Application User: ${USER_NAME}"
echo "  - Application Group: ${GROUP_NAME}"
echo "  - JAR File: $(basename $JAR_FILE)"
echo "====================================================================="
