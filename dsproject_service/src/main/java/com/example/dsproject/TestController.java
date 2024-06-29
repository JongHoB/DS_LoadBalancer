package com.example.dsproject;


import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TestController {

    @Value("${eureka.instance.metadataMap.instanceId}")
    private String instanceId;

    @Value("${server.port}")
    private String port;
    private final ComputationService computationService;

    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("Hello from " + instanceId);
    }

    @GetMapping("port")
    public ResponseEntity<String> port() {
        return ResponseEntity.ok("Port: " + port);
    }

    @PostMapping("/test")
    public ResponseEntity<String> test(@RequestBody String body) {
        return ResponseEntity.ok("Hello from " + port + " with body: " + body);
    }

    /**
     * 처음에 현재 사용하는 서버의 처리 능력을 계산하는 API
     *
     */
    @PostMapping("/init-computation")
    public ResponseEntity<String> initComputation(@RequestBody String body){
        return ResponseEntity.ok(computationService.initialComputation(body));
    }


    @PostMapping("/computations")
    public ResponseEntity<String> computations(@RequestBody String body){
        return ResponseEntity.ok(computationService.computations(body));
    }

    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("pong");
    }
}
