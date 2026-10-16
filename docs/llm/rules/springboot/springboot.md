# Reglas — Spring Boot / Backend Java

Reglas específicas para las partes de este proyecto construidas con Spring Boot. Las reglas
globales están en [`general.md`](../common/general.md).

## Organización de paquetes: por dominio

Las clases se organizan **por dominio de negocio**, no por capa técnica a nivel de raíz.

Cada dominio (ej. `auth`, `user`, `employee`, `invitation`...) es su propio paquete, y dentro de
él se subdivide por capa técnica solo cuando ese dominio la necesita: `controller/service/
repository/model/dto/event/mapper/exception/...`. No debe existir un paquete `controller` o
`service` global que agrupe clases de todos los dominios.

Lo transversal a varios dominios (configuración, excepciones genéricas, utilidades, seguridad
compartida, clientes HTTP comunes) vive en un paquete `shared`, subdividido igual por capa
técnica dentro (`shared/config`, `shared/exception`, `shared/security`, `shared/util`, etc.).

Ejemplo de estructura:

```
com/<empresa>/<servicio>/
├── auth/
│   ├── config/
│   ├── controller/
│   ├── dto/
│   ├── exception/
│   ├── security/
│   └── service/
├── user/
│   ├── controller/
│   ├── dto/
│   ├── event/
│   ├── model/
│   ├── repository/
│   └── service/
└── shared/
    ├── config/
    ├── exception/
    ├── model/
    ├── security/
    └── util/
```

## Documentación

- Documentación **siempre en español**: Javadoc en toda clase, interfaz, enum, método y
  constante que se añada — no solo en lo no trivial. Cada valor de un enum lleva su propia
  línea de Javadoc si su nombre no basta para entenderlo.
- No documentar: getters/setters generados por Lombok, DTOs autoexplicativos (record con nombres
  de campo claros), y excepciones simples de una línea sin lógica propia.

## Código

- Cachear con `@Cacheable` los métodos de servicio de solo lectura cuyo coste sea relevante
  (llamadas a APIs externas, agregados sobre varias tablas/repositorios, cálculos
  repetidos en cada carga de pantalla) siempre que el dato tolere servirse ligeramente desatrasado.
  No hace falta registrar el nombre de la caché en `CacheConfig` salvo que necesite un TTL distinto
  al de 60s por defecto.
- Antes de anotar con `@Cacheable`/`@Transactional` desde otro método de la misma clase: recuerda
  que Spring AOP no intercepta auto-invocación (`this.metodo()`). Si hace falta que el proxy
  actúe, el método debe vivir en otro bean.
- Manejo de excepciones centralizado con `ErrorResponse` + `GlobalExceptionHandler`.
- Migraciones de base de datos con Flyway.

## Alcance de commits

El formato general está en [`commits.md`](../common/commits.md). En este stack, el alcance
suele ser el dominio afectado (ej: `auth`, `user`).

- Ejemplo: `feat(auth): agregar autenticación OAuth`

## Checklist de calidad (además de la de `general.md`)

- [ ] Clases ubicadas en el paquete de su dominio, no en un paquete de capa global
- [ ] Documentación Javadoc donde corresponda
