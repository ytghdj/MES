# MES GitHub 同步

仓库：`git@github.com:ytghdj/MES.git`，主分支：`main`。
前后端统一管理，在项目根目录执行以下操作。

## 开始开发前更新

工作区没有未提交改动时执行：

```powershell
git pull --ff-only
```

## 提交并上传

```powershell
git status
git add .
git commit -m "update 描述本次修改"
git push
```

推送提示远端有新提交时，先用 `git pull --rebase` 整合，再执行 `git push`。

## 在其他电脑获取项目

```powershell
git clone git@github.com:ytghdj/MES.git
```

依赖、构建产物、日志和本机配置不会上传。数据库密码通过环境变量
`MES_DB_PASSWORD`，或本机的 `ruoyi-admin/src/main/resources/application-local.yml`
设置；后者已加入忽略规则，可按以下结构创建：

```yaml
spring:
  datasource:
    dynamic:
      datasource:
        master:
          password: "填写本机数据库密码"
```

本机原前端仓库历史保留在 `.git/local-backups/plus-ui.git`，不上传。
原后端提交保留在本地 `refs/local-backups/before-github-upload`，不上传。
