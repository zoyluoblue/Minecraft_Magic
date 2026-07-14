# Release 流程

本模组使用 Git tag 触发 GitHub Actions 构建并创建 Release。

1. 在 `gradle.properties` 更新 `mod_version`。
2. 本地执行构建验证：

   ```bash
   ./gradlew build
   ```

3. 提交并推送 `main`。
4. 创建并推送版本 tag：

   ```bash
   git tag vX.Y.Z
   git push origin vX.Y.Z
   ```

推送 `v*` tag 后，Release workflow 会使用 Java 21 构建，并把 `build/libs/` 下的模组 JAR 和 sources JAR 上传到 GitHub Release。
