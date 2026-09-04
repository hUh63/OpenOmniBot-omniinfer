
喂饭版本，跟着我做，如果手机型号相同99.999...%成功，因为🕳️我已经踩烂了。本文基于 **Termux + proot-distro (Ubuntu)** 方案，无需 Root。我用的是 pixel6 (8g+128g)。
---

## 一、准备工作

### 1.1 核心组件一览

| 组件 | 作用 | 是否必须 |

|---|---|---|

| **Termux** | 安卓终端模拟器，所有程序的"地基" | 必须 |

| **Termux:API** | 让 OpenClaw 调用手机摄像头、麦克风等硬件的桥梁 | 可选（不需要硬件调用可跳过） |

| **Ubuntu (proot-distro)** | 在 Termux 内运行完整 Ubuntu 系统，解决兼容性问题 | 必须 |

**为什么不直接用 Termux 原生环境跑 OpenClaw？**

原生 Termux 是安卓环境（Bionic Libc），很多 AI 依赖库（如 PyTorch、Numpy、bun）无法直接安装或报错。在 Ubuntu 容器里跑 OpenClaw 兼容性最好。

### 1.2 手机要求

- Android 11 及以上版本

- 能跑安卓系统的手机都行

- Termux 耗电量不大，基本排名靠后

---

## 二、安装 Termux

### 2.1 下载安装

GitHub 官方下载地址：https://github.com/termux/termux-app/releases/tag/v0.118.3

**版本选择**：根据手机 CPU 架构选择对应的 APK。不确定的话问 AI，告诉它你的手机型号即可。例如 Redmi Turbo 5 Max 选择 `termux-app_v0.118.3+github-debug_arm64-v8a.apk`。

> **注意**：不要从 Google Play 下载，该渠道早已停更。后续安装的 Termux:Boot、Termux:API 等插件也必须从同一渠道下载，否则会因签名不一致导致无法工作。

### 2.2 安装后配置权限

安装完成后，进入手机 **设置** → **应用管理** → **Termux**，开启以下权限：

- 后台弹出界面

- 自启动

- 电池策略设为"无限制"

### 2.3 安装 Termux:API（可选）

> 如果不需要 OpenClaw 调用摄像头、麦克风等手机硬件，可跳过此节。

下载地址：https://github.com/termux/termux-api/releases/tag/v0.53.0

下载安装 `termux-api-app_v0.53.0+github.debug.apk` 后，手动授予权限：

**设置** → **应用设置** → **应用管理** → 搜索 **Termux:API** → **权限管理** → 将相机、麦克风、位置信息、存储等权限全部改为"始终允许"或"仅在使用中允许"。

调用链路：`OpenClaw (代码)` → `Termux 终端命令` → `Termux:API (App)` → `安卓系统` → `硬件`

### 2.4 更新系统并安装核心组件

打开 Termux，执行：

```bash

pkg update -y && pkg upgrade -y

# 安装 Ubuntu 管理器（必须）和 API 桥接工具（可选）

pkg install proot-distro -y

# 如果安装了 Termux:API APP，还需安装命令行端接口

pkg install termux-api -y

```

---

## 三、安装并进入 Ubuntu

### 3.1 一键安装 Ubuntu

```bash

proot-distro install ubuntu

```

系统会自动下载 Ubuntu 文件系统（Rootfs）并解压配置，通常 1-3 分钟。

### 3.2 登录 Ubuntu

```bash

proot-distro login ubuntu

```

观察提示符变化：

- 登录前（Termux）：`~ $`

- 登录后（Ubuntu）：`root@localhost:~#`

看到 `root@localhost` 就说明已进入 Ubuntu 系统。

### 3.3 设置快捷指令（可选）

每次输入 `proot-distro login ubuntu` 太长，可以设置别名：

```bash

# 先退回 Termux（输入 exit）

exit

# 在 Termux 配置文件里加别名

echo "alias u='proot-distro login ubuntu'" >> ~/.bashrc

source ~/.bashrc

```

以后在 Termux 中输入 `u` 即可直接进入 Ubuntu。

---

## 四、Ubuntu 内部环境配置

进入 Ubuntu 后，需要安装 Node.js 和编译工具（OpenClaw 基于 Node.js 开发）。

### 4.1 更新软件源

```bash

apt update && apt upgrade -y

```

### 4.2 安装基础工具

```bash

apt install curl git build-essential python3 -y

```

### 4.3 安装 Node.js

Ubuntu 默认仓库中的 Node.js 版本较旧，需要添加官方源：

