resource "yandex_vpc_subnet" "default" {
  name           = "shklyarova-bot-vm-subnet-lab6"
  zone           = "ru-central1-d"
  network_id     = "enpq1korg6qpq5kr687c"  # ← Существующая сеть default
  v4_cidr_blocks = ["192.168.101.0/24"]     # ← Уникальная подсеть для вас
}


# Виртуальная машина
resource "yandex_compute_instance" "bot_vm" {
  name        = var.vm_name
  hostname    = "shklyarova-bot-vm"
  platform_id = "standard-v4a"
  zone        = "ru-central1-d"
  
  resources {
    cores  = 2 # ядра
    memory = 2 # гб RAM
  }

  # настройки загрузочного диска
  boot_disk {
    initialize_params {
      image_id = "fd83c1pf8uf99qhppnvb"
      name     = "root-disk"
      type     = "network-hdd"
      size     = 20 # гб
    }
  }

  # сетевой интерфейс
  network_interface {
    subnet_id = yandex_vpc_subnet.default.id
    nat       = true # выдаем публичный ip
  }

  # берем ключ из переменной
  metadata = {
    ssh-keys = "ubuntu:${var.ssh_public_key}"
  }
}
