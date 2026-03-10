variable "os_auth_url" {
  description = "OpenStack authentication URL"
  type        = string
  sensitive   = true
}

variable "os_username" {
  description = "OpenStack username"
  type        = string
  sensitive   = true
}

variable "os_password" {
  description = "OpenStack password"
  type        = string
  sensitive   = true
}

variable "os_project_name" {
  description = "OpenStack project name"
  type        = string
  sensitive   = true
}

variable "image_id" {
  description = "Ubuntu image ID"
  type        = string
  default     = "Ubuntu 22.04"
}

variable "flavor_id" {
  description = "VM flavor ID"
  type        = string
  default     = "2vCPU 4GB"
}

variable "key_name" {
  description = "SSH key pair name"
  type        = string
  default     = "arseniy_jenkins_key"
}

variable "existing_subnet_id" {
  description = "Existing subnet ID for VM"
  type        = string
}

variable "stack_name" {
  description = "Name of the stack"
  type        = string
  default     = "currency-converter-bot-infra"
}

variable "docker_image" {
  description = "Docker image to deploy"
  type        = string
  default     = "docker.io/arseniybogdan/currency-converter-bot:latest"
}

variable "ansible_ssh_private_key_file" {
  description = "Path to SSH private key for Ansible"
  type        = string
  sensitive   = true
}