terraform {
  required_version = ">= 1.0.0"
  
  required_providers {
    openstack = {
      source  = "terraform-provider-openstack/openstack"
      version = "~> 1.50.0"
    }
  }

  provider_installation {
    network_mirror {
      url = "https://registry.opentofu.org/"
    }
    direct {
      include = ["*/*"]
    }
  }
}

provider "openstack" {
  auth_url    = var.os_auth_url
  username    = var.os_username
  password    = var.os_password
  tenant_name = var.os_project_name
  domain_name = "Default"
}