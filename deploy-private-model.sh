#!/bin/bash
# 私有化模型快速部署脚本

set -e

echo "🚀 开始私有化模型部署准备..."

# 检查Java环境
if ! command -v java &> /dev/null; then
    echo "❌ 未找到Java环境，请先安装JDK 21"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)
echo "✅ Java版本: $JAVA_VERSION"

# 检查Maven
if ! command -v mvn &> /dev/null; then
    echo "⚠️  未找到Maven，将使用项目自带的mvnw"
fi

# 编译项目
echo "🔨 编译项目..."
./mvnw clean package -DskipTests

# 询问部署方式
echo "
请选择私有化模型部署方式：
1) Ollama (本地测试，简单易用)
2) vLLM (生产环境，高性能)
3) 自定义部署
"

read -p "请输入选择 (1-3): " choice

case $choice in
    1)
        echo "📦 部署Ollama..."
        if ! command -v ollama &> /dev/null; then
            echo "正在安装Ollama..."
            curl https://ollama.ai/install.sh | sh
        fi
        
        echo "下载必要模型..."
        ollama pull llama3.1:8b
        ollama pull nomic-embed-text
        
        echo "启动Ollama服务..."
        ollama serve &
        OLLAMA_PID=$!
        sleep 5
        
        echo "✅ Ollama部署完成"
        echo "📝 配置文件已准备就绪: application-private-model.yml"
        echo "🔧 启动命令:"
        echo "   java -jar target/demo-ai-0.0.1-SNAPSHOT.jar \\"
        echo "     --spring.profiles.active=private-model \\"
        echo "     --spring.config.location=classpath:/application.yml,file:./application-private-model.yml"
        ;;
        
    2)
        echo "📦 部署vLLM..."
        if ! command -v pip &> /dev/null; then
            echo "❌ 未找到pip，请先安装Python和pip"
            exit 1
        fi
        
        echo "安装vLLM..."
        pip install vllm
        
        echo "请手动启动vLLM服务:"
        echo "python -m vllm.entrypoints.openai.api_server \\"
        echo "    --model Qwen/Qwen2-7B-Instruct \\"
        echo "    --host 0.0.0.0 \\"
        echo "    --port 8000"
        
        echo "然后使用以下配置启动应用:"
        echo "   java -jar target/demo-ai-0.0.1-SNAPSHOT.jar \\"
        echo "     --spring.profiles.active=private-model"
        ;;
        
    3)
        echo "🔧 自定义部署..."
        echo "请确保您的私有模型服务已启动并支持OpenAI API格式"
        echo "然后修改 application-private-model.yml 中的配置"
        echo "最后使用以下命令启动:"
        echo "   java -jar target/demo-ai-0.0.1-SNAPSHOT.jar \\"
        echo "     --spring.profiles.active=private-model"
        ;;
        
    *)
        echo "❌ 无效选择"
        exit 1
        ;;
esac

echo "
📚 更多帮助请查看:
- 配置文件: application-private-model.yml
- 详细指南: PRIVATE_MODEL_DEPLOYMENT_GUIDE.md

🎯 部署完成后访问: http://localhost:8080
"

# 清理后台进程
trap "kill $OLLAMA_PID 2>/dev/null || true" EXIT