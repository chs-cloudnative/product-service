#!/bin/bash
# ===================================================================
# Application Deployment Automation Script
# ===================================================================
# 用途：在全新的 Ubuntu 24.04 LTS 系統上自動設定應用環境/ 執行方式：sudo bash setup.sh
# -e: 遇到任何錯誤就立即停止執行/ -x: 顯示每個執行的指令（方便除錯
set -e

# Define output color
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'
echo_success() {
    echo -e "${GREEN}✓ $1${NC}"
}
echo_error() {
    echo -e "${RED}✗ $1${NC}"
}

# 強制要求用 sudo bash setup.sh 執行
if [ "$EUID" -ne 0 ]; then # if Effective_User_ID not equal to Root_ID
    echo_error "Use sudo to run the script"
    exit 1
fi

echo "====================================================================="
echo "Starting application environment setup"
echo "====================================================================="

echo "Step 1: Updating package lists..."
apt update -y # automatically update from repository and answer yes
echo_success "Package lists updated"


echo "Step 2: Upgrading system packages..."
DEBIAN_FRONTEND=noninteractive apt upgrade -y
echo_success "System packages upgraded"


echo "Step 3: Installing Java, Maven, Unzip and PostgreSQL..."
apt install -y openjdk-17-jdk maven unzip postgresql postgresql-contrib # postgresql-contrib: 一些額外的工具和擴充功能
java -version
mvn -version
echo_success "Java, Maven, Unzip and PostgreSQL installed"
systemctl start postgresql # start: 啟動服務
systemctl enable postgresql # enable: 設定開機自動啟動
echo_success "PostgreSQL service started"


echo "Step 4: Creating application database..."
DB_NAME="csye6225_db"
DB_USER="csye6225"
DB_PASSWORD="csye6225_password"
sudo -u postgres psql -c "CREATE DATABASE ${DB_NAME};" 2>/dev/null || echo "Database already exists, skipping" # 2>/dev/null: 只把錯誤訊息丟掉（不顯示）
sudo -u postgres psql -c "CREATE USER ${DB_USER} WITH PASSWORD '${DB_PASSWORD}';" 2>/dev/null || echo "User already exists, skipping"
sudo -u postgres psql -c "GRANT ALL PRIVILEGES ON DATABASE ${DB_NAME} TO ${DB_USER};" # 授予所有權限
sudo -u postgres psql -c "ALTER DATABASE ${DB_NAME} OWNER TO ${DB_USER};" # 讓這個使用者成為資料庫的擁有者（有完整控制權）
echo_success "Database ${DB_NAME} created"
echo_success "Database user ${DB_USER} created"


echo "Step 5: Creating application group..."
GROUP_NAME="csye6225"
if getent group ${GROUP_NAME} > /dev/null 2>&1; then # > /dev/null 2>&1: 把所有輸出（包括錯誤）都丟掉, getent: 查詢系統資料庫
    echo "Group ${GROUP_NAME} already exists, skipping"
else
    groupadd ${GROUP_NAME}
    echo_success "Group ${GROUP_NAME} created"
fi


echo "Step 6: Creating application user..."
USER_NAME="csye6225"
if id "${USER_NAME}" > /dev/null 2>&1; then
    echo "User ${USER_NAME} already exists, skipping"
else
    # -r: 創建系統使用者（system user），專門給服務使用，不是給人登入用的
    # -g: 指定群組
    # -s /usr/sbin/nologin: 設定 shell 為 nologin, 因為這個帳號只是用來跑應用程式，不需要登入功能
    # -m: 創建家目錄（home directory）即使不能登入，有些應用還是需要家目錄來存放設定檔
    useradd -r -g ${GROUP_NAME} -s /usr/sbin/nologin -m ${USER_NAME}
    echo_success "User ${USER_NAME} created"
fi


echo "Step 7: Deploying application files..."
APP_DIR="/opt/csye6225"
mkdir -p ${APP_DIR}
# 7-1 如果應用在運行，先停止避免檔案被占用
SERVICE_NAME="${USER_NAME}.service"
if systemctl is-active --quiet ${SERVICE_NAME} 2>/dev/null; then
    systemctl stop ${SERVICE_NAME}
    echo_success "Existing service stopped"
fi
# 7-2 檢查 ZIP 是否存在 and unzip
if [ ! -f "/tmp/webapp.zip" ]; then
    echo_error "ERROR: /tmp/webapp.zip not found! "
    echo "Please upload: scp webapp.zip ubuntu@server-ip:/tmp/webapp.zip"
    exit 1
