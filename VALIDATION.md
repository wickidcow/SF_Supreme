# Validation Notes

## Completed offline checks

- Compared the original English Supreme source, Gugu-oriented Supreme source, Supreme Legacy fork, Slimefun 5 ideas branch, and the supplied Slimefun Legacy API source.
- Confirmed the supplied Supreme English and Supreme Legacy archives began from the same codebase.
- Validated `pom.xml` as XML.
- Validated `plugin.yml`, `config.yml`, and the GitHub Actions workflow as YAML.
- Checked all Java sources for parser-level syntax diagnostics with Java 21.
- Checked the source tree for removed Spring and external GuizhanLib imports.
- Checked that the modified legacy Slimefun classes/methods referenced by the patch exist in the supplied Slimefun Legacy source.
- Compiled the two compatibility adapters with Java 21 against API stubs where `Config` is marked for removal; the compile completed with zero warnings.
- Confirmed all `Config` imports are isolated to `com.github.relativobr.supreme.compat`.
- Confirmed no machine class directly creates an anonymous `BlockTicker`.

## Environment limitation

A complete dependency build could not be executed in the artifact sandbox because Maven is not preinstalled and outbound package resolution is unavailable. The included GitHub Actions workflow performs the authoritative build against Slimefun Legacy and compile checks against the other API targets.
