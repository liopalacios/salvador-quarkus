# API MyIdentifier: autenticación y endpoints

Base URL: `https://api.salvador-latam.com/api/v1`

## Autenticación

- Todas las rutas exigen `Authorization: Bearer <token>`, salvo las marcadas como **Público**.
- En las rutas públicas el header `Authorization` se ignora, así que un token vencido no bloquea el login.
- Token JWT HS256 con vigencia de 24 h. Claims: `sub` (userId), `phone`, `role` (`USER` | `SUPERVISOR`), `iat`, `exp` y `jti`. No hay refresh token: al vencer, se vuelve a hacer login.
- Formato único de error (sin stack traces):

```json
{ "timestamp": "2026-11-17T10:00:00.000-0500", "status": 401, "error": "UNAUTHORIZED", "message": "Token inválido o expirado" }
```

| Código | `error` | Cuándo |
|---|---|---|
| 400 | `VALIDATION_ERROR` / `BAD_REQUEST` | Body o parámetros inválidos |
| 401 | `UNAUTHORIZED` | Token ausente, inválido, con otra firma o vencido |
| 401 | `INVALID_CREDENTIALS` | Login: celular inexistente o contraseña errónea (mismo mensaje en ambos casos) |
| 403 | `FORBIDDEN` | Token válido sin permiso sobre el recurso o sin el rol |
| 403 | `PASSWORD_REGISTRATION_EXPIRED` | register-password fuera de los 15 min posteriores al registro |
| 403 | `HTTPS_REQUIRED` | Petición por HTTP (solo en Azure) |
| 409 | `CONFLICT` | Duplicado (celular, contraseña ya creada, foto usada) |
| 423 | `ACCOUNT_LOCKED` | Login bloqueado 15 min tras 3 fallos (header `Retry-After`) |
| 429 | `TOO_MANY_REQUESTS` | Rate limit por IP (header `Retry-After`) |

Rate limit por IP: login y supervisor/login comparten 10/min, register-password 10/min, users/register 10/min y users/phone 30/min (holgado por CGNAT). Todos se pueden configurar en `application.yaml`.

---

## Públicos

### GET /users/phone/{phone}
200 existe · 404 no existe · 400 formato · 429.
```json
// 200
{ "exists": true }
```

### POST /users/register
201 · 400 (phone `^\d{9}$`) · 409 celular ya registrado · 429.
```json
// request
{ "phone": "987654321" }
// 201
{ "id": "6f1c…", "phone": "987654321", "email": null, "address": null, "nationality": null, "isVerified": false, "message": null }
```

### POST /auth/register-password
201 · 400 (password `^\d{4}$`, userId UUID) · 403 `PASSWORD_REGISTRATION_EXPIRED` · 404 usuario inexistente · 409 ya tiene contraseña · 429.
```json
// request
{ "userId": "6f1c…", "password": "1234" }
// 201
{ "token": "eyJhbGciOiJIUzI1NiJ9…", "userId": "6f1c…" }
```

### POST /auth/login
200 · 400 · 401 `INVALID_CREDENTIALS` · 423 `ACCOUNT_LOCKED` · 429.
Los intentos 1 y 2 fallidos dan 401. El 3.º da 423 y bloquea el celular 15 min (en ese tiempo, incluso con la contraseña correcta, responde 423). Un login exitoso reinicia el contador.
```json
// request
{ "phone": "987654321", "password": "1234" }
// 200
{ "token": "eyJhbGciOiJIUzI1NiJ9…", "userId": "6f1c…" }
```

### POST /auth/supervisor/login
Igual que `/auth/login`, pero solo emite token si el usuario tiene `role = SUPERVISOR`. A un USER responde 401.

### POST /auth/otp/send · POST /auth/otp/verify (siguiente fase)
Por ahora responden 501 `NOT_IMPLEMENTED`. Request: `{ "phone": "987654321" }` y `{ "phone": "987654321", "code": "123456" }`.

