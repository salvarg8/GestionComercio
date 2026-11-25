# GestionComercio

Sistema de gestión comercial de escritorio (JavaFX + Spring Boot) sobre PostgreSQL.
Varias PC (cajas, depósito) pueden trabajar a la vez sobre la misma base.

## Requisitos

- Java 21
- Maven 3.9+
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (para la base de desarrollo y los tests de integración)

## Inicio rápido

Desde cero, con Docker en ejecución:

```bash
git clone https://github.com/salvarg8/GestionComercio.git
cd GestionComercio
docker compose up -d      # PostgreSQL de desarrollo
mvn javafx:run            # compila y abre la aplicación
```

En el primer arranque se crean las tablas y los datos iniciales. Ingresar con
el usuario **`admin`** y la contraseña **`admin123`**, y cambiarla desde
el perfil (Cambiar contraseña).

## Compilar

```bash
mvn clean package         # compila, corre Checkstyle y los tests
mvn clean package -DskipTests
```

Checkstyle ([checkstyle.xml](checkstyle.xml)) corre en la fase `validate`: una
violación corta la compilación.

## Estructura

| Carpeta                              | Contenido                                              |
|--------------------------------------|--------------------------------------------------------|
| `src/main/java/org/gestionComercio`  | Código: `entity`, `repository`, `service`, `controller`, `view`, etc. |
| `src/main/resources`                 | `application.properties`, vistas FXML, estilos y migraciones Flyway |
| `src/test`                           | Tests unitarios y de integración                       |
| `scripts/migracion`                  | Script para pasar datos de SQLite a PostgreSQL         |
| `data/` (no versionada)              | Datos locales: logos subidos, base SQLite anterior     |

## Base de datos para desarrollo

El archivo [docker-compose.yml](docker-compose.yml) levanta PostgreSQL 17 con la base, el usuario y la contraseña que espera la aplicación:

```bash
docker compose up -d      # levantar (los datos quedan en un volumen)
docker compose ps         # ver el estado
docker compose down       # detener (conserva los datos)
docker compose down -v    # detener y borrar los datos
```

| Dato       | Valor por defecto                                    | Variable de entorno |
|------------|------------------------------------------------------|---------------------|
| URL        | `jdbc:postgresql://localhost:5432/gestion_comercio` | `DB_URL`            |
| Usuario    | `gestion`                                            | `DB_USER`           |
| Contraseña | `gestion`                                            | `DB_PASSWORD`       |

Para conectar otra PC de la red, apuntar `DB_URL` al servidor, por ejemplo
`jdbc:postgresql://192.168.0.10:5432/gestion_comercio`.

Al arrancar contra una base vacía, Flyway crea las tablas y `DataInitializer`
carga los permisos, la empresa de ejemplo y el usuario `admin` / `admin123`.

## Migraciones de base de datos (Flyway)

El esquema lo definen sólo los scripts de
[src/main/resources/db/migration](src/main/resources/db/migration), que Flyway
aplica al arrancar la aplicación. Hibernate no crea ni modifica tablas
(`ddl-auto=validate`): si las entidades no coinciden con el esquema, la
aplicación no arranca.

**Regla del equipo:** cada cambio de entidad (tabla, columna, tipo, índice,
restricción) viene en el mismo PR con su script
`V<n>__descripcion.sql`, con `<n>` el siguiente número libre. Un script ya
mergeado no se modifica nunca: un error se corrige con otro script.

- Cada migración corre en una transacción y PostgreSQL tiene DDL transaccional:
  si falla, se revierte entera y la aplicación no arranca.
- Los datos iniciales (permisos, rol y usuario administrador, empresa) los
  mantiene `DataInitializer`, no las migraciones: los permisos salen del enum
  `PermisoCodigo`, así que agregar uno no requiere script.
- Las columnas de enums son `varchar` sin `CHECK` de valores, así que agregar un
  valor a un enum tampoco requiere script (renombrarlo o quitarlo, sí).
- Los tests de integración usan las mismas migraciones.

### Base de desarrollo creada antes de COM31

