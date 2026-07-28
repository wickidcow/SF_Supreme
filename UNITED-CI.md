# Slimefun United compatibility check

The United workflow builds the current `dev` branch, locates its compiled API jar, and installs that jar into Maven under the CI-only version `4.10-CI`.

This intentionally avoids asking JitPack for `4.10-SNAPSHOT`; that coordinate is produced by the checked-out United source and is not a public JitPack artifact.

For a local check:

```bash
# Build United first, then install its main jar as 4.10-CI.
mvn -Punited -Dslimefun.version=4.10-CI -DskipTests clean package
```