### GET /actuator/health
200 `{ "status": "UP" }`.

---

## Protegidos (cliente)

### PUT /users/{userId}/dni-photos (nuevo)
200 · 400 · 401 · 403 (`{userId}` ≠ `sub`) · 404 · 409 foto ya usada por otro usuario.
```json
// request
{ "dniFrontPhotoId": "front-abc", "dniBackPhotoId": "back-abc" }
// 200
{ "userId": "6f1c…", "dniFrontPhotoId": "front-abc", "dniBackPhotoId": "back-abc" }
```

### GET /users/{id}
200 · 401 · 403 (ni dueño ni supervisor) · 404. Responde igual que `/users/register`.

### POST /questionnaires/register
201 · 400 · 401 · 403 (`body.userId` ≠ `sub`) · 404.
```json
// request
{ "userId": "6f1c…", "employmentType": "1h", "employmentTenure": "2a", "residentialTenure": "3a", "housingType": "4b", "dependents": "5a" }
// 201
{ "questionnaireId": 12, "userId": "6f1c…", "status": "A", "createdAt": "…" }
```

### GET /scores/{userId}
200 · 401 · 403 (ni dueño ni supervisor) · 404.
```json
// 200
{ "scoreValue": 30, "levelName": "PLATA", "maxLoanAmount": 300, "reasons": ["…"] }
```

### POST /scores/calculate (legado)
200 · 400 · 401 · 403 (`body.userId` ≠ `sub`).

### POST /loan-requests/register
201 · 400 · 401 · 403 (`idUsuario` ≠ `sub` o `numCelular` ≠ claim `phone`) · 422.
El backend usa el userId y el celular del token.
```json
// request
{ "idUsuario": "6f1c…", "numCelular": "987654321", "tipoDeposito": "YAPE", "categoria": "ORO", "puntaje": 72, "montoSolicitado": 200, "frecuenciaCuota": 15 }
// 201
{ "id": "a9e2…", "status": "PENDIENTE", "createdAt": "…" }
```

### GET /loans/current/phone/{phone}
200 · 400 · 401 · 403 (`{phone}` ≠ claim `phone` y no es supervisor) · 404 · 429.
```json
// 200 (solicitud pendiente)
{ "tipo": "SOLICITUD", "idSolicitud": "a9e2…", "estado": "PENDIENTE", "carteraDigital": "YAPE", "montoSolicitado": 200, "frecuenciaCuota": 15 }
```

### POST /context/{userId} · POST /stability/{userId}
Solo el dueño (403 si no). Responden 201.

### GET /context/{userId} · GET /stability/{userId}
Dueño o supervisor. Responden 200 o 404.

---

## Protegidos (solo SUPERVISOR)

### GET /loan-requests?estado=PENDIENTE|PROCESADA|RECHAZADA
200 · 400 · 401 · 403 con token USER.
```json
// 200
[ { "idSolicitud": "a9e2…", "numCelular": "987654321", "montoSolicitado": 200, "puntaje": 72, "categoria": "ORO", "estado": "PENDIENTE", "carteraDigital": "YAPE" } ]
```

### POST /loans/register
201 · 400 · 401 · 403 con token USER · 404 · 409 · 422. `created_by` = `sub` del supervisor.
```json
// request
{ "idSolicitud": "a9e2…", "codigoOperacion": "OP-458712" }
```

---

## Operación

- `JWT_SECRET` (al menos 32 bytes) es obligatorio. Ya no hay valor por defecto y sin él la app no arranca. En local: `export JWT_SECRET=$(openssl rand -hex 32)`.
- Para crear un supervisor: el usuario se registra normalmente y luego se ejecuta `UPDATE users SET role = 'SUPERVISOR' WHERE phone = '9XXXXXXXX';`.
- Auditoría: cada intento de login y cada registro de contraseña quedan en `application_attempts` (fecha, celular, IP, resultado y `failure_reason`), nunca la contraseña.
