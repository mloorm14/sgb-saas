import os

path = 'backend-springboot/src/main/java/com/uteq/backend/repository/projection/ReservacionHoyProjection.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('OffsetDateTime getFechaLimiteRetiro()', 'java.time.Instant getFechaLimiteRetiro()')
content = content.replace('import java.time.OffsetDateTime;', '')

with open(path, 'w', encoding='utf-8', newline='\n') as f:
    f.write(content)

print("Updated Projection")
