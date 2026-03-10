# =============================================================================
# Security Group
# =============================================================================
resource "openstack_networking_secgroup_v2" "bot_sg" {
  name        = "${var.stack_name}-sg"
  description = "Security group for Currency Converter Bot"
}

resource "openstack_networking_secgroup_rule_v2" "ssh" {
  direction         = "ingress"
  ethertype         = "IPv4"
  protocol          = "tcp"
  port_range_min    = 22
  port_range_max    = 22
  remote_ip_prefix  = "0.0.0.0/0"
  security_group_id = openstack_networking_secgroup_v2.bot_sg.id
}

resource "openstack_networking_secgroup_rule_v2" "app_port" {
  direction         = "ingress"
  ethertype         = "IPv4"
  protocol          = "tcp"
  port_range_min    = 8081
  port_range_max    = 8081
  remote_ip_prefix  = "0.0.0.0/0"
  security_group_id = openstack_networking_secgroup_v2.bot_sg.id
}

resource "openstack_networking_secgroup_rule_v2" "vault_port" {
  direction         = "ingress"
  ethertype         = "IPv4"
  protocol          = "tcp"
  port_range_min    = 8200
  port_range_max    = 8200
  remote_ip_prefix  = "0.0.0.0/0"
  security_group_id = openstack_networking_secgroup_v2.bot_sg.id
}

resource "openstack_networking_secgroup_rule_v2" "rabbitmq_amqp" {
  direction         = "ingress"
  ethertype         = "IPv4"
  protocol          = "tcp"
  port_range_min    = 5672
  port_range_max    = 5672
  remote_ip_prefix  = "0.0.0.0/0"
  security_group_id = openstack_networking_secgroup_v2.bot_sg.id
}

resource "openstack_networking_secgroup_rule_v2" "rabbitmq_mgmt" {
  direction         = "ingress"
  ethertype         = "IPv4"
  protocol          = "tcp"
  port_range_min    = 15672
  port_range_max    = 15672
  remote_ip_prefix  = "0.0.0.0/0"
  security_group_id = openstack_networking_secgroup_v2.bot_sg.id
}

resource "openstack_networking_secgroup_rule_v2" "mongodb" {
  direction         = "ingress"
  ethertype         = "IPv4"
  protocol          = "tcp"
  port_range_min    = 27017
  port_range_max    = 27017
  remote_ip_prefix  = "0.0.0.0/0"
  security_group_id = openstack_networking_secgroup_v2.bot_sg.id
}

# =============================================================================
# VM Instance
# =============================================================================
resource "openstack_compute_instance_v2" "bot_server" {
  name            = "${var.stack_name}-vm"
  image_id        = var.image_id
  flavor_id       = var.flavor_id
  key_pair        = var.key_name
  security_groups = [openstack_networking_secgroup_v2.bot_sg.name]
  
  network {
    uuid = var.existing_subnet_id
  }
  
  user_data = templatefile("${path.module}/scripts/cloud-init.sh", {
    ansible_setup_playbook = "${path.module}/../ansible/playbooks/provision.yml"
  })
  
  tags = {
    project     = "currency-converter-bot"
    environment = "production"
  }
}

# =============================================================================
# Volume for MongoDB Data (Optional)
# =============================================================================
resource "openstack_blockstorage_volume_v3" "mongo_volume" {
  name = "${var.stack_name}-mongo-data"
  size = 20
  # image_id = "ubuntu-2204" # Optional: pre-format
}

resource "openstack_compute_volume_attach_v2" "mongo_attach" {
  instance_id = openstack_compute_instance_v2.bot_server.id
  volume_id   = openstack_blockstorage_volume_v3.mongo_volume.id
}