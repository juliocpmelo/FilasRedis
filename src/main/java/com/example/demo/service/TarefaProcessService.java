package com.example.demo.service;

import com.example.demo.model.Tarefa;
import com.example.demo.model.Usuario;
import com.example.demo.repository.TarefaRepository;
import com.example.demo.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.json.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
public class TarefaProcessService {
    Logger logger = LoggerFactory.getLogger(this.getClass());

    private final UsuarioRepository usuarioRepository;
    private final TarefaRepository tarefaRepository;
    private void processTarefa(Tarefa t){
        JSONObject payload = new JSONObject(t.getPayload());

        logger.info("Processando tarefa id {}", t.getId());

        switch(t.getTipo()){
            case INSERT: {
                String nome = payload.get("nome").toString();
                Usuario usuario = new Usuario();
                usuario.setNome(nome);
                usuarioRepository.save(usuario);
                logger.info("Inserindo usr nome {}", nome);
                break;
            }
            case DELETE: {
                String id = payload.get("id").toString();
                try {
                    if (id != null)
                        usuarioRepository.deleteById(Long.valueOf(id));
                }
                catch (Exception exception){
                    logger.error("Erro ao tentar deletar Id {}", id);
                }
                break;
            }
            case UPDATE: {
                String id = payload.get("id").toString();
                var usr = usuarioRepository.findById(Long.valueOf(id)).orElse(null);
                try {
                    if (usr != null) {
                        logger.info("Update usr {} nome", id);
                        usr.setNome(payload.get("nome").toString());
                        usuarioRepository.save(usr);
                    }
                }
                catch (Exception ex){
                    logger.error("Erro ao tentar atualizar Id {}", id);
                }

                break;
            }
        }
    }

    public void processFilaTarefas() {
        var tarefas = tarefaRepository.findAll(Pageable.ofSize(10));
        for(var t : tarefas){
            processTarefa(t);
        }
        tarefaRepository.deleteAll(tarefas);
    }

    public void processFilaTarefas(List<Long> tarefasIds) {
        var tarefas = tarefaRepository.findAllById(tarefasIds);
        for(var t : tarefas){
            processTarefa(t);
        }
        tarefaRepository.deleteAll(tarefas);
    }
}
