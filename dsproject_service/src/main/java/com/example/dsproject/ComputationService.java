package com.example.dsproject;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComputationService {

    @Value("${eureka.instance.metadataMap.instanceId}")
    private String instanceId;
    private final int numCores = Runtime.getRuntime().availableProcessors() / 2 + 1;
    private double[][] matrixA;
    private double[][] matrixB;

    @Value("${server.port}")
    private String port;

    /**
     * 처음에 현재 사용하는 서버의 처리 능력 계산 확인
     */
    public String initialComputation(String value) {
//        log.info("value : "+value);

        final int loop = Integer.parseInt(value);

        matrixA = createMatrix(512); // 512 x 512
        matrixB = createMatrix(512);

        long start = System.currentTimeMillis();
        for (int i = 0; i < loop; i++) {
            double result = parallelMatMul(matrixA, matrixB);
        }
        long end = System.currentTimeMillis();
//        log.info("Port :"+port+" Time: " + (end - start) + "ms");


        return "InstanceId:"+instanceId+" Time: " + (end - start) + "ms";

    }

    private double[][] createMatrix(final int N) {
        double[][] matrix = new double[N][N];
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                matrix[i][j] = i * j;
            }
        }
        return matrix;
    }

    private double parallelMatMul(final double[][] A, final double[][] B) {
        final int N = A.length;
        final double[][] C = new double[N][N];
        final int cores = numCores;
        final int chunkSize = N / cores;
        Thread[] threads = new Thread[cores];

        for (int i = 0; i < cores; i++) {
            final int start = i * chunkSize;
            final int end = (i == cores - 1) ? N : start + chunkSize;
            threads[i] = new Thread(() -> {
                for (int j = start; j < end; j++) {
                    for (int k = 0; k < N; k++) {
                        for (int l = 0; l < N; l++) {
                            C[j][k] += A[j][l] * B[l][k];
                        }
                    }
                }
            });
            threads[i].start();
        }

        for (int i = 0; i < cores; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        return C[N - 1][N - 1];
    }

    public String computations(String value) {
        final int loop = Integer.parseInt(value);

        double result = 0;
        long start = System.currentTimeMillis();
        for (int i = 0; i < loop; i++) {
            result = parallelMatMul(matrixA, matrixB);
        }
        long end = System.currentTimeMillis();

        return "InstanceId " + instanceId + " Time: " + (end - start) + "ms" + "result :" + result;


    }
}