```bash

# 添加 Node.js 24.x 官方源

curl -fsSL https://deb.nodesource.com/setup_24.x | bash -

# 正式安装

apt install nodejs -y

```

### 4.4 验证安装

```bash

node -v

npm -v

```

输出版本号（如 v24.x.x）即为成功。

---

## 五、安装 OpenClaw 并解决兼容性问题

### 5.1 安装 OpenClaw

```bash

npm install -g openclaw

```

> **不要直接运行 `openclaw onboard`！** 会触发 SystemError 13 导致崩溃。需要先打补丁。

### 5.2 理解 SystemError 13

**根因**：Android 10+ 的隐私保护机制封锁了普通应用读取 `/proc/net/` 的权限。Node.js 启动时会调用 `uv_interface_addresses` 函数尝试读取网络接口信息（IP、MAC 地址等），被安卓内核拦截后抛出 SystemError 13 并崩溃。

**解决思路**：创建一个 JS 补丁，拦截 `os.networkInterfaces()` 调用，在读取失败时返回伪造的本地回环地址，从而绕过系统限制。

### 5.3 创建补丁文件

```bash

mkdir -p /root/.openclaw

nano /root/.openclaw/bionic-bypass.js

```

在编辑器中粘贴以下代码：

```javascript

const os = require('os');

const originalNetworkInterfaces = os.networkInterfaces;

os.networkInterfaces = function() {

try {

const interfaces = originalNetworkInterfaces.call(os);

if (interfaces && Object.keys(interfaces).length > 0) {

return interfaces;

}

} catch (e) {}

return {

lo: [{

address: '127.0.0.1',

netmask: '255.0.0.0',

family: 'IPv4',

mac: '00:00:00:00:00:00',

internal: true,

cidr: '127.0.0.1/8'

}]

};

};

```

保存退出：`Ctrl+X`→ y → 回车 

### 5.4 配置环境变量（让补丁永久生效）

告诉 Node.js 每次启动前先加载补丁：

```bash

echo 'export NODE_OPTIONS="--require /root/.openclaw/bionic-bypass.js"' >> ~/.bashrc

source ~/.bashrc

```

### 5.5 启动 OpenClaw

```bash

# 初始化配置

openclaw onboard

```

配置完成后界面会卡在 `completed`，按 `Ctrl+C` 退出，然后启动网关服务：

```bash

openclaw gateway --verbose

```

---

## 六、开机自启配置

由于 proot-distro 内没有 `systemd`，手机重启后 Termux、Ubuntu 和 OpenClaw 都不会自动运行。通过 Termux:Boot 插件可实现全自动启动。

### 6.1 安装 Termux:Boot

1. 从下载 Termux 的**同一渠道**下载安装 **Termux:Boot**
> 由于我的Termux是从GIthub官方下载的，那么Termux:Boot
也要从GitHub下载，这里：https://github.com/termux/termux-boot/releases/tag/v0.8.1

2. 安装后在手机桌面**点击打开一次**（注册开机自启权限）

3. 在手机 **设置** → **应用管理** 中，将 Termux 和 Termux:Boot 的电池策略设为"无限制"

### 6.2 创建开机启动脚本

在**外层 Termux**（不要进入 Ubuntu）执行：

```bash

mkdir -p ~/.termux/boot

nano ~/.termux/boot/start-openclaw.sh

```

粘贴以下脚本内容：

```bash

#!/data/data/com.termux/files/usr/bin/sh

# 强制加载 Termux 全局环境变量

source /data/data/com.termux/files/usr/etc/profile

# 开启唤醒锁，防止休眠时杀进程

termux-wake-lock

# 启动外层 Termux SSH（可选，方便电脑远程管理）

sshd

# 缓冲 5 秒，等待网络和文件系统就绪

sleep 5

# 启动内层 Ubuntu SSH（端口 2222）
# 使用 -D 让 sshd 在 Ubuntu 内前台运行，防止 Proot 容器因"无前台进程"而自动关闭
# 在 Termux 层用 nohup + & 将整个 proot 进程放入后台
nohup proot-distro login ubuntu -- /usr/sbin/sshd -D > /data/data/com.termux/files/home/ubuntu-sshd.log 2>&1 &

# 如果需要后台启动其他应用（如 OpenClaw），同理：
# 同样在 Ubuntu 内前台运行，在 Termux 层后台挂起
nohup proot-distro login ubuntu -- bash -lc "export NODE_OPTIONS=\"--require /root/.openclaw/bionic-bypass.js\"; openclaw gateway --verbose" > /data/data/com.termux/files/home/ubuntu-openclaw.log 2>&1 &

```

