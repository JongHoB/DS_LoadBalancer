package com.example.dsproject.lbtest;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.netflix.ribbon.RibbonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.stream.Collectors;


// TEST용 컨트롤러
@RestController
@Slf4j
@RequiredArgsConstructor
public class TestController {

    private final DiscoveryClient discoveryClient;
    @Value("${eureka.instance.metadataMap.instanceId}")
    private String instanceId;
    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("Hello from " + instanceId);
    }

    // Get all the instances of a service
    @GetMapping("/list")
    public ResponseEntity<String> list(){

        /**
         * [[EurekaServiceInstance@6e5add47 instance = InstanceInfo [instanceId = 192.168.0.10:dsproject_service3:3004, appName = DSPROJECT_SERVICE3, hostName = 192.168.0.10, status = UP, ipAddr = 192.168.0.10, port = 3004, securePort = 443, dataCenterInfo = com.netflix.appinfo.MyDataCenterInfo@3a339c2]]
         * [[EurekaServiceInstance@4f0bf2b6 instance = InstanceInfo [instanceId = 192.168.0.10:dsproject_service2:3003, appName = DSPROJECT_SERVICE2, hostName = 192.168.0.10, status = UP, ipAddr = 192.168.0.10, port = 3003, securePort = 443, dataCenterInfo = com.netflix.appinfo.MyDataCenterInfo@5c78569e]]
         * [[EurekaServiceInstance@647008b3 instance = InstanceInfo [instanceId = 192.168.0.10:dsproject_server:3001, appName = DSPROJECT_SERVER, hostName = 192.168.0.10, status = UP, ipAddr = 192.168.0.10, port = 3001, securePort = 443, dataCenterInfo = com.netflix.appinfo.MyDataCenterInfo@7e0750d6]]
         * [[EurekaServiceInstance@6e0993e2 instance = InstanceInfo [instanceId = 192.168.0.10:dsproject_service:3002, appName = DSPROJECT_SERVICE, hostName = 192.168.0.10, status = UP, ipAddr = 192.168.0.10, port = 3002, securePort = 443, dataCenterInfo = com.netflix.appinfo.MyDataCenterInfo@705bf41b]]
         */

        List<ServiceInstance> instances= discoveryClient.getServices()
                .stream()
                .map(discoveryClient::getInstances)
                .flatMap(List::stream)
                .toList();

        List<ServiceInstance> instances2= discoveryClient.getInstances("DSPROJECT-SERVICE");
        log.info("instances2: {}", instances2);

        instances2.stream()
                .forEach(i->log.info("instance: {}", i.getUri()));


        String services = instances.stream()
                .map(i->ServiceInstance.class.cast(i).getUri().toString())
                .collect(Collectors.joining(","));

        return ResponseEntity.ok(services);
    }

}
