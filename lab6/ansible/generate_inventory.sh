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

# Извлекаем IP: ищем блок "vm_public_ip", затем внутри него "value"
# grep -A 10 берёт 10 строк после нахождения ключа — этого достаточно для блока
VM_IP=$(grep -A 10 '"vm_public_ip"' "$OUTPUT_FILE" | grep '"value"' | head -1 | sed 's/.*"value"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')

if [ -z "$VM_IP" ]; then
    echo "❌ Could not extract VM IP from $OUTPUT_FILE"
    echo "💡 Debug: trying alternative extraction..."
    # Альтернатива: просто ищем первую строку с "value" после "vm_public_ip"
    VM_IP=$(awk '/"vm_public_ip"/,/}/ {if(/"value"/) {gsub(/.*"value"[[:space:]]*:[[:space:]]*"/, ""); gsub(/".*/, ""); print; exit}}' "$OUTPUT_FILE")
fi

if [ -z "$VM_IP" ]; then
    echo "❌ Still could not extract VM IP"
    echo "💡 File content:"
    cat "$OUTPUT_FILE"
    exit 1
fi

# Генерируем inventory БЕЗ указания ключа
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