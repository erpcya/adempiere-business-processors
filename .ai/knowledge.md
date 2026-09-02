## 1. Identity

| Field | Value |
|---|---|
| Name | adempiere-business-processors |
| Repository Type | Library |
| Classification basis | The declaration in `.ai/repository.yml` sets `type: Library`. |
| Standards | knowledge-contract-v1, repository-classification-v1 |
| Component type | ADempiere library with process implementations, dictionary migrations, and dKron export support |
| Language and target | Java; `sourceCompatibility = 1.17`, `targetCompatibility = 1.17` |
| Build / runtime | Gradle wrapper 7.3.3; CI builds with JDK 17 |
| Published artifact | `io.github.adempiere:adempiere-business-processors`; default publish URL `https://maven.pkg.github.com/erpcya/adempiere-business-processors` |
| Version | Build version from `ADEMPIERE_LIBRARY_VERSION`; default `local-1.0.0`; tags include `ERP-1.2.1`, `ERP-1.2.0`, `ERP-1.1.9` |
| License | GNU General Public License, version 2 |
| Root package or module | `org.spin.eca46` |
| Entity type | `ECA46` |
| Upstream | `https://github.com/adempiere/adempiere-business-processors` |
| Owner | ERP Consultores y Asociados |

---

## 2. Responsibility

This repository owns the ADempiere-side capability to run ADempiere processors outside ADempiere's own scheduler, and to export processor definitions to an external scheduler. It provides concrete ADempiere process classes for accounting, alert, project, request, scheduler, and workflow processors, plus an export process that reads those processor configurations and creates jobs in dKron.

It also owns the dictionary changes that register those processes, menus, forms, application support definitions, setup definitions, and process parameters under entity type `ECA46`.

It does **not** own:

- the dKron service itself or its installation;
- the ADempiere middleware/backend service that dKron jobs call at runtime;
- ADempiere core processor models such as `MAcctProcessor`, `MAlertProcessor`, `MRequestProcessor`, `MScheduler`, `MWorkflowProcessor`, or `MProjectProcessor`;
- UI implementation beyond the dictionary form registration; there are no Swing or Web UI source files in this repository;
- customer-specific customizations.

This is a fork of upstream `adempiere-business-processors`. Local work happens on the repository's default branch, `erpya`.

---

## 3. Architecture

```text
src/main/java/org/spin/eca46/
  process/                 ADempiere process implementations
  setup/                   Setup definition for dKron registration
  util/support/            Processor wrappers and external-processor SPI
xml/migration/             ADempiere dictionary migrations using ECA46
.github/workflows/         CI, publish, and platform review workflows
gradle/wrapper/            Gradle wrapper
build.gradle               Build and publication configuration
```

Each processor follows the same pattern:

- a generated `*Abstract` class under `org.spin.eca46.process` declares the ADempiere process value, name, numeric process ID, and parameter constants;
- a concrete class extends the abstract class and implements the actual work.

The export path is:

- `ExportInternalProcessors` queries active accounting, alert, project, request, scheduler, and workflow processors;
- each is wrapped by an `IProcessorEntity` implementation in `util/support`;
- the selected external processor is obtained from `MADAppRegistration` through `AppSupportHandler`;
- `IExternalProcessor.exportProcessor` is called for each wrapped processor.

`DKron` is the supplied `IExternalProcessor` implementation. It uses a Jersey JAX-RS client to `POST` dKron job definitions to `v1/jobs`, with an HTTP executor that calls the ADempiere backend service.

`CreateDKron` implements `ISetupDefinition` and creates a default `AD_AppRegistration` for `DKron` when one does not already exist.

The migration set is sequential under `xml/migration`, from `10100` through `10170`, all declaring entity type `ECA46`.

### Pre-existing records this repository modifies

| Record | Table | Columns changed | Effect | Migration |
|---|---|---|---|---|
| None | — | — | — | — |

The evidence lists no updates to records that existed before this repository's own migrations.

---

## 4. Dependencies

