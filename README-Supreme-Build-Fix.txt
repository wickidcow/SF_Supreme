Supreme Legacy build fix

Replace this file in your repository:
src/main/java/com/github/relativobr/supreme/machine/MobCollector.java

Fix:
- Replaces the removed direct EntityType.SNOWMAN reference.
- Recognizes both the older SNOWMAN enum name and the newer SNOW_GOLEM enum name.
- Preserves compile compatibility across older Slimefun/Paper forks and modern Paper 1.21.11 / 26.x.

After replacing the file, commit it and rerun the build-legacy GitHub Action.
