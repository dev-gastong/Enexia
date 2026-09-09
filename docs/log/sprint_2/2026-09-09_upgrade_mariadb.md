# Actualización a MariaDB 11.8.9 y el bug de concurrencia que destapó

**Fecha:** 2026-09-09
**Motivo:** `ddl-auto=update` no podía agregar columnas contra MariaDB 10.4 (ver [sprint_2/2026-09-08_sprint2_backend.md §7](./2026-09-08_sprint2_backend.md))
**Resultado:** ✅ actualizado · ✅ 166 pruebas en verde · ⚠️ **2 bugs de concurrencia encontrados y corregidos**

---

## 1. Por qué actualizar XAMPP no servía

Antes de mover nada se verificó: los XAMPP estándar (PHP 8.0.30, 8.1.25, 8.2.12) **siguen trayendo MariaDB 10.4.32**, exactamente la versión que había. Solo XAMPP-Lite trae 11.4.10 LTS, pero cambia también Apache y PHP.

Se instaló **MariaDB 11.8 LTS standalone**, dejando XAMPP intacto como vía de retorno.

## 2. La causa raíz no era "el dialecto mal configurado"

Se probaron tres configuraciones borrando `evento.fecha_creacion` a propósito y viendo si Hibernate la recreaba:

| Config | Dialecto detectado | Sentencia generada | ¿Funciona? |
|---|---|---|---|
| Dialecto fijado a mano (la que había) | MariaDBDialect | `alter table IF EXISTS` | ❌ error 1064 |
| Autodetección + conector MySQL | MySQLDialect **5.5.5** | `alter table` | ✅ pero dialecto no soportado |
| Autodetección + driver MariaDB nativo | MariaDBDialect **10.4.32** | `alter table IF EXISTS` | ❌ error 1064 |

La tercera fila es la concluyente: **aun detectando la versión exacta, Hibernate genera sintaxis que 10.4 no acepta**, y lo dice explícitamente:

```
HHH000511: The 10.4.32 version for [org.hibernate.dialect.MariaDBDialect]
is no longer supported... The minimum supported version is 10.6.0
```

No era un dialecto mal elegido: **Hibernate 7 (Spring Boot 4.1) dejó de soportar MariaDB por debajo de 10.6**. Ninguna configuración lo arregla.

> La segunda fila "funcionaba" por casualidad: Hibernate creía hablar con un MySQL 5.5 (MariaDB reporta `5.5.5-10.4.32-MariaDB` por compatibilidad) y generaba SQL ultra conservador. Es la configuración que el equipo ya había descartado en Sprint 1 porque rompía el `SELECT ... FOR UPDATE` del bloqueo de login.

## 3. Procedimiento aplicado

```bash
# 1. Backup (fuera del repo)
mysqldump -u root --databases enexia --routines --triggers --events \
  --default-character-set=utf8mb4 > backups/enexia_2026-09-08_pre-upgrade.sql

# 2. Instalar MariaDB 11.8 LTS (MSI): root sin contraseña, puerto 3306,
#    servicio, utf8mb4, buffer pool 512MB

# 3. Restaurar
mysql -u root -e "CREATE DATABASE enexia CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root enexia < backups/enexia_2026-09-08_pre-upgrade.sql
```

Verificado tras restaurar: 40 tablas, 176 usuarios, 52 eventos, las 40 en InnoDB. `application.properties` **no cambió** (root sigue sin contraseña, mismo puerto).

Confirmación de que el problema original desapareció: 0 avisos `HHH000511`, 0 errores `1064`, y al borrar `fecha_creacion` Hibernate **la recreó solo**.

---

## 4. 🐛 Lo importante: el servidor nuevo destapó dos bugs de concurrencia

La suite, que pasaba 165/165 contra 10.4, falló contra 11.8. **No era una regresión de la actualización: eran bugs que ya existían y que la 10.4 ocultaba.**

