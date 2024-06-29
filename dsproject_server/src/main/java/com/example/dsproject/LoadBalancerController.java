package com.example.dsproject;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;


@Getter
@Setter
@Builder
class Server {
    String instanceUrl;
    int  priority;

    // Weighted Round Robin
    int initialWeight;
}

class ServerComparator implements Comparator<Server> {
    @Override
    public int compare(Server s1, Server s2) {
        return s2.priority - s1.priority; // 내림차순
    }
}

class ServerComparator2 implements Comparator<Server> {
    @Override
    public int compare(Server s1, Server s2) {
        return s1.priority - s2.priority; // 오름차순
    }
}

@RestController
@RequiredArgsConstructor
@Slf4j
public class LoadBalancerController {

    @Autowired
    private final WebClient webClient;
    public final DiscoveryClient discoveryClient;
    private final Queue<Server> rrServers = new ConcurrentLinkedQueue<>();
    private final PriorityBlockingQueue<Server> wrServers = new PriorityBlockingQueue<>(6,new ServerComparator());
    private final PriorityBlockingQueue<Server> leastResponseTimeServers = new PriorityBlockingQueue<>(6,new ServerComparator2());
    private final PriorityBlockingQueue<Server> leastConnectionServers = new PriorityBlockingQueue<>(6,new ServerComparator2());
    private final PriorityBlockingQueue<Server> customLoadBalancerServers = new PriorityBlockingQueue<>(6,new ServerComparator2());
    private final CopyOnWriteArrayList<Integer> responseList = new CopyOnWriteArrayList<>();
    private final AtomicInteger nList= new AtomicInteger(0);

    @GetMapping("/whole")
    public ResponseEntity<String> whole(){
        List<ServiceInstance> instances = discoveryClient.getInstances("DSPROJECT-SERVICE");
        String result = instances.stream()
                .map(i -> i.getUri().toString()+"\n")
                .reduce("", (a, b) -> a + b);
        return ResponseEntity.ok(result);
    }


    @GetMapping("/init-roundrobin")
    public ResponseEntity<String> initRoundRobin() {
        List<ServiceInstance> instances = discoveryClient.getInstances("DSPROJECT-SERVICE");
        instances
                .forEach(i -> {
                    webClient.post()
                            .uri(i.getUri().toString()+"/DSPROJECT-SERVICE/init-computation")
                            .body(Mono.just(Integer.toString(20)), String.class)
                            .retrieve()
                            .toEntity(String.class)
                            .subscribe();
                    rrServers.add(
                            Server.builder()
                                    .instanceUrl(i.getUri().toString()+"/DSPROJECT-SERVICE")
                                    .build()
                    );
                });
        String result = rrServers.stream()
                .map(s -> s.getInstanceUrl()+"\n")
                .reduce("", (a, b) -> a + b);
        return ResponseEntity.ok(result);
    }

    /*
    round-robin 방식으로 서버 선택
    request가 들어올 때마다 서비스 인스턴스에 request를 전달하는 방식
     */
    @PostMapping("/roundrobin")
    public Mono<ResponseEntity<String>> roundRobin(@RequestBody String body) {
        Server server = rrServers.poll();
        if(server ==null){
            return Mono.just(ResponseEntity.status(503).body("No server available"));
        }
        rrServers.add(server);
        log.info("roundrobin - server url : {} priority: {}",server.getInstanceUrl(), server.getPriority());
        return webClient.post()
                .uri(server.getInstanceUrl()+"/computations")
                .body(Mono.just((String)body), String.class)
                .retrieve()
                .toEntity(String.class);
    }

