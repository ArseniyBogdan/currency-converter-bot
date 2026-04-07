#!/bin/sh
# Простая генерация inventory.yml из Terraform output.
# Автоматически создаёт инвентарь из вывода Terraform.
# Если ВМ пересоздастся и изменится IP — инвентарь обновится сам

set -e

# Пути
SCRIPT_DIR="$(dirname "$0")"
OUTPUT_FILE="${SCRIPT_DIR}/terraform_outputs.json"
INV_FILE="${SCRIPT_DIR}/inventory.yml"

# Проверяем, что файл существует
[ -f "$OUTPUT_FILE" ] || { echo " Not found: $OUTPUT_FILE"; exit 1; }

# Извлекаем IP (простой и надёжный способ)
VM_IP=$(grep -A5 '"vm_public_ip"' "$OUTPUT_FILE" | grep '"value"' | sed 's/.*: *"\([^"]*\)".*/\1/')

# Генерируем inventory
cat > "$INV_FILE" << EOF
all:
  children:
    bot_vms:
      hosts:
        currency_converter_bot:
          ansible_host: ${VM_IP}
          vars:
            ansible_user: ubuntu
            ansible_python_interpreter: /usr/bin/python3
EOF

echo " Inventory: ${VM_IP}"