Si la base del `docker-compose.yml` la creó Hibernate (antes de Flyway), la
aplicación no arranca: Flyway no aplica migraciones sobre un esquema que no
creó. Hay que recrearla:

```bash
docker compose down -v
docker compose up -d
```

y arrancar la aplicación una vez. Para recuperar los datos de la base SQLite
anterior, seguir después la sección siguiente.

## Configuración por comercio

Cada comercio adapta el sistema sin cambiar el código desde el menú
**Parámetros del sistema** (permisos `CONFIGURACION_VER` y
`CONFIGURACION_EDITAR`). Los parámetros se guardan en la tabla `configuracion`
(una sola fila, compartida por todas las PC) y rigen desde la próxima operación,
sin reiniciar.

| Parámetro | Por defecto | Efecto |
|-----------|-------------|--------|
| Exigir caja abierta para vender | Sí | Si no se exige, quien no tiene caja abierta vende igual: la venta queda sin caja y su efectivo no entra a ninguna (en Inicio aparece como "Sin caja"). |
| Forma de pago por defecto | Efectivo | Con la que empieza cada venta en el punto de venta. |
| Comprobante por defecto | Factura (si está habilitada; si no, ticket) | También ticket o remito. |
| Redondeo del total | Sin redondeo | A los 10 centavos, al peso, a los 10 o a los 100 pesos, siempre hacia abajo: la diferencia se suma al descuento de la venta (así también cuadra en ARCA). Un total menor que el múltiplo no se redondea. |
| Permitir vender sin stock | No | Valor que se propone al crear un producto. |
| Unidad de medida por defecto | Unidad | Valor que se propone al crear un producto. |
| Formato del ticket | Térmico de 80 mm | Térmico de 58 u 80 mm, o A4. Facturas y remitos salen siempre en A4. |
| Imprimir el logo | Sí | El logo se carga en **Datos de la empresa**. |
| Texto al pie | (vacío) | Horario, redes, políticas de cambio: hasta 500 caracteres, se respetan los renglones. |

- **Impresora:** es de cada PC (se guarda en el registro de Windows del
  usuario, no en la base). Sin impresora elegida, los comprobantes se abren en
  el visor de PDF como antes. **Imprimir prueba** manda un ticket de ejemplo
  con el formato que muestra la pantalla, aunque todavía no esté guardado.
- **Datos iniciales por rubro:** para una instalación nueva, crea las
  categorías típicas del rubro (almacén, kiosco, verdulería, ferretería…) y
  fija la unidad de medida por defecto. No duplica categorías existentes.

### Control del efectivo en caja

Se configura en **Datos de la empresa**, sección "Control del efectivo en caja"
(columnas de `empresas`). Sin monto máximo, la caja funciona sin control.

| Parámetro | Efecto |
|-----------|--------|
| Monto máximo en caja | Cuando el saldo esperado de la caja llega a este monto, Punto de venta y Caja muestran una franja que pide retirar dinero (y un cartel al alcanzarlo). |
| Clientes antes de bloquear | Ventas que se permiten después de alcanzar el monto. Superadas, no se registran ventas hasta que el saldo baje del monto (por ejemplo, con un retiro). Vacío: nunca se bloquea; 0: se bloquea apenas se alcanza. Sólo con monto máximo. |
| Los retiros los debe confirmar un encargado | Un movimiento de tipo Retiro lo confirma un usuario con el permiso `CAJA_CONFIRMAR_RETIRO` (el rol ADMIN lo tiene; al resto se asigna desde Roles). Si el cajero no lo tiene, el diálogo pide usuario y contraseña del encargado sin cerrar su sesión. Quién confirmó se ve en la descripción del movimiento. |

- Se cuentan como clientes las ventas no anuladas ni canceladas de la caja,
  con cualquier forma de pago. Si el saldo baja del monto y vuelve a
  alcanzarlo, la cuenta empieza de nuevo.
- No se guarda estado: avisar, bloqueada y clientes restantes se calculan
  siempre a partir de los movimientos y las ventas de la caja.

### Actualización masiva de precios

