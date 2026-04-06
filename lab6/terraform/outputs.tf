output "vm_public_ip" {
  description = "Public IP address of the VM"
  value       = yandex_compute_instance.bot_vm.network_interface.0.nat_ip_address
}

output "ansible_inventory" {
  description = "Ansible inventory line"
  value       = "[bot_servers]\n${yandex_compute_instance.bot_vm.network_interface.0.nat_ip_address} ansible_user=ubuntu ansible_ssh_private_key_file=~/.ssh/mykey"
}
