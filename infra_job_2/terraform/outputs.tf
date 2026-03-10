output "server_name" {
  description = "Server name"
  value       = openstack_compute_instance_v2.bot_server.name
}

output "server_private_ip" {
  description = "Private IP address"
  value       = openstack_compute_instance_v2.bot_server.network[0].fixed_ip_v4
}

output "server_id" {
  description = "Server ID"
  value       = openstack_compute_instance_v2.bot_server.id
}

output "volume_id" {
  description = "MongoDB volume ID"
  value       = openstack_blockstorage_volume_v3.mongo_volume.id
}

output "security_group_id" {
  description = "Security group ID"
  value       = openstack_networking_secgroup_v2.bot_sg.id
}

output "ansible_inventory" {
  description = "Ansible inventory string"
  value       = "[bot]\n${openstack_compute_instance_v2.bot_server.name} ansible_host=${openstack_compute_instance_v2.bot_server.network[0].fixed_ip_v4} ansible_user=ubuntu ansible_ssh_private_key_file=${var.ansible_ssh_private_key_file}"
  sensitive   = true
}