Desde **Actualizar precios** (permiso `PRODUCTO_ACTUALIZAR_PRECIOS`) se aumenta
o rebaja por porcentaje el precio de venta, el de compra o ambos de los
productos activos de una categoría, marca y/o proveedor (o de todos), con
redondeo opcional al múltiplo más cercano.

- Primero se genera la **vista previa**; cualquier cambio en el criterio la
  descarta. Al aplicar, el sistema vuelve a calcular: si desde la vista previa
  cambió algún precio o la lista de productos, no aplica nada.
- Cada actualización queda registrada (`actualizaciones_precios` y su detalle)
  con el usuario, la fecha, el criterio y los precios anteriores y nuevos de
  cada producto. El historial se ve en la misma pantalla.

## Copias de seguridad

Desde el menú **Copias de seguridad** (permisos `BACKUP_CREAR` y
`BACKUP_RESTAURAR`) se crea una copia de la base en una carpeta a elección y se
restaura una copia anterior.

- **Backup:** `pg_dump` en formato custom (`gestion_comercio_AAAAMMDD_HHMMSS.backup`).
  Es consistente aunque haya usuarios trabajando. Conviene guardar también una
  copia fuera de la PC (pendrive, carpeta en la nube).
- **Restauración:** `pg_restore` previa confirmación. Antes guarda una copia de
  la base actual (`..._antes_de_restaurar.backup`) en la carpeta de copias
  automáticas, avisa a las demás PC (que muestran un aviso y se cierran) y corta
  sus conexiones. Restaura en un esquema vacío en una sola transacción: si
  falla, o si la copia es de una versión más nueva de la aplicación, la base
  queda como estaba. Al terminar hay que volver a abrir la aplicación en todas
  las PC; al arrancar, Flyway aplica las migraciones que le falten a la copia.
- **Copias automáticas:** sólo en la PC servidor (`BACKUP_AUTOMATICO=true`).
  Hace una copia por día y otra después de cada cierre de caja (de cualquier
  PC), y conserva las últimas `BACKUP_RETENCION`.

| Variable de entorno | Valor por defecto | Uso |
|---------------------|-------------------|-----|
| `PG_BIN`            | (vacío)           | Carpeta de `pg_dump`/`pg_restore`. Vacía: se buscan en `pgsql/bin` y en el `PATH` |
| `BACKUP_CARPETA`    | `data/backups`    | Copias automáticas y copias previas a cada restauración |
| `BACKUP_RETENCION`  | `14`              | Copias automáticas que se conservan |
| `BACKUP_AUTOMATICO` | `false`           | `true` en la PC servidor |

