#!/bin/bash
# Генерирует inventory.yml из Terraform output

set -e

TF_DIR="../terraform"
INV_FILE="inventory.yml"
KEY_FILE="${SSH_KEY_FILE:-~/.ssh/mykey}"

# Получаем публичный IP из Terraform
VM_IP=$(cd "$TF_DIR" && terraform output -raw vm_public_ip 2>/dev/null)

if [ -z "$VM_IP" ]; then
    echo "❌ Не удалось получить IP из Terraform"
    echo "💡 Убедитесь, что выполнили 'terraform apply' в папке $TF_DIR"
    exit 1
fi

# Создаём inventory в формате YAML
 Создаём inventory БЕЗ указания ключа (sshagent сам его подставит)
cat > "$INV_FILE" << EOF
all:
  children:
    bot_vms:
      hosts:
        currency_converter_bot:
          ansible_host: $VM_IP
          vars:
            ansible_user: ubuntu
            ansible_python_interpreter: /usr/bin/python3
            # ansible_ssh_private_key_file не нужен при использовании sshagent!
EOF

echo "✅ Inventory создан: $INV_FILE"
echo "📍 VM IP: $VM_IP"