    @GetMapping("/init-weightedroundrobin")
    public ResponseEntity<String> initWeightedRoundRobin() {
        List<ServiceInstance> instances = discoveryClient.getInstances("DSPROJECT-SERVICE");

        instances
                .forEach(i -> {
                    log.info("init-weightedroundrobin - uri: {}", i.getUri().toString());
                    final long start = System.currentTimeMillis();
                    RestTemplate restTemplate = new RestTemplate();
                    restTemplate.setRequestFactory(new HttpComponentsClientHttpRequestFactory());

                    ResponseEntity<String> responseEntity = null;
                    try {
                        responseEntity = restTemplate.postForEntity(
                                new URI(i.getUri().toString()+"/DSPROJECT-SERVICE/init-computation"),
                                Integer.toString(20),
                                String.class
                        );
                    } catch (URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                    final long end = System.currentTimeMillis();
                    final int weight = 5000/(int)(end-start);
                    log.info("init-weightedroundrobin - responseEntity: {}", responseEntity.getBody());
                    log.info("init-weightedroundrobin - weight: {}", weight);
                    wrServers.add(
                           Server.builder()
                                   .instanceUrl(i.getUri().toString()+"/DSPROJECT-SERVICE")
                                   .priority(weight)
                                   .initialWeight(weight)
                                   .build()
                   );
                });

        // weight 값 보정
        int sum = wrServers.stream()
                .map(Server::getInitialWeight)
                .reduce(0, Integer::sum);
        // 반올림하여 총합 약 100으로 맞추기
        wrServers
                .forEach(s -> {
                    int wtemp = ((int)Math.round((double)s.getInitialWeight()/sum*100));
                    s.setPriority(wtemp);
                    s.setInitialWeight(wtemp);
                });

        String result = wrServers.stream()
                .map(s -> s.getInstanceUrl()+" : "+ s.getPriority()+"\n")
                .reduce("", (a, b) -> a + b);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/weightedroundrobin")
    public Mono<ResponseEntity<String>> weightedRoundRobin(@RequestBody String body){
        Server server = wrServers.poll();
        if(server ==null){
            return Mono.just(ResponseEntity.status(503).body("No server available"));
        }
        server.setPriority(server.priority-100);
        wrServers.add(server);
        wrServers.parallelStream()
                .forEach(s -> {
                    s.setPriority(s.getPriority()+s.getInitialWeight());
                });

        log.info("weightedroundrobin - server url : {} priority: {}",server.getInstanceUrl(), server.getPriority());
        return webClient.post()
                .uri(server.getInstanceUrl()+"/computations")
                .body(Mono.just((String)body), String.class)
                .retrieve()
                .toEntity(String.class);
    }

    @GetMapping("/init-leastresponsetime")
    public ResponseEntity<String> initLeastResponseTime() {
        List<ServiceInstance> instances = discoveryClient.getInstances("DSPROJECT-SERVICE");

        instances
                .forEach(i -> {
                    log.info("leastresponsetime - uri: {}", i.getUri().toString());
                    webClient.post()
                            .uri(i.getUri().toString()+"/DSPROJECT-SERVICE/init-computation")
                            .body(Mono.just(Integer.toString(20)), String.class)
                            .retrieve()
                            .toEntity(String.class)
                            .subscribe();
                    leastResponseTimeServers.add(
                            Server.builder()
                                    .instanceUrl(i.getUri().toString()+"/DSPROJECT-SERVICE")
                                    .priority(0)
                                    .build()
                    );
                });

        String result = leastResponseTimeServers.stream()
                .map(s -> s.getInstanceUrl()+" : "+ s.getPriority()+"\n")
                .reduce("", (a, b) -> a + b);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/leastresponsetime")
    public Mono<ResponseEntity<String>> leastResponseTime(@RequestBody String body){
        Server server = leastResponseTimeServers.poll();
        if(server ==null){
            return Mono.just(ResponseEntity.status(503).body("No server available"));
        }
        log.info("leastresponsetime - server url : {} priority: {}",server.getInstanceUrl(), server.getPriority());
        double start = System.currentTimeMillis();
        return webClient.post()
                .uri(server.getInstanceUrl() + "/computations")
                .body(Mono.just((String)body), String.class)
                .retrieve()
                .toEntity(String.class)
                .doOnSuccess(response -> {
                    double end = System.currentTimeMillis();
                    int responseTime = (int) (end - start);
                    server.setPriority(responseTime);
                    leastResponseTimeServers.add(server);
                })
                .doOnError(error -> {
                    // 에러가 발생하면 서버의 우선순위를 증가시키지 않고 다시 큐에 추가
                    leastResponseTimeServers.add(server);
                });
    }

    @GetMapping("/init-leastconnection")
    public ResponseEntity<String> initLeastConnection() {
        List<ServiceInstance> instances = discoveryClient.getInstances("DSPROJECT-SERVICE");

        instances
                .forEach(i -> {
                    log.info("least-connection uri: {}", i.getUri().toString());
                    webClient.post()
                            .uri(i.getUri().toString()+"/DSPROJECT-SERVICE/init-computation")
                            .body(Mono.just(Integer.toString(20)), String.class)
                            .retrieve()
                            .toEntity(String.class)
                            .subscribe();
                    leastConnectionServers.add(
                            Server.builder()
                                    .instanceUrl(i.getUri().toString()+"/DSPROJECT-SERVICE")
                                    .priority(0)
                                    .build()
                    );
                });

        String result = leastConnectionServers.stream()
                .map(s -> s.getInstanceUrl()+" : "+ s.getPriority()+"\n")
                .reduce("", (a, b) -> a + b);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/leastconnection")
    public Mono<ResponseEntity<String>> leastConnection(@RequestBody String body){
        Server server = leastConnectionServers.poll();
        if(server ==null){
            return Mono.just(ResponseEntity.status(503).body("No server available"));
        }
        log.info("leastconnection - server url : {} priority: {}",server.getInstanceUrl(), server.getPriority());
        server.setPriority(server.getPriority()+1);
        leastConnectionServers.add(server);
        return webClient.post()
                .uri(server.getInstanceUrl() + "/computations")
                .body(Mono.just((String)body), String.class)
                .retrieve()
                .toEntity(String.class)
                .doFinally(res->{
                    boolean t = leastConnectionServers.remove(server);
                    server.setPriority(server.getPriority()-1);
                    leastConnectionServers.add(server);
                });
    }

    @GetMapping("/init-customloadbalancer")
    public ResponseEntity<String> initCustomLoadBalancer() {
        customLoadBalancerServers.clear();
        List<ServiceInstance> instances = discoveryClient.getInstances("DSPROJECT-SERVICE");

        instances.stream()
                .forEach(i -> {
                    log.info("uri: {}", i.getUri().toString());
                    final long start = System.currentTimeMillis();
                    RestTemplate restTemplate = new RestTemplate();
                    restTemplate.setRequestFactory(new HttpComponentsClientHttpRequestFactory());

                    ResponseEntity<String> responseEntity = null;
                    try {
                        responseEntity = restTemplate.postForEntity(
                                new URI(i.getUri().toString()+"/DSPROJECT-SERVICE/init-computation"),
                                Integer.toString(20),
                                String.class
                        );
                    } catch (URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                    final long end = System.currentTimeMillis();
                    final int weight = (int)(end-start);
                    log.info("init - responseEntity: {}", responseEntity.getBody());
                    log.info("init - weight: {}", weight);
                    customLoadBalancerServers.add(
                            Server.builder()
                                    .instanceUrl(i.getUri().toString()+"/DSPROJECT-SERVICE")
                                    .priority(weight)
                                    .initialWeight(weight)
                                    .build()
                    );
                });

        String result = customLoadBalancerServers.stream()
                .map(s -> s.getInstanceUrl()+" : "+ s.getPriority()+"\n")
                .reduce("", (a, b) -> a + b);

        return ResponseEntity.ok(result);
    }

    @PostMapping("/customloadbalancer")
    public Mono<ResponseEntity<String>> customLoadBalancer(@RequestBody String body){
        Server server = customLoadBalancerServers.poll();
        if(server ==null){
            return Mono.just(ResponseEntity.status(503).body("No server available"));
        }
        log.info("customloadbalancer - server url : {} priority: {}",server.getInstanceUrl(), server.getPriority());

        // 서버의 우선순위를 낮추고 다시 큐에 추가
        server.setPriority(server.getPriority()-1);
        customLoadBalancerServers.add(server);

        double start = System.currentTimeMillis();
        return webClient.post()
                .uri(server.getInstanceUrl() + "/computations")
                .body(Mono.just((String)body), String.class)
                .retrieve()
                .toEntity(String.class)
                .doOnSuccess(res->{
                    double end = System.currentTimeMillis();
                    int responseTime = (int) (end - start);
                    boolean t = customLoadBalancerServers.remove(server);
                    server.setPriority(server.getPriority()+1);
                    responseList.add(responseTime);
                    nList.incrementAndGet();
                    if(nList.get()>=5){
                        weightControl();
                    }
                    customLoadBalancerServers.add(server);
                })
                .doOnError(error -> {
                    // 에러가 발생하면 서버의 우선순위를 증가시키지 않고 다시 큐에 추가
                    customLoadBalancerServers.add(server);
                });
    }

    public synchronized void weightControl(){
        int sum = responseList.stream()
                .reduce(0, Integer::sum);
        int size = responseList.size();
        if(size==0){
            return;
        }
        int avg = (int)(sum/size);
        responseList.clear();
        nList.set(0);
        customLoadBalancerServers.parallelStream()
                .forEach(s->{
                    int weight = s.getPriority();
                    if(avg>=weight){
                        s.setPriority((int)(weight-0.1*avg));
                    }
                    else{
                        s.setPriority((int)(weight+0.1*avg));
                    }
                });
    }

    @Scheduled(fixedRate = 120000)  // 2분마다 실행
    @Async
    public void reset(){
        if(customLoadBalancerServers.isEmpty()){
            return;
        }
        customLoadBalancerServers.parallelStream()
                .forEach(s->{
                    s.setPriority(s.getInitialWeight());
                });
    }


    @Scheduled(fixedRate = 10000) // 10초마다 실행
    @Async
    public void checkServerHealth(){
        if(customLoadBalancerServers.isEmpty()){
            return;
        }
        log.info("Checking server health");
        List<Map<Server,Long>> responseTimes = new ArrayList<>();
        AtomicLong totalTime= new AtomicLong();
        customLoadBalancerServers.parallelStream()
                .forEach(s->{
                    String url = s.getInstanceUrl()+"/ping";

                    long start = System.currentTimeMillis();
                    webClient.get()
                            .uri(url)
                            .retrieve()
                            .bodyToMono(String.class)
                            .doOnError(e->{
                                s.setPriority(0);
                            })
                            .subscribe();
                    long end = System.currentTimeMillis();
                    long responseTime = (end-start);
                    responseTimes.add(Map.of(s,responseTime));
                    totalTime.addAndGet(responseTime);
                });

        if(totalTime.get()==0){
            return;
        }

        // response time에 따른 가중치 조정
        responseTimes.parallelStream()
                .forEach(m->{
                    Server server = m.keySet().iterator().next();
                    long responseTime = m.get(server);
                    double weight = (double)(responseTime/totalTime.get());
                    if(server.getPriority()<-5000)
                        server.setPriority(server.getPriority()-(int)(0.2*server.getPriority()*weight));
                    else if(server.getPriority()<-1000)
                        server.setPriority(server.getPriority()-(int)(0.1*server.getPriority()*weight));
                    else if(server.getPriority()<0)
                        server.setPriority(server.getPriority()-(int)(0.05*server.getPriority()*weight));
                    else
                        server.setPriority(server.getPriority()-(int)(0.01*server.getPriority()*weight));
                });
        totalTime.set(0);
        responseTimes.clear();
    }
}