| Dependency | Scope | Purpose |
|---|---|---|
| `io.github.adempiere:base:3.9.4` | Compile/runtime | ADempiere core models, process engine, and infrastructure used by the processor classes |
| `io.github.adempiere:project:3.9.4` | Compile/runtime | Project processor models and services |
| `jakarta.ws.rs:jakarta.ws.rs-api:4.0.0` | Compile/runtime | JAX-RS client API used by dKron integration |
| `org.glassfish.jersey.core:jersey-client:4.0.0` | Compile/runtime | REST client implementation for dKron |
| `org.glassfish.jersey.inject:jersey-hk2:4.0.0` | Compile/runtime | Jersey HK2 injection support |
| `org.json:json:20250517` | Compile/runtime | JSON construction for dKron job definitions |
| `fileTree(dir: 'lib')` | Compile/runtime via `api` | Local jar dependencies; no files under `lib/` are listed in the tracked-file evidence |
| dKron HTTP API | Runtime/integration | External scheduler receiving job definitions and executing HTTP jobs |
| ADempiere backend service | Runtime/integration | Endpoint called by dKron; configured through `AD_AppRegistration` parameter `adempiere_endpoint` |
| Gradle wrapper 7.3.3 / JDK 17 | Build | Defines the build tool and target runtime |

Publishing credentials are supplied through environment/CI contexts such as `GITHUB_DEPLOY_USER`, `GITHUB_DEPLOY_TOKEN`, and signing-related Gradle properties. They must not be stored in the repository.

---

## 5. Consumers

- **ADempiere installations** consume the published Maven artifact `io.github.adempiere:adempiere-business-processors`. Changing the group, artifact, or publication repository breaks resolution.
- **ADempiere Application Dictionary** consumes the XML migrations. They create or update `AD_EntityType`, `AD_Process`, `AD_Menu`, `AD_Form`, `AD_AppSupport`, `AD_SetupDefinition`, and related records under `ECA46`.
- **ADempiere process engine** instantiates `org.spin.eca46.process.*` classes by classname from dictionary records. Renaming a class, changing a process value, or changing process parameter codes would break runtime process execution while still compiling as a library.
- **dKron** consumes job definitions created by `DKron.exportProcessor`. Changes to the job JSON shape, executor configuration, headers, or HTTP endpoint can break exporting installations even though the Java artifact compiles.
- **Downstream ADempiere services** may call the generated process values referenced by the README middleware/backend path. Changing process values or parameter codes can break these external integrations without a compile-time signal.

---

## 6. Allowed changes

Changes are permissible when they preserve the published artifact contract and the ADempiere dictionary contract.

- Add new `IProcessorEntity` wrappers for additional ADempiere processor types, following the existing pattern in `org.spin.eca46.util.support`.
- Add new `IExternalProcessor` implementations and register them through `AD_AppSupport` records in migrations with entity type `ECA46`.
- Extend `DKron` job-definition mapping while keeping the dKron API field names, `v1/jobs` path, and HTTP executor configuration compatible with existing installations.
- Add or adjust concrete process classes under `org.spin.eca46.process` when the corresponding generated `*Abstract` constants and migrations are updated together.
- Add migrations that create new `ECA46` dictionary records.
- Update dependency versions or build settings when compatible with Java 17 and the existing published artifact coordinates.
- Adjust CI/publish workflow configuration for the fork's own GitHub Packages registry.

---

## 7. Prohibited changes

- Do not add customer-specific behavior or a dependency on `PatchCustomer`; this repository must remain a reusable library.
- Do not modify pre-existing ADempiere dictionary records in migrations. The repository's own migrations are only to create or adjust `ECA46`-owned records.
- Do not commit secrets, credentials, tokens, signing keys, or password values. Such values belong in environment variables or CI secret stores, as the current publish workflow does.
- Do not change the published coordinates `io.github.adempiere:adempiere-business-processors` without a coordinated consumer migration.
- Do not change generated process values, names, IDs, or parameter codes in `*Abstract` classes without updating the dictionary migrations and downstream integration points in the same change.
- Do not remove or rename `IExternalProcessor` or `IProcessorEntity` without updating all implementers and consumers.
- Do not replace `ECA46` with another entity type for records this repository creates.
- Do not lower the Java target below 17 while the build and CI workflows specify JDK 17.

---

## 8. Architectural rules

1. Every dictionary record created by this repository's migrations must carry entity type `ECA46`.
2. Each concrete process class must remain paired with its generated `*Abstract` class, and the process value/name/ID constants must match the corresponding `AD_Process` and `AD_Process_Para` migration records.
3. Every external scheduler integration must implement `IExternalProcessor`; every processor exported to one must implement `IProcessorEntity`.
4. Credentials and tokens must be resolved at runtime from process parameters, `AD_AppRegistration` configuration, or environment/CI secrets — never as committed literals.
5. The published artifact must remain reusable and consumable as `io.github.adempiere:adempiere-business-processors`.