fi
unzip -oq /tmp/webapp.zip -d ${APP_DIR}/
echo_success "Source code extracted"
# 7-3 複製.env 通常不會放在 Git 裡，需要單獨上傳
if [ -f "/tmp/.env" ]; then
    cp /tmp/.env ${APP_DIR}/
    echo_success "Environment file copied"
fi
# 7-4 設定owner build project and verify
chown -R ${USER_NAME}:${GROUP_NAME} ${APP_DIR} # chown: change owner, -R: recursive處理所有子目錄和檔案
cd ${APP_DIR}
if [ -f ./mvnw ]; then # 專案裡有 mvnw/mvnw.cmd 就可以用 Maven Wrapper
    chmod +x mvnw # 給 mvnw 執行權限
    su -s /bin/bash -c "./mvnw clean package -DskipTests" ${USER_NAME} # -u ${USER_NAME}: 以指定使用者執行, package: 打包成 JAR, -DskipTests: 跳過測試（加快編譯速度）
    echo_success "Build completed with Maven Wrapper"
else # 如果沒有 mvnw，使用系統安裝的 Maven
    su -s /bin/bash -c "mvn clean package -DskipTests" ${USER_NAME} # 需要確保 Step 3 有安裝 Maven
    echo_success "Build completed with system Maven"
fi
# 7-5 Verify build output
JAR_FILE=$(find ${APP_DIR}/target -name "*.jar" -type f ! -name "*original*" 2>/dev/null | head -1) # 在 target/ 目錄尋找編譯好的 JAR 檔案, -type f: 只找一般檔案（不包括目錄, ! -name "*original*": 排除 Spring Boot 的備份檔案）
if [ -z "$JAR_FILE" ]; then # -z: 檢查字串是否為空
    echo_error "ERROR: Build failed - no JAR file found in target/"
    exit 1
fi
echo_success "Build successful: $(basename $JAR_FILE). JAR file location: ${JAR_FILE}"


echo "Step 8: Setting file permissions..."
chown -R ${USER_NAME}:${GROUP_NAME} ${APP_DIR} # Step 7 編譯時會產生新檔案, 需要再次確認擁有者
chmod -R 755 ${APP_DIR}
# 讀(r) = 4 寫(w) = 2 執行(x) = 1, 755：
# 7 = 4+2+1 = rwx（擁有者可以讀、寫、執行）
# 5 = 4+1 = r-x（群組可以讀、執行，不能寫）
# 5 = 4+1 = r-x（其他人可以讀、執行，不能寫）
echo_success "File permissions set"
echo_success "Owner: ${USER_NAME}:${GROUP_NAME}"
echo_success "Permissions: 755"


echo "====================================================================="
echo_success "Environment setup completed!"
echo "====================================================================="
echo "Database Information:"
echo "  - Database Name: ${DB_NAME}"
echo "  - Database User: ${DB_USER}"
echo "  - Database Password: ${DB_PASSWORD}"
echo ""
echo "Application Information:"
echo "  - Application Directory: ${APP_DIR}"
echo "  - Application User: ${USER_NAME}"
echo "  - Application Group: ${GROUP_NAME}"
echo "====================================================================="


