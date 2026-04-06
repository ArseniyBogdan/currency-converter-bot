#!/bin/sh
# Простой скрипт для генерации inventory.yml
# Совместим с /bin/sh (не требует bash)

set -e

# Пути: скрипт лежит в ansible/, проект — на уровень выше
SCRIPT_DIR="$(dirname "$0")"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
OUTPUT_FILE="${PROJECT_ROOT}/terraform_outputs.json"
INV_FILE="${SCRIPT_DIR}/inventory.yml"

# Проверяем, что файл с output существует
if [ ! -f "${OUTPUT_FILE}" ]; then
    echo "❌ File not found: ${OUTPUT_FILE}"
    echo "💡 Terraform should have created it in the previous stage"
    exit 1
fi

# Извлекаем IP через grep и sed (POSIX-совместимо)
# Формат JSON: "vm_public_ip": "130.193.56.11"
VM_IP=$(grep '"vm_public_ip"' "${OUTPUT_FILE}" | sed 's/.*: *"\([^"]*\)".*/\1/' | head -1)

if [ -z "$VM_IP" ]; then
    echo "❌ Could not extract VM IP from ${OUTPUT_FILE}"
    echo "💡 File content:"
    cat "${OUTPUT_FILE}"
    exit 1
fi

# Генерируем inventory БЕЗ указания ключа (sshagent подставит его)
cat > "${INV_FILE}" << EOF
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

echo "✅ Inventory created: ${INV_FILE}"
echo "📍 VM IP: ${VM_IP}"