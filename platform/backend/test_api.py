import urllib.request
import json
import time

proxy_support = urllib.request.ProxyHandler({})
opener = urllib.request.build_opener(proxy_support)
urllib.request.install_opener(opener)

url = 'http://127.0.0.1:8080/api/analysis/predict'
data = json.dumps({
    'url': 'https://douyiner.cn/user/authentication/login',
    'context': '分析登录面'
}).encode('utf-8')

req = urllib.request.Request(
    url, 
    data=data, 
    headers={'Content-Type': 'application/json'}
)

try:
    print("开始请求 AI 大模型接口分析目标...")
    start_time = time.time()
    
    with urllib.request.urlopen(req) as response:
        content = response.read().decode('utf-8')
        end_time = time.time()
        
        print(f"耗时: {end_time - start_time:.2f} 秒")
        print("\n=== HTTP 返回结果 ===")
        print(content)
        
except Exception as e:
    print("遭遇异常:", e)
