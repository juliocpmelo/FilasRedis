package com.example.demo;

import com.example.demo.dto.TarefaScoredSetDto;
import com.example.demo.model.Tarefa;
import com.example.demo.model.Usuario;
import com.example.demo.repository.TarefaRepository;
import com.example.demo.repository.UsuarioRepository;
import com.example.demo.service.TarefaProcessService;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RQueue;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.ScoredEntry;
import org.redisson.codec.SerializationCodec;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@SpringBootTest
@Testcontainers
class FilasRedisApplicationTests {
    Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private TarefaProcessService tarefaProcessService;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private TarefaRepository tarefaRepository;
    static RedissonClient redissonClient;
    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:6.2")).withExposedPorts(6379);


    static {
        redis.start();
        Config config = new Config();
        Codec codecWorks = new SerializationCodec( Thread.currentThread().getContextClassLoader() );
        config.setCodec(codecWorks)
                .useSingleServer().setAddress("redis://%s:%s".formatted(redis.getHost(), redis.getMappedPort(6379)));
        //.useClusterServers()
        //.addNodeAddress(redisEndpoint);
        redissonClient =  Redisson.create(config);
    }

    @Test
    void testProcessJobs() {
        for(int i=0; i<10; i++){
            generateTarefas(20);
            tarefaProcessService.processFilaTarefas();
        }
    }

    @Test
    void testProcessAsync() {
        fillDb(200);
        generateTarefas(200);

        int n_threads = 4;
        List<CompletableFuture> futures = new ArrayList<>();
        for(int t = 0; t < n_threads; t++) {
            var future = CompletableFuture.runAsync(() -> {
                tarefaProcessService.processFilaTarefas(); //!!!
            });
            futures.add(future);
        }

        //espera todos
        futures.forEach(CompletableFuture::join);
    }

    @Test
    void testProcessAsyncRedis() {
        fillDb(200);
        generateTarefas(200);
        saveAllTarefasRedisQueue();

        int n_threads = 4;
        List<CompletableFuture> futures = new ArrayList<>();
        for(int t = 0; t < n_threads; t++) {
            var future = CompletableFuture.runAsync(() -> {
                RQueue<Long> queue = redissonClient.getQueue("FilasRedisApplicationTests_queue");
                var tarefas = queue.poll(20);
                tarefaProcessService.processFilaTarefas(tarefas);
            });
            futures.add(future);
        }
        //espera todos
        futures.forEach(CompletableFuture::join);
    }

    @Test
    void testProcessAsyncRedisScoredSet() {
        fillDb(200);
        generateTarefas(200);
        saveAllTarefasRedisScoredSet();

        int n_threads = 4;
        List<CompletableFuture> futures = new ArrayList<>();
        for(int t = 0; t < n_threads; t++) {
            var future = CompletableFuture.runAsync(() -> {
                RScoredSortedSet<TarefaScoredSetDto> queue = redissonClient.getScoredSortedSet("FilasRedisApplicationTests_scoredSortedSet");

                RLock lock = redissonClient.getLock("FilasRedisApplicationTests_scoredSortedSet::lock");
                lock.lock(5, TimeUnit.SECONDS);
                var tarefasWithScore = queue.entryRange(0, 20); //pega os primeiros 20 elementos
                var tarefasToRemove = tarefasWithScore.stream().map(ScoredEntry::getValue).toList();
                queue.removeAll(tarefasToRemove); //remove os elementos retornados
                lock.unlock();

                tarefaProcessService.processFilaTarefas(tarefasWithScore.stream().map(ScoredEntry::getScore).map(Double::longValue).toList());
            });
            futures.add(future);
        }
        //espera todos
        futures.forEach(CompletableFuture::join);
    }

    private void saveAllTarefasRedisQueue(){
        //quando duplicatas não importam
        RQueue<Long> queue = redissonClient.getQueue("FilasRedisApplicationTests_queue");
        var tarefas = tarefaRepository.findAll();
        for(var t : tarefas)
            queue.add(t.getId());
    }

    private void saveAllTarefasRedisScoredSet(){
        //quando duplicatas importam
        RScoredSortedSet<TarefaScoredSetDto> queue = redissonClient.getScoredSortedSet("FilasRedisApplicationTests_scoredSortedSet");
        var tarefas = tarefaRepository.findAll();
        for(var t : tarefas) {
            switch (t.getTipo()){
                case DELETE, UPDATE: {
                    try {
                        JSONObject payload = new JSONObject(t.getPayload());
                        String id = payload.get("id").toString();
                        queue.add(t.getId(), new TarefaScoredSetDto(t.getTipo(), Long.valueOf(id)));
                    }
                    catch (Exception ex){
                        ex.printStackTrace();
                        logger.error("Erro ao criar dto para {}", t);
                    }
                }
            }
        }
    }

    private void fillDb(int quantidade){
        for(int i=0; i<quantidade; i++) {
            var usuario = new Usuario();
            usuario.setNome(UUID.randomUUID().toString());
            usuarioRepository.save(usuario);
        }
    }

    private void generateTarefas(int numero){
        var tipos = List.of(
                Tarefa.Tipo.UPDATE,
                Tarefa.Tipo.DELETE,
                Tarefa.Tipo.INSERT);
        for(int i=0; i<numero; i++){
            var t = new Tarefa();
            var tipo = (int) (Math.random()*3);
            JSONObject payload = new JSONObject();
            try {
                t.setTipo(tipos.get(tipo));
                switch (tipos.get(tipo)) {
                    case INSERT: {
                        payload.put("nome", UUID.randomUUID().toString());
                        t.setPayload(payload.toString());
                        tarefaRepository.save(t);
                    }
                    case DELETE: {
                        var count = usuarioRepository.count();
                        if(count > 0) {
                            var users = usuarioRepository.findAll(Pageable.ofSize(20)).toList();
                            var randomUser = users.get((int) (Math.random() * users.size()));
                            payload.put("id", randomUser.getId());
                            t.setPayload(payload.toString());
                            tarefaRepository.save(t);
                        }
                    }
                    case UPDATE: {
                        var count = usuarioRepository.count();
                        if(count > 0) {
                            var users = usuarioRepository.findAll(Pageable.ofSize(20)).toList();
                            var randomUser = users.get((int) (Math.random() * users.size()));
                            payload.put("id", randomUser.getId());
                            payload.put("nome", UUID.randomUUID().toString());
                            t.setPayload(payload.toString());
                            tarefaRepository.save(t);
                        }
                    }
                }
            }
            catch (Exception ex){
                ex.printStackTrace();
            }

        }
    }

}
