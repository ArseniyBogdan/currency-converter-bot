#!/bin/sh
# Генерирует inventory.yml из Terraform output (POSIX-совместимый)

set -e

SCRIPT_DIR="$(dirname "$0")"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
INV_FILE="${SCRIPT_DIR}/inventory.yml"

# Ищем terraform_outputs.json в нескольких местах
OUTPUT_FILE=""
for loc in "${SCRIPT_DIR}/terraform_outputs.json" "${PROJECT_ROOT}/terraform_outputs.json" "./terraform_outputs.json"; do
    if [ -f "$loc" ]; then
        OUTPUT_FILE="$loc"
        break
    fi
done

if [ -z "$OUTPUT_FILE" ] || [ ! -f "$OUTPUT_FILE" ]; then
    echo "❌ terraform_outputs.json not found"
    exit 1
fi

# Извлекаем IP: Terraform JSON имеет структуру {"vm_public_ip": {"value": "IP", ...}}
# Используем grep + sed для извлечения значения из вложенного объекта
VM_IP=$(grep -A1 '"vm_public_ip"' "$OUTPUT_FILE" | grep '"value"' | sed 's/.*"value"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/' | head -1)

if [ -z "$VM_IP" ]; then
    echo "❌ Could not extract VM IP from $OUTPUT_FILE"
    echo "💡 File content preview:"
    head -20 "$OUTPUT_FILE"
    exit 1
fi

# Генерируем inventory БЕЗ указания ключа (sshagent или ansible.cfg подставят)
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

echo "✅ Inventory created: $INV_FILE"
echo "📍 VM IP: $VM_IP"