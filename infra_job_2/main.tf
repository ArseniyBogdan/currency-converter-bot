terraform {
  required_providers {
    yandex = {
      source  = "yandex-cloud/yandex"
      version = "~> 0.130"
    }
  }
  required_version = ">= 1.0"

  provider_installation {
    filesystem_mirror {
      path    = "/tmp/terraform-providers"
      include = ["yandex-cloud/yandex"]
    }
    direct {
      exclude = ["yandex-cloud/yandex"]
    }
  }
}

provider "yandex" {
  folder_id = var.folder_id
}

variable "folder_id" {
  type        = string
  description = "Yandex Cloud Folder ID"
  default     = "b1gm94s1sde2ispi5k21"
}

variable "subnet_id" {
  type        = string
  description = "Existing Subnet ID"
  default     = "fl80id702e4irnblcd63"
}

variable "ssh_public_key" {
  type        = string
  description = "Public SSH key for ubuntu user"
}

variable "image_family" {
  type    = string
  default = "ubuntu-2204-lts"
}

data "yandex_vpc_subnet" "main" {
  subnet_id = var.subnet_id
}

data "yandex_compute_image" "ubuntu" {
  family = var.image_family
}

# Security Group (SSH + Bot API)
resource "yandex_vpc_security_group" "arseniy_bot_sg" {
  name       = "bot-sg"
  network_id = data.yandex_vpc_subnet.main.network_id
  folder_id  = var.folder_id

  ingress {
    protocol       = "TCP"
    description    = "SSH Access"
    v4_cidr_blocks = ["0.0.0.0/0"]
    port           = 22
  }

  ingress {
    protocol       = "TCP"
    description    = "Bot API Port"
    v4_cidr_blocks = ["0.0.0.0/0"]
    port           = 8081
  }
}

# MongoDB Volume (2GB)
resource "yandex_compute_disk" "mongo_vol" {
  name     = "mongo-vol"
  size     = 2
  type     = "network-hdd"
  zone     = data.yandex_vpc_subnet.main.zone
}

# RabbitMQ Volume (2GB)
resource "yandex_compute_disk" "rabbit_vol" {
  name     = "rabbit-vol"
  size     = 2
  type     = "network-hdd"
  zone     = data.yandex_vpc_subnet.main.zone
}

# Virtual Machine
resource "yandex_compute_instance" "arseniy_bot_server" {
  name        = "currency-converter-bot"
  platform_id = "standard-v3"
  zone        = data.yandex_vpc_subnet.main.zone

  resources {
    cores         = 2
    memory        = 2
    core_fraction = 20
  }

  boot_disk {
    initialize_params {
      image_id = data.yandex_compute_image.ubuntu.id
      size     = 10
      type     = "network-hdd"
    }
  }

  # Автоматическое подключение дисков
  secondary_disk {
    disk_id = yandex_compute_disk.mongo_vol.id
    mode    = "READ_WRITE"
  }

  secondary_disk {
    disk_id = yandex_compute_disk.rabbit_vol.id
    mode    = "READ_WRITE"
  }

  network_interface {
    subnet_id          = var.subnet_id
    nat                = true 
    security_group_ids = [yandex_vpc_security_group.arseniy_bot_sg.id]
  }

  metadata = {
    ssh-keys = "ubuntu:${var.ssh_public_key}"
  }

  allow_stopping_for_update = true
}

output "server_private_ip" {
  description = "Private IP address (доступ через VPN/Bastion)"
  value       = yandex_compute_instance.arseniy_bot_server.network_interface[0].ip_address
}

output "server_name" {
  value = yandex_compute_instance.arseniy_bot_server.name
}

output "ssh_command" {
  value = "ssh -i ~/.ssh/id_rsa ubuntu@${yandex_compute_instance.arseniy_bot_server.network_interface[0].ip_address}"
}