### 4.1 Pérdida de actualización: la moderación resucitaba un evento dado de baja

**Síntoma:** la prueba "409 al dar de baja dos veces" recibía 200 en la segunda baja.

**Causa:** sobre un mismo evento escriben dos procesos en paralelo, y tocan campos distintos:

```
t0  el pipeline de moderación carga el evento      (estado_organizador = PUBLICADO)
t1  el organizador lo da de baja y confirma        (estado_organizador = DADO_DE_BAJA)
t2  el pipeline confirma y reescribe TODA la fila  (estado_organizador = PUBLICADO)  ← se perdió la baja
```

Hibernate, por defecto, genera el `UPDATE` con **todas** las columnas, tengan cambios o no. La del pipeline llevaba el `estado_organizador` que había leído en t0.

**Impacto real:** un organizador que crea un evento y se arrepiente antes de que termine la moderación veía su evento **publicado igual**, sin haber hecho nada.

**Corrección:** `@DynamicUpdate` en `Evento` → el `UPDATE` incluye solo las columnas que esa transacción modificó de verdad.

### 4.2 Escrituras concurrentes sin serializar

Con lo anterior corregido apareció el siguiente: la baja respondía **500**.

```
Record has changed since last read in table 'evento'; try restarting transaction
```

Es el error 1020 de MariaDB, y aparece por una función nueva:

```
innodb_snapshot_isolation = ON     (por defecto desde MariaDB 11.6.2)
```

Bajo REPEATABLE READ, un `SELECT ... FOR UPDATE` que encuentra la fila modificada **después** de la instantánea de la transacción **aborta** en vez de seguir. Es una protección deliberada contra escribir sobre datos vencidos — y tenía razón: la transacción iba a decidir con información vieja.

**Corrección, en dos partes:**

1. **Bloqueo pesimista** (`EventoRepository.bloquearParaActualizar`): tanto la baja como el pipeline toman `SELECT ... FOR UPDATE` sobre el evento **antes** de escribir. Las dos rutas toman ese bloqueo primero, así que el orden de adquisición es siempre el mismo y no puede haber interbloqueo. Es el mismo patrón que `IntentosLoginService` ya usaba para el contador de intentos fallidos.

2. **`READ_COMMITTED`** en esas transacciones: sin instantánea de larga vida, cada sentencia ve lo último confirmado, así que el bloqueo simplemente espera su turno. Es el nivel que usan por defecto Oracle y PostgreSQL, y el adecuado para transacciones cortas de escritura.

### 4.3 Prueba de regresión

Se agregó `bajaDuranteModeracion`, que da de baja **sin esperar** el veredicto — el escenario exacto del bug — y verifica que el evento quede `DADO_DE_BAJA` y fuera del catálogo. Además se hizo determinista `bajaRepetida`, que ahora espera el veredicto antes de la primera baja para medir una sola cosa.

**Suite: 166 pruebas, 0 fallos, verificado en 3 corridas seguidas.**

---

## 5. Lección

> La 10.4 no es que "andaba bien": **ocultaba** dos bugs de concurrencia reales. Uno de ellos publicaba eventos que el organizador había cancelado.
>
> Actualizar no rompió nada. Expuso lo que ya estaba roto, y encima con un mensaje de error preciso. Es un buen argumento para no quedarse en versiones viejas de la base "porque total funciona".

## 6. Estado final

| | |
|---|---|
| Servidor | MariaDB **11.8.9** LTS, puerto 3306, `utf8mb4` |
| Ruta | `C:\Program Files\MariaDB 11.8` |
| `application.properties` | **sin cambios** |
| `ddl-auto=update` | ✅ funciona; ya puede agregar columnas |
| Migraciones | Siguen siendo la práctica recomendada para producción (`validate` + Flyway) |
| XAMPP | Intacto. **No arrancar su MySQL**: pelea por el 3306 |
| Backup | `backups/enexia_2026-09-08_pre-upgrade.sql` |
