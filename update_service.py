import os

path = 'backend-springboot/src/main/java/com/uteq/backend/service/ReservacionService.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('p.getFechaLimiteRetiro()', 'p.getFechaLimiteRetiro() != null ? p.getFechaLimiteRetiro().atOffset(java.time.ZoneOffset.UTC) : null')

with open(path, 'w', encoding='utf-8', newline='\n') as f:
    f.write(content)

print("Updated Service")
