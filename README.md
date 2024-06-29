# 📁File List

1. `dsproject_eureka` - Registry Server용 코드
2. `dsproject_server` - Load Balancer Server용 코드
3. `dsproject_service` - Service Server용 코드
4. `testcode.ipynb` - Test Code

# 📌Environment

- JAVA - JDK 17

# ✅방법

1. `dsproject_server` 와 `dsproject_service` 내의 `application.yml` 에서`REGISTRY_SERVER:3000` 주소를 `dsproject_eureka` 를 실행하는 서버의 주소로 변경 후 실행

```docker
eureka:
	client:
		~~~~~~~~
		service-url:
			defaultZone: http://REGISTRY_SERVER:3000/eureka
		~~~~~~~~
```

2. `dsproject_eureka` → `dsproject_server` → `dsproject_service` 순으로 실행시킵니다.
3. `dsproject_service` 는 동일한 코드로 여러 서버에서 실행시켜도 문제 없습니다. Spring+eureka를 통해 자동 탐지하게 됩니다.
4. `testcode.ipynb` 를 참고하여 실행하면 됩니다.
