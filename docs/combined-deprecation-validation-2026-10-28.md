# Supreme combined build/deprecation candidate

Validation-only source combining:
- Maven upgrade #26: cb0e656b9a9e1eadbc51be024c7f7d41087053eb
- Adventure action-bar implementation and two tests #27: 4b402337677974feccaa163bb61130b4cb303bbe
- Display-only PluginMeta accessor #28: caaf67feea3ed53ed57dcfaada4c1e38d1c97ff4

Donor Java/test files are reused by Git blob identity. The existing full API matrix remains unchanged. A separate read-only workflow tests the combined Supreme source against exact core candidate f505b9f79e8e4598eedf1160b0831dbec758eb78 on the Java 21 release baseline and checks actual test execution and absence of shaded core/test dependencies.

Require exact-head results; prior individually green PRs are not combined validation. No saved lore, items, machine state, rates, recipes, IDs, versions or publishing guards are changed. Compilation/tests are not proof of live-world recovery or Folia runtime behavior. This draft does not authorize merges, releases or production deployment.