保存退出后赋予执行权限：

```bash

chmod +x ~/.termux/boot/start-openclaw.sh

```

### 6.3 验证脚本

无需重启手机，直接在 Termux 中模拟运行：

```bash

bash ~/.termux/boot/start-openclaw.sh

```

等待几秒后，进入 Ubuntu 检查进程：

```bash

proot-distro login ubuntu

ps aux | grep [o]penclaw

```

如果能看到 `openclaw` 和 `openclaw-gateway` 进程，说明自启脚本工作正常。

---

## 七、日常使用技巧

### 7.1 从电脑远程操作

如果觉得在手机上操作不便，可通过 SSH 从电脑连接到手机：

```bash

# 手机 Termux 中安装 SSH

pkg install openssh && passwd && sshd

# 查看用户名和 IP

whoami && ifconfig

# 电脑上连接

ssh <用户名>@<手机IP> -p 8022

```

### 7.2 在电脑浏览器中打开 Dashboard

OpenClaw 自带 Web 控制面板。在 Ubuntu 中运行：

```bash

openclaw dashboard

```

终端会输出类似以下信息：

```

Dashboard URL: http://127.0.0.1:18789/#token=a10838e85317...

No GUI detected. Open from your computer:

ssh -N -L 18789:127.0.0.1:18789 root@192.168.1.29

```

由于 Dashboard 默认只绑定 `127.0.0.1`（本机），无法通过局域网 IP 直接访问，需要使用 **SSH 隧道（SSH Tunneling）** 把手机内部端口映射到电脑本地。

**步骤 1：在电脑上建立 SSH 隧道**

在电脑上**新开一个终端窗口**，执行：

```bash

ssh -N -L 18789:127.0.0.1:18789 root@<手机IP> -p 2222

```

> **注意**：OpenClaw 提示的命令没有 `-p 2222`，因为它不知道我们改了 SSH 端口。必须手动加上。

输入密码后终端会卡住不动，这是正常的（`-N` 参数表示"只建隧道，不打开命令行"）。保持这个窗口不要关闭。

**步骤 2：在电脑浏览器中访问**

打开浏览器，粘贴 Dashboard 输出的**带 token 的完整 URL**：

```

http://localhost:18789/#token=<OPENCLAW_SESSION_TOKEN>

```

> URL 末尾的 `#token=...` 是一次性安全令牌，每次启动 Dashboard 都会重新生成。没有 token 将无法访问控制面板。

### 7.3 后台运行与日志监控

```bash

# 后台运行 OpenClaw（手动启动时使用）

nohup openclaw gateway --verbose > /root/openclaw.log 2>&1 &

# 实时查看日志

tail -f /root/openclaw.log

# 查看进程状态

ps aux | grep [o]penclaw

# 终止进程

pkill -f openclaw

```

## 附录：Q&A

### Q1：为什么会报 `Systemd user services are unavailable`？

Termux 的 proot-distro 只是在安卓上模拟的 Linux 环境，没有真实内核权限，无法运行 `systemd`。所有依赖 `systemctl` 的自动启动功能在此环境中均不可用。解决方案见第六章（Termux:Boot 开机自启）。

### Q2：`openclaw onboard --install-daemon` 为什么失效？

`--install-daemon` 底层依赖 `systemd` 注册守护进程，而 proot 环境没有 systemd，所以直接失效。替代方案是使用 `nohup openclaw gateway --verbose > /root/openclaw.log 2>&1 &` 实现后台运行。

### Q3：通过 SSH 连接时，Ubuntu 和 Termux 的 IP 一样吗？

一样。proot-distro 不会创建独立的虚拟网卡，Ubuntu 容器与 Termux、安卓手机共享同一个 IP 地址。区分连接目标靠端口：`8022` = Termux 外层，`2222` = Ubuntu 内层（需在 Ubuntu 内单独配置 SSH）。

### Q4：每次重启手机都要手动启动服务吗？

是的，除非你配置了 Termux:Boot 开机自启脚本（见第六章）。Termux 被杀后台或手机重启后，Ubuntu 内所有服务都会停止。

### Q5：能直接用局域网 IP 访问 Dashboard 吗？

通常不行。OpenClaw Dashboard 默认只绑定 `127.0.0.1`（本机回环地址），不监听局域网。即使在电脑浏览器输入 `http://192.168.x.x:18789` 也会被拒绝。必须通过 SSH 隧道将端口映射到电脑本地后，用 `http://localhost:18789` 访问。