# ===================================================================
# DOCUMENTATION: Step 7 詳細說明
# ===================================================================
# 本區塊為 Step 7 的完整註解說明，供日後複習參考
#
# Step 7 目的：
# 從原始碼部署並編譯 Spring Boot 應用程式
#
# ===================================================================
# 7.1 創建應用目錄
# ===================================================================
# APP_DIR="/opt/csye6225"
# mkdir -p ${APP_DIR}
#
# 說明：
# - /opt/ 是 Linux 存放第三方應用的標準目錄
# - mkdir -p: 如果目錄已存在不會報錯，會創建所有必要的父目錄
#
# ===================================================================
# 7.2 停止現有服務
# ===================================================================
# SERVICE_NAME="${USER_NAME}.service"
# if systemctl is-active --quiet ${SERVICE_NAME} 2>/dev/null; then
#     systemctl stop ${SERVICE_NAME}
# fi
#
# 說明：
# - systemctl: Linux 的服務管理工具
# - is-active --quiet: 安靜模式檢查服務是否運行
# - 2>/dev/null: 把錯誤訊息丟掉（如果服務不存在）
# - 為什麼要停止：避免部署時檔案被占用
#
# ===================================================================
# 7.3 檢查 ZIP 檔案
# ===================================================================
# if [ ! -f "/tmp/webapp.zip" ]; then
#     echo_error "ERROR: /tmp/webapp.zip not found!"
#     exit 1
# fi
#
# 說明：
# - [ ! -f "路徑" ]: 檢查檔案是否「不」存在
# - -f: file，檢查是否為一般檔案
# - exit 1: 以錯誤狀態退出（1 是錯誤碼）
#
# 前置步驟（在執行腳本前需手動完成）：
# 1. 在 Mac 上打包：
#    cd ~/webapp
#    zip -r webapp.zip . -x '*.git*' 'target/*' '*.DS_Store'
#
# 2. 上傳到伺服器：
#    scp webapp.zip ubuntu@server-ip:/tmp/webapp.zip
#
# ===================================================================
# 7.4 解壓縮原始碼
# ===================================================================
# unzip -oq /tmp/webapp.zip -d ${APP_DIR}/
#
# 說明：
# - unzip: 解壓縮指令
# - -o: overwrite，覆蓋現有檔案（不詢問）
# - -q: quiet，安靜模式，減少輸出訊息
# - -d: destination，指定解壓縮目標目錄
#
# 結果：
# /opt/csye6225/
# ├── src/
# ├── pom.xml
# ├── mvnw
# └── ... (所有原始碼)
#
# ===================================================================
# 7.5 複製環境變數檔案
# ===================================================================
# [ -f "/tmp/.env" ] && cp /tmp/.env ${APP_DIR}/
#
# 說明：
# - [ -f "路徑" ]: 檢查檔案是否存在
# - &&: 邏輯 AND，前面成功才執行後面
# - 整句意思：如果 .env 存在，就複製它
#
# .env 檔案用途：
# - 存放敏感資訊（資料庫密碼、API keys）
# - 不會放在 Git 裡（.gitignore 會排除）
# - 需要單獨上傳到伺服器
#
# 範例 .env 內容：
# DB_HOST=localhost
# DB_PORT=5432
# DB_NAME=csye6225_db
# DB_USER=csye6225
# DB_PASSWORD=csye6225_password
#
# ===================================================================
# 7.6 設定目錄擁有者
# ===================================================================
# chown -R ${USER_NAME}:${GROUP_NAME} ${APP_DIR}
#
# 說明：
# - chown: change owner，變更檔案擁有者
# - -R: recursive，遞迴處理所有子目錄和檔案
# - ${USER_NAME}:${GROUP_NAME}: 格式為「使用者:群組」
# - 例如：csye6225:csye6225
#
# 為什麼要這樣做：
# - 編譯需要寫入權限（產生 target/ 目錄）
# - 應用使用者需要能讀寫自己的檔案
# - 安全原則：不用 root 執行應用
#
# ===================================================================
# 7.7 編譯應用程式
# ===================================================================
# cd ${APP_DIR}
#
# if [ -f ./mvnw ]; then
#     chmod +x mvnw
#     su -s /bin/bash -c "./mvnw clean package -DskipTests" ${USER_NAME}
# else
#     su -s /bin/bash -c "mvn clean package -DskipTests" ${USER_NAME}
# fi
#
# 說明：
#
# Maven Wrapper (mvnw)：
# - 專案自帶的 Maven 執行檔
# - 優點：確保版本一致，不依賴系統 Maven
# - 如果專案有 mvnw，優先使用它
#
# chmod +x mvnw：
# - 給 mvnw 執行權限
# - +x: 加上 execute（執行）權限
#
# su 指令：
# - su: switch user，切換使用者
# - -s /bin/bash: 指定使用 bash shell
# - -c "指令": 執行單一指令
# - ${USER_NAME}: 目標使用者（csye6225）
# - 整句意思：以 csye6225 使用者身份執行編譯
#
# Maven 指令：
# - clean: 清理之前的編譯結果（刪除 target/ 目錄）
# - package: 編譯並打包成 JAR
# - -DskipTests: 跳過測試（加快編譯速度）
#
# 編譯流程：
# 1. 下載依賴套件（從 Maven Central Repository）
# 2. 編譯 Java 原始碼（.java → .class）
# 3. 執行測試（我們用 -DskipTests 跳過）
# 4. 打包成 JAR（所有 .class 和資源打包成單一檔案）
#
# 編譯結果：
# target/
# ├── webapp-0.0.1-SNAPSHOT.jar          ← 可執行的 JAR
# ├── webapp-0.0.1-SNAPSHOT.jar.original ← 原始 JAR（備份）
# └── classes/ ...
#
# ===================================================================
# 7.8 驗證編譯結果
# ===================================================================
# JAR_FILE=$(find ${APP_DIR}/target -name "*.jar" -type f ! -name "*original*" 2>/dev/null | head -1)
#
# if [ -z "$JAR_FILE" ]; then
#     echo_error "Build failed - no JAR file found"
#     exit 1
# fi
#
# 說明：
#
# find 指令拆解：
# - find ${APP_DIR}/target: 在 target/ 目錄搜尋
# - -name "*.jar": 找所有 .jar 結尾的檔案
# - -type f: 只找一般檔案（file），不包括目錄
# - ! -name "*original*": 排除檔名含有 original 的檔案
# - 2>/dev/null: 把錯誤訊息丟掉
# - | head -1: 只取第一個結果
#
# 為什麼排除 original：
# - Spring Boot 打包時會產生兩個 JAR：
#   1. webapp-0.0.1-SNAPSHOT.jar (可執行，包含所有依賴)
#   2. webapp-0.0.1-SNAPSHOT.jar.original (原始檔，不可執行)
# - 我們只要第一個
#
# $(...) 語法：
# - 命令替換（command substitution）
# - 執行括號內的指令，把結果存入變數
# - 例如：JAR_FILE=$(find ...) 會把找到的路徑存入 JAR_FILE
#
# [ -z "$JAR_FILE" ]：
# - -z: zero length，檢查字串是否為空
# - 如果為空，表示沒找到 JAR（編譯失敗）
#
# basename 指令：
# - 從完整路徑取得檔案名稱
# - 例如：/opt/csye6225/target/webapp.jar → webapp.jar
#
# ===================================================================
# Step 7 完整流程圖
# ===================================================================
#
#     開始
#       ↓
#   創建目錄 /opt/csye6225/
#       ↓
#   停止現有服務（如果運行中）
#       ↓
#   檢查 /tmp/webapp.zip 是否存在？
#       ↓ 是
#   解壓縮到 /opt/csye6225/
#       ↓
#   複製 .env（如果存在）
#       ↓
#   設定擁有者為 csye6225:csye6225
#       ↓
#   切換到應用目錄
#       ↓
#   檢查是否有 mvnw？
#    ↙是    ↘否
# 用 mvnw   用 mvn
#    ↘      ↙
#   執行編譯（clean package）
#       ↓
#   在 target/ 尋找 JAR 檔案
#       ↓
#   找到了嗎？
#    ↙否    ↘是
# 報錯退出  顯示成功
#       ↓
#     完成
#
# ===================================================================
# 常見問題與解決方案
# ===================================================================
#
# Q1: 編譯失敗，顯示 "command not found: mvn"
# A1: Step 3 沒有正確安裝 Maven
#     解決：apt install -y maven
#
# Q2: 編譯失敗，顯示 "JAVA_HOME not set"
# A2: Java 沒有正確安裝或環境變數未設定
#     解決：apt install -y openjdk-17-jdk
#
# Q3: 找不到 JAR 檔案
# A3: 編譯過程有錯誤（往上看編譯日誌）
#     可能原因：
#     - pom.xml 有錯誤
#     - 依賴套件下載失敗
#     - 程式碼語法錯誤
#
# Q4: Permission denied 錯誤
# A4: 檔案權限不正確
#     解決：chown -R csye6225:csye6225 /opt/csye6225/
#
# Q5: 解壓縮失敗
# A5: ZIP 檔案損壞或不完整
#     解決：重新上傳 webapp.zip
#
# ===================================================================
# 安全性考量
# ===================================================================
#
# 1. 為什麼用專用使用者編譯？
#    - 避免用 root 執行（最小權限原則）
#    - 如果編譯有問題，不會影響整個系統
#
# 2. 為什麼 .env 不放在 Git？
#    - 包含敏感資訊（密碼、API keys）
#    - 如果上傳到 GitHub 會洩漏
#    - 每個環境的設定可能不同
#
# 3. 為什麼跳過測試？
#    - 加快編譯速度
#    - 測試應該在 CI/CD 階段執行
#    - 部署環境可能沒有測試所需的資源
#
# ===================================================================