Las herramientas de PostgreSQL tienen que ser de la misma versión mayor que el
servidor (17) o más nuevas. En desarrollo, sin PostgreSQL instalado, se toman
de la [distribución de binarios](https://www.enterprisedb.com/download-postgresql-binaries)
(zip de Windows x64): copiar su carpeta `pgsql/bin` a `pgsql/bin` en la raíz
del proyecto (no se versiona). La aplicación las encuentra ahí sin configurar
`PG_BIN`. El usuario de la base tiene que ser
dueño de la base (la restauración renombra el esquema `public`).

## Facturación electrónica (ARCA)

Las facturas A, B y C y sus notas de crédito se autorizan con los web services
de ARCA (WSAA + WSFEv1). Cada comercio factura con **su propio certificado**:
la clave privada se genera en el sistema y no sale del comercio.

Se configura desde el menú **Facturación electrónica** (permiso
`FACTURACION_CONFIGURAR`), con una guía paso a paso: datos de la empresa,
ambiente, punto de venta, pedido e importación del certificado, autorización
en ARCA, prueba de conexión y habilitación. La pantalla avisa cuando el
certificado está por vencer (30 días), igual que la pantalla de Inicio.

Con la facturación habilitada, el punto de venta ofrece la factura que
corresponde al cliente (A, B o C) y pide el CAE al confirmar. Si ARCA no
responde, la venta queda registrada con la factura *pendiente de CAE*. Anular
una venta con factura autorizada emite la nota de crédito asociada. El
listado de ventas muestra el estado de cada factura (facturada, pendiente,
rechazada), y en **Comprobantes pendientes de CAE** (permiso
`FACTURACION_REINTENTAR`) se reintentan los que quedaron sin CAE.

El certificado se guarda en la base **cifrado** con una clave que no queda en
la base: se carga desde la pantalla en cada PC que factura y se guarda en el
perfil de Windows del usuario (registro, sin cifrar). Es la misma clave en todas
las PC; la pantalla verifica que abra el certificado guardado. Así, una copia de
la base o de un backup no alcanza para facturar a nombre del comercio. La clave
se puede cambiar desde la misma pantalla (el certificado se vuelve a cifrar y
las demás PC escriben la nueva). Si se pierde, se descarta el certificado y se
genera otro en ARCA.

Requisitos en ARCA (los hace el comercio o su contador): clave fiscal nivel 3,
un **punto de venta exclusivo para web services**, el certificado (se sube el
pedido que genera el sistema en "Administración de Certificados Digitales", o en
WSASS para homologación) y la relación del certificado con el servicio
"Facturación Electrónica" en el Administrador de Relaciones.

| Variable de entorno         | Valor por defecto | Uso |
|-----------------------------|-------------------|-----|
| `ARCA_CLAVE_CERTIFICADO`    | (vacío)           | Clave con que se cifra el certificado guardado en la base. Opcional: si no está, se carga desde la pantalla (paso 2 de la guía). Si está, tiene prioridad |
| `ARCA_TIMEOUT`              | `30s`             | Espera máxima de cada llamada a ARCA |
| `ARCA_MONTO_IDENTIFICACION` | `10000000`        | Desde este total, la factura a consumidor final debe identificar al comprador |

El reloj de las PC tiene que estar sincronizado (ARCA rechaza el pedido de
acceso si la hora no coincide). Para ver en el log los pedidos y respuestas
(sin token, sign ni certificado): `logging.level.org.gestionComercio.arca=DEBUG`.

## Licencias

Cada comercio necesita una licencia. Es **por comercio**: vale para todas sus
PC, se guarda en la base (tabla `licencia`) y lista los códigos de las PC
habilitadas. Funciona sin internet.

- **Período de prueba:** sin licencia, el sistema funciona completo durante 30
  días desde la instalación (desde que se creó la base).
- **Modalidades:** pago único (sin vencimiento) o abono (con vencimiento; se
  renueva con una licencia nueva). Avisa desde 15 días antes de vencer.
- **Módulos:** la licencia indica los módulos habilitados (por ahora,
  facturación electrónica). Sin el módulo, se quitan sus permisos y no se
  emiten facturas electrónicas. En la prueba están todos.
- **Modo sólo consulta:** al vencer la prueba o el abono, en una PC que no está
  en la licencia, si la licencia fue modificada o si la fecha de la PC es
  anterior a la última en que se usó el sistema, la sesión queda sólo con los
  permisos de consulta: ver datos, reportes y exportaciones, crear copias de
  seguridad y administrar la licencia. No se bloquea el acceso a los datos.
  Se aplica al iniciar sesión.
- **Pantalla:** menú **Licencia** (permiso `LICENCIA_ADMINISTRAR`): estado,
  código de esta PC (para pedir la licencia) y activación desde el archivo
  `.lic` o pegando la clave. Se activa una vez desde cualquier PC incluida.
- **Cambio de PC:** se pide al proveedor una licencia nueva con la lista de
  códigos actualizada, que reemplaza a la anterior. No se acepta una licencia
  emitida antes que la activa. El código cambia también si se reinstala
  Windows.

La clave es un texto firmado con Ed25519 (`GC1.<datos>.<firma>`): la
aplicación sólo tiene la clave pública
([VerificadorLicencia](src/main/java/org/gestionComercio/licencia/VerificadorLicencia.java)),
así que modificar la licencia la invalida. La clave pública está fija en el
código a propósito (no es configurable).

### Emitir licencias (herramienta interna)

[scripts/licencias/GenerarLicencia.java](scripts/licencias/GenerarLicencia.java)
no forma parte de la aplicación. Usa las clases compiladas:

```bash
mvn compile
# Emitir (sin --vence: pago único). Los códigos de equipo los muestra la pantalla Licencia de cada PC.
java -cp target/classes scripts/licencias/GenerarLicencia.java emitir \
     --clave-privada <ruta>/licencias-privada.key \
     --cliente "Almacén Don José SRL" --cuit 30712345679 \
     --equipos A1B2-C3D4-E5F6-0718,9F8E-7D6C-5B4A-3921 \
     --vence 2027-12-31 --modulos FACTURACION_ELECTRONICA \
     --salida licencia_don_jose.lic
# Verificar un archivo con la clave pública de la aplicación
java -cp target/classes scripts/licencias/GenerarLicencia.java ver licencia_don_jose.lic
```

La **clave privada** (`licencias-privada.key`) no se versiona ni se distribuye
(`*.key` y `*.lic` están en `.gitignore`). Hay que guardarla con copia de
seguridad: sin ella no se pueden emitir licencias que la aplicación acepte. Si
se compromete, se genera otro par (`claves <carpeta>`), se reemplaza
`CLAVE_PUBLICA` y las licencias emitidas antes dejan de valer en las versiones
nuevas.

Si una PC tuvo la fecha adelantada por error y el sistema quedó en sólo
consulta por "fecha de la PC incorrecta", se resuelve activando una licencia
nueva (volver a activar la misma no alcanza).

En desarrollo, una base nueva (`docker compose down -v`) vuelve a tener 30 días
de prueba; para no depender de eso, emitir una licencia para la PC de desarrollo.

Limitación conocida: sin conexión no se puede revocar una licencia. Una licencia
reemplazada sigue siendo válida en otra base que la tenga activada.

## Ejecutar

```bash
mvn javafx:run
```

## Tests

```bash
mvn test
```

Los tests de integración (`*IntegrationTest`) levantan su propio PostgreSQL con
[Testcontainers](https://testcontainers.com/), así que Docker tiene que estar en
ejecución. No usan la base de desarrollo.

## Pasar los datos de la base SQLite anterior (opcional)

Hasta COM40 la aplicación usaba SQLite (`data/gestion-comercio.db`). El script
[scripts/migracion/MigrarSqliteAPostgres.java](scripts/migracion/MigrarSqliteAPostgres.java)
copia esos datos a PostgreSQL:

1. Levantar PostgreSQL y arrancar la aplicación una vez, para que Flyway cree las tablas. Cerrarla.
2. Descargar los drivers al repositorio local de Maven:

   ```bash
   mvn dependency:get -Dartifact=org.xerial:sqlite-jdbc:3.50.3.0
   mvn dependency:get -Dartifact=org.postgresql:postgresql:42.7.7
   ```

3. Ejecutar el script desde la raíz del proyecto.

   Windows (PowerShell):

   ```powershell
   $R = "$env:USERPROFILE\.m2\repository"
   java -cp "$R\org\xerial\sqlite-jdbc\3.50.3.0\sqlite-jdbc-3.50.3.0.jar;$R\org\postgresql\postgresql\42.7.7\postgresql-42.7.7.jar" `
        scripts\migracion\MigrarSqliteAPostgres.java data\gestion-comercio.db
   ```

   Linux/macOS:

   ```bash
   R="$HOME/.m2/repository"
   java -cp "$R/org/xerial/sqlite-jdbc/3.50.3.0/sqlite-jdbc-3.50.3.0.jar:$R/org/postgresql/postgresql/42.7.7/postgresql-42.7.7.jar" \
        scripts/migracion/MigrarSqliteAPostgres.java data/gestion-comercio.db
   ```

   Opcionalmente se pasan URL, usuario y contraseña como argumentos 2, 3 y 4.

El script vacía las tablas de PostgreSQL (incluidos los datos iniciales, pero no
el historial de Flyway), copia
cada tabla en una sola transacción y ajusta las secuencias de los id. Si algo
falla, PostgreSQL queda como estaba.