---

## 9. Risks

| Check | Finding | Impact | Precaution |
|---|---|---|---|
| Identifiers outside the allowed allocation range | Allowed allocation range for `ECA46` is not declared in `.ai/repository.yml` or migration evidence; created IDs are 1-, 2-, and 5-digit, none 7+ digits. | Cannot confirm allocation validity for release evaluation. | Declare the allowed identifier range in the repository marker or contract. |
| Build output or IDE metadata under version control | `.classpath`, `.project`, `.settings/`, and `.vscode/settings.json` are tracked. | Machine-specific editor/build state is committed; checkouts can be dirty and diffs noisy. | Remove these files from Git tracking and add them to `.gitignore`. |
| Secrets in the tree or recoverable from history | `None found`. The evidence shows only masked key names and environment references, not secret values. | No known exposure. | Keep credentials in CI secret stores and environment variables. |
| Absent verification mechanism | No test source files or test task are evidenced; CI runs `./gradlew build` only. | Defects can reach consumers without automated testing. | Use the release-candidate workflow and manual verification for behavior changes; consider adding tests. |
| Pre-existing records modified (cross-reference section 3) | `None`. | No release risk from pre-existing-record modifications. | Keep migrations limited to `ECA46`-owned records. |

| Risk | Impact | Precaution |
|---|---|---|
| `DKron.exportProcessor` checks `response.getStatus() != 201 \|\| response.getStatus() != 200`, which is true for every possible status. | Every dKron response, including success, is treated as an error path and read as an error result. | Correct the condition to `response.getStatus() != 201 && response.getStatus() != 200`. |
| `Request.getProcessorParameterId()` returns `processor.getR_RequestType_ID()` while its identifier and parameter code are based on `R_RequestProcessor_ID`. | Exported request-processor jobs may call the ADempiere backend with the wrong request type identifier. | Verify the intended request processor parameter and correct it before relying on request-processor export. |
| `Workflow.getProcessorParameterCode()` returns `RequestProcessor.R_REQUESTPROCESSOR_ID` instead of the workflow processor parameter code. | Exported workflow-processor jobs may send the wrong parameter code. | Verify and set the workflow processor parameter code to match the ADempiere process parameter. |
| `README.md` advertises Java 11, but `build.gradle` and CI target Java 17. | Users on Java 11 may attempt the library and fail at build or runtime. | Update the README requirements and Java badge to 17. |
| `CreateDKron` creates a default registration with host `http://localhost` and port `8080`. | A setup not adjusted after creation would export to a non-existent or wrong dKron instance. | Treat the created registration as a placeholder and set the real host/port in `AD_AppRegistration`. |
| Publish configuration relies on environment/secrets for GitHub Packages and signing. | Misconfigured secrets can cause publish failures or unsigned artifacts. | Configure `GITHUB_DEPLOY_TOKEN`, signing properties, and Sonatype properties in GitHub Actions secrets rather than files. |

---

## 10. Current state

The repository is a Java 17 library built with Gradle wrapper 7.3.3, published to the fork's own GitHub Packages under `io.github.adempiere:adempiere-business-processors`. It includes concrete process implementations for accounting, alert, project, request, scheduler, and workflow processors, plus `ExportInternalProcessors` for exporting them to dKron.

It provides eight sequential dictionary migrations under `xml/migration` using entity type `ECA46`, creating entity type, process, menu, form, application support, setup, parameter, and reference list records. The migration evidence records no modifications to pre-existing records.

Known current gaps and defects include: no evidenced automated test suite; the dKron response validation bug; request and workflow export parameter mismatches; README/badge Java mismatch; tracked IDE metadata; and a default localhost dKron registration placeholder.

---

## 11. UNKNOWN

- The allowed identifier range for `ECA46` is not declared in this repository's `.ai/repository.yml`, contract, or migration evidence.
- The contents of the `lib/` directory referenced by `build.gradle` are not listed in the evidence; whether it exists or contains jars could not be confirmed from the tracked files shown.
- Whether the request and workflow processor parameter code/value mismatches are intentional workarounds or defects is not documented; only the source inconsistency is visible.
- Whether the original `.ai/knowledge.md` exists inside this repository could not be confirmed from the supplied evidence.