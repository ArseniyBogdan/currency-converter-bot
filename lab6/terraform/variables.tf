variable "cloud_id" {
  type        = string
  description = "Yandex Cloud ID"
  default     = "b1gtgpmqgvqfitjnck7"
}

variable "folder_id" {
  type        = string
  description = "Yandex Folder ID"
  default     = "b1gl79ck193vcibt8q9l"
}

variable "zone" {
  type        = string
  description = "Default zone"
  default     = "ru-central1-d"
}

variable "vm_name" {
  type    = string
  default = "shklyarova-bot-vm"
}

variable "ssh_public_key" {
  type        = string
  description = "SSH public key for VM access"
}
