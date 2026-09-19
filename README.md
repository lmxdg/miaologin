MiaoLogin 登录插件



一款支持多语言、带箱子 GUI 界面、功能全面的 Minecraft 登录插件，兼容 Spigot / Paper / Purpur 1.7 - 26.2。



简介



MiaoLogin（喵登录）是一款轻量、安全、易配置的登录插件。玩家进服即可看到欢迎语和服务器规则，通过箱子界面点击按钮完成注册、登录或重置密码，全程在聊天框输入即可，无需记忆复杂命令。内置多语言支持，自动识别玩家客户端语言，文字都以"喵"结尾，可爱又贴心。



主要功能



- 注册 / 登录 / 重置密码：完整的账户生命周期管理，带箱子 GUI 界面

- 欢迎语 + 服务器规则：玩家进服自动显示，文字全部可在 text.yml 中自由修改

- 多语言支持：简体中文 / 繁体中文 / 英语 / 日语，按客户端语言自动选择

- 密码安全：SHA-256 加盐哈希存储，聊天输入自动隐藏，防止密码泄露

- 登录保护：登录超时自动踢出、密码错误次数上限自动踢出（参数可调）

- 命令 / 聊天拦截：未登录玩家无法使用命令和聊天（白名单可配置）

- AuthMe 自动接管：检测到 AuthMe 自动启用，玩家可用原密码直接登录，账户自动迁移

- 双存储后端：SQLite 数据库 / YAML 文件，自由切换



管理命令



/miaologin reload 重新加载配置，权限：miaologin.admin.reload

/miaologin info <玩家> 查看玩家账户信息，权限：miaologin.admin.info

/miaologin reset <玩家> 重置玩家密码，权限：miaologin.admin.reset

/miaologin unregister <玩家> 删除玩家账户，权限：miaologin.admin.unregister

/miaologin list 查看已注册玩家数量，权限：miaologin.admin.list

/miaologin takeover 查看 AuthMe 接管状态，权限：miaologin.admin.takeover

miaologin.bypass 权限：跳过登录验证，适用于管理员，默认 OP 拥有

miaologin.admin 权限：允许使用所有管理命令，默认 OP 拥有



兼容性



- 服务器类型：Spigot / Paper / Purpur

- 版本范围：1.7 - 26.2

- 多语言：简体中文 / 繁体中文 / 英语 / 日语

- 不支持 Folia



部署方法



1. 将下载的jar文件上传到服务器的 plugins 目录

2. 重启服务器

3. 按需修改 plugins/MiaoLogin/ 下的 text.yml（欢迎语）、config.yml（登录配置）

4. 从旧版本升级，配置文件和账户数据完全保留，无需迁移

欢迎加入猫猫的交流群：1041398736 喵~
