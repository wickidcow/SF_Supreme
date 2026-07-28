# Validation Notes

## Completed offline checks

- Compared the original English Supreme source, Gugu-oriented Supreme source, Supreme Legacy fork, Slimefun 5 ideas branch, and the supplied Slimefun Legacy API source.
- Confirmed the supplied Supreme English and Supreme Legacy archives began from the same codebase.
- Validated `pom.xml` as XML.
- Validated `plugin.yml`, `config.yml`, and the GitHub Actions workflow as YAML.
- Checked all Java sources for parser-level syntax diagnostics with Java 21.
- Checked the source tree for removed Spring and external GuizhanLib imports.
- Checked that the modified legacy Slimefun classes/methods referenced by the patch exist in the supplied Slimefun Legacy source.

## Environment limitation

A complete Maven/Gradle dependency build could not be executed in the artifact sandbox because outbound dependency downloads and the Gradle distribution host were unavailable. The included GitHub Actions workflow performs the authoritative build against Slimefun Legacy and compile checks against the other API targets.
