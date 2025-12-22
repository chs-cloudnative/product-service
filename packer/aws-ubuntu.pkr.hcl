# =================================================================================
# Packer Configuration for Product Service Web Application AMI
# =================================================================================

# =================================================================================
# Required Plugins
# =================================================================================
packer {
  required_plugins {
    amazon = {
      version = ">= 1.0.0"
      source  = "github.com/hashicorp/amazon"
    }
  }
}

# =================================================================================
# Data Source: Find Latest Ubuntu 24.04 LTS AMI
# =================================================================================
data "amazon-ami" "ubuntu" {
  filters = {
    name                = "ubuntu/images/hvm-ssd-gp3/ubuntu-noble-24.04-amd64-server-*"
    root-device-type    = "ebs"
    virtualization-type = "hvm"
  }
  most_recent = true
  owners      = ["099720109477"] # Canonical Official Account ID
  region      = var.aws_region
}

# =================================================================================
# Source Configuration: EC2 Instance for AMI Creation
# =================================================================================
source "amazon-ebs" "webapp" {
  # AMI Configuration
  ami_name        = "${var.ami_name_prefix}-${formatdate("YYYY-MM-DD-hhmm", timestamp())}"
  ami_description = "AMI for Product Service Web Application"
  ami_users       = [var.demo_account_id]
  ami_regions     = [var.aws_region]

  # Instance Configuration
  instance_type = var.instance_type
  region        = var.aws_region
  source_ami    = data.amazon-ami.ubuntu.id
  ssh_username  = var.ssh_username

  # EBS Volume Configuration
  launch_block_device_mappings {
    device_name           = "/dev/sda1"
    volume_size           = 8
    volume_type           = "gp2"
    delete_on_termination = true
  }

  # Tags
  tags = {
    Name        = "${var.ami_name_prefix}-${formatdate("YYYY-MM-DD-hhmm", timestamp())}"
    Environment = "dev"
    Application = "product-service"
  }
}

# =================================================================================
# Build Configuration: Provisioning Steps
# =================================================================================
build {
  sources = ["source.amazon-ebs.webapp"]

  # =================================================================================
  # Step 1: Update System Packages
  # =================================================================================
  provisioner "shell" {
    inline = [
      "echo 'Waiting for cloud-init to complete...'",
      "sudo cloud-init status --wait",
      "echo 'Updating system packages...'",
      "sudo apt-get update",
      "sudo DEBIAN_FRONTEND=noninteractive apt-get upgrade -y -o Dpkg::Options::='--force-confdef' -o Dpkg::Options::='--force-confold'"
    ]
  }

  # =================================================================================
  # Step 2: Install Java Runtime
  # =================================================================================
  provisioner "shell" {
    inline = [
      "echo 'Installing Java 21...'",
      "sudo apt-get install -y openjdk-21-jdk",
      "echo 'Verifying Java installation...'",
      "java -version"
    ]
  }

  # =================================================================================
  # Step 3: Install AWS CLI v2
  # =================================================================================
  provisioner "shell" {
    inline = [
      "echo 'Installing AWS CLI v2...'",
      "curl 'https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip' -o '/tmp/awscliv2.zip'",
      "sudo apt-get install -y unzip",
      "cd /tmp && unzip awscliv2.zip",
      "sudo ./aws/install",
      "echo 'Verifying AWS CLI installation...'",
      "aws --version",
      "rm -rf /tmp/awscliv2.zip /tmp/aws"
    ]
  }

  # =================================================================================
  # Step 4: Create Application User and Group
  # =================================================================================
  provisioner "shell" {
    inline = [
      "echo 'Creating productservice user and group...'",
      "sudo groupadd productservice",
      "sudo useradd -r -g productservice -s /usr/sbin/nologin productservice",
      "echo 'Creating application directory...'",
      "sudo mkdir -p /opt/productservice",
      "sudo chown productservice:productservice /opt/productservice"
    ]
  }

  # =================================================================================
  # Step 5: Upload Application Files
  # =================================================================================
  provisioner "file" {
    source      = "../target/webapp-0.0.1-SNAPSHOT.jar"
    destination = "/tmp/webapp.jar"
  }

  provisioner "file" {
    source      = "../systemd/productservice.service"
    destination = "/tmp/productservice.service"
  }

  provisioner "file" {
    source      = "../cloudwatch-config.json"
    destination = "/tmp/cloudwatch-config.json"
  }

  # =================================================================================
  # Step 6: Move Files and Set Permissions
  # =================================================================================
  provisioner "shell" {
    inline = [
      "echo 'Moving application files...'",
      "sudo mv /tmp/webapp.jar /opt/productservice/webapp.jar",
      "sudo chown productservice:productservice /opt/productservice/webapp.jar",
      "sudo chmod 500 /opt/productservice/webapp.jar",
      "echo 'Setting up systemd service...'",
      "sudo mv /tmp/productservice.service /etc/systemd/system/productservice.service",
      "sudo chmod 644 /etc/systemd/system/productservice.service",
      "echo 'Moving CloudWatch config...'",
      "sudo mkdir -p /opt/aws/amazon-cloudwatch-agent/etc/",
      "sudo mv /tmp/cloudwatch-config.json /opt/aws/amazon-cloudwatch-agent/etc/",
      "sudo chmod 644 /opt/aws/amazon-cloudwatch-agent/etc/cloudwatch-config.json"
    ]
  }

  # =================================================================================
  # Step 7: Install CloudWatch Agent
  # =================================================================================
  provisioner "shell" {
    inline = [
      "echo 'Installing CloudWatch Agent...'",
      "wget -q https://s3.amazonaws.com/amazoncloudwatch-agent/ubuntu/amd64/latest/amazon-cloudwatch-agent.deb -O /tmp/amazon-cloudwatch-agent.deb",
      "sudo DEBIAN_FRONTEND=noninteractive dpkg -i -E /tmp/amazon-cloudwatch-agent.deb",
      "rm -f /tmp/amazon-cloudwatch-agent.deb",
      "echo 'CloudWatch Agent installed successfully'"
    ]
  }

  # =================================================================================
  # Step 8: Enable Systemd Service
  # =================================================================================
  provisioner "shell" {
    inline = [
      "echo 'Enabling systemd service...'",
      "sudo systemctl daemon-reload",
      "sudo systemctl enable productservice.service",
      "echo 'Service will be started by user-data script on EC2 launch'"
    ]
  }

  # =================================================================================
  # Step 9: Cleanup
  # =================================================================================
  provisioner "shell" {
    inline = [
      "echo 'Cleaning up...'",
      "sudo apt-get clean",
      "sudo rm -rf /var/lib/apt/lists/*"
    ]
  }
}
