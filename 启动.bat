@echo off
chcp 65001 >nul 2>&1
title PentaMind 启动器

set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot"
set "MVN=E:\apache-maven-3.9.8\bin\mvn.cmd"
set "BACKEND_DIR=%~dp0platform\backend"
set "FRONTEND_DIR=%~dp0platform\frontend"

echo ============================================
echo   PentaMind 智能渗透测试平台 启动中...
echo ============================================
echo.

echo [1/4] 清理旧进程...
taskkill /IM java.exe /F >nul 2>&1
for /f "tokens=5" %%a in ('netstat -ano 2^>nul ^| findstr ":5173 "') do taskkill /PID %%a /F >nul 2>&1
echo     完成.
echo.

echo [2/4] 检查前端依赖...
if not exist "%FRONTEND_DIR%\node_modules" (
    echo     首次运行，安装依赖中，请稍等...
    pushd "%FRONTEND_DIR%"
    call npm install
    popd
)
echo     OK.
echo.

echo [3/4] 启动后端 (Spring Boot:8080)...
start "PentaMind Backend" cmd /k "set JAVA_HOME=%JAVA_HOME% && cd /d "%BACKEND_DIR%" && "%MVN%" clean compile -q spring-boot:run || (echo. && echo [错误] 后端启动失败，按任意键退出 && pause)"
echo     等待后端初始化 (20秒)...
timeout /t 20 /nobreak >nul
echo.

echo [4/4] 启动前端 (Vite:5173)...
start "PentaMind Frontend" cmd /k "cd /d "%FRONTEND_DIR%" && npm run dev"
echo     等待前端构建 (6秒)...
timeout /t 6 /nobreak >nul
echo.

echo ============================================
echo   启动完成！
echo   前端: http://localhost:5173
echo   后端: http://localhost:8080
echo   
echo   服务运行在独立窗口，本窗口可关闭
echo ============================================

start "" "http://localhost:5173"

echo.
echo 按任意键关闭启动器...
pause >nul
