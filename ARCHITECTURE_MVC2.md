# Backend MVC2 structure

The Spring Boot API uses a layered MVC2 package structure. Each layer is further grouped by business domain.

```text
com.claritycam.platform
|-- controller/<domain>     HTTP endpoints and request/response mapping
|-- service/<domain>        Use cases, transactions and business rules
|-- repository/<domain>     Spring Data persistence gateways
|-- model/<domain>          JPA entities, enums and domain state
|-- dto/<domain>            External request/response contracts
|-- infrastructure/         Storage and external-system adapters
|-- exception/              API exceptions and centralized handling
|-- config/                 Security, bootstrap and application setup
`-- ClarityCamPlatformApplication
```

## Request flow

```text
Client -> Controller -> Service -> Repository -> Database
                     -> Infrastructure adapter
```

## Dependency rules

- Controllers validate and translate HTTP input, then delegate to services.
- Services own transactions and business decisions.
- Repositories only expose persistence operations.
- Models do not depend on controllers or views.
- DTOs protect the model from being used as an uncontrolled external contract.
- Existing endpoint paths and database mappings remain unchanged by this